package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.EM4ES;
import ace.actually.EM4ES.ExplorerMapTradeFactory;
import ace.actually.EM4ES.StructureSearchResult;
import ace.actually.EM4ES.VillagerDataAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.world.ServerWorld;

@Mixin(MerchantEntity.class)
public abstract class MapReplenishMixin extends PassiveEntity {

    protected MapReplenishMixin(EntityType<? extends PassiveEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "trade", at = @At("TAIL"))
    private void onTradeCompleted(TradeOffer offer, CallbackInfo ci) {
        if (this.getWorld().isClient) return;

        MerchantEntity merchant = (MerchantEntity) (Object) this;
        if (!(merchant instanceof VillagerDataAccessor accessor)) return; // Safety cast

        boolean isTrader = merchant instanceof WanderingTraderEntity;
        boolean isVillager = merchant instanceof VillagerEntity;
        if (!isTrader && !isVillager) return;

        PlayerEntity rawCustomer = merchant.getCustomer();
        if (!(rawCustomer instanceof ServerPlayerEntity player)) return;

        // Check if trade is a map and sold out
        if (offer.getSellItem().getItem() == Items.FILLED_MAP && offer.isDisabled()) {

            int searchRadiusInBlocks = getRadiusForMerchant(merchant);
            int radiusInChunks = Math.max(1, searchRadiusInBlocks / 16);
            ServerWorld serverWorld = (ServerWorld) this.getWorld();

            EM4ES.LOGGER.info("Map sold out. Restocking async...");

            // 1. ASYNC SEARCH (Heavy Math, No World Writes)
            EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {

                StructureSearchResult result = ExplorerMapTradeFactory.findStructure(
                        serverWorld,
                        merchant.getBlockPos(),
                        accessor.getOfferedStructureMaps(),
                        radiusInChunks
                );

                // 2. MAIN THREAD UPDATE (World Writes / UI)
                this.getServer().execute(() -> {
                    if (result != null) {
                        try {
                            // Create the map item and trade offer on the main thread
                            ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(1, radiusInChunks);
                            TradeOffer newOffer = factory.createTradeFromSearch(serverWorld, result, accessor.getOfferedStructureMaps());

                            if (newOffer != null) {
                                TradeOfferList offers = merchant.getOffers();
                                int index = offers.indexOf(offer);

                                if (index != -1) {
                                    offers.set(index, newOffer);
                                    EM4ES.LOGGER.info("Restocked map trade.");

                                    // Safety: Check if player is still there
                                    if (merchant.getCustomer() == player && player.currentScreenHandler instanceof MerchantScreenHandler) {
                                        player.sendTradeOffers(
                                                player.currentScreenHandler.syncId,
                                                offers,
                                                0,
                                                merchant.getExperience(),
                                                merchant.isLeveledMerchant(),
                                                merchant.canRefreshTrades()
                                        );
                                    }
                                }
                            }
                        } catch (Exception e) {
                            EM4ES.LOGGER.error("Failed to update trade UI", e);
                        }
                    }
                });
            });
        }
    }

    private int getRadiusForMerchant(MerchantEntity merchant) {
        if (merchant instanceof WanderingTraderEntity) {
            return EM4ES.WANDERING_TRADER_SEARCH_RADIUS;
        } else if (merchant instanceof VillagerEntity villager) {
            int level = villager.getVillagerData().getLevel();
            switch (level) {
                case 1: return EM4ES.CARTOGRAPHER_L1_SEARCH_RADIUS;
                case 2: return EM4ES.CARTOGRAPHER_L2_SEARCH_RADIUS;
                case 3: return EM4ES.CARTOGRAPHER_L3_SEARCH_RADIUS;
                case 4: return EM4ES.CARTOGRAPHER_L4_SEARCH_RADIUS;
                case 5: return EM4ES.CARTOGRAPHER_L5_SEARCH_RADIUS;
                default: return 500;
            }
        }
        return 500;
    }
}