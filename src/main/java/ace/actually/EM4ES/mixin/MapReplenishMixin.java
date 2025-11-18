package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.EM4ES;
import ace.actually.EM4ES.ExplorerMapTradeFactory;
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

@Mixin(MerchantEntity.class)
public abstract class MapReplenishMixin extends PassiveEntity {

    protected MapReplenishMixin(EntityType<? extends PassiveEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "trade", at = @At("TAIL"))
    private void onTradeCompleted(TradeOffer offer, CallbackInfo ci) {
        // 1. Client-side check
        if (this.getWorld().isClient) return;

        MerchantEntity merchant = (MerchantEntity) (Object) this;

        // 2. Verify Entity Type (Trader or Villager)
        boolean isTrader = merchant instanceof WanderingTraderEntity;
        boolean isVillager = merchant instanceof VillagerEntity;
        if (!isTrader && !isVillager) return;

        // 3. Capture the *current* customer safely
        PlayerEntity rawCustomer = merchant.getCustomer();
        if (!(rawCustomer instanceof ServerPlayerEntity player)) {
            return;
        }

        // 4. Check if it was a Map trade and it is now disabled (out of stock)
        if (offer.getSellItem().getItem() == Items.FILLED_MAP && offer.isDisabled()) {

            int searchRadiusInBlocks = getRadiusForMerchant(merchant);
            EM4ES.LOGGER.info("Map sold out. Restocking async...");

            // 5. Async Search
            EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {

                // Convert blocks to chunks
                int radiusInChunks = Math.max(1, searchRadiusInBlocks / 16);

                ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(1, radiusInChunks);
                TradeOffer newOffer = factory.create(merchant, merchant.getRandom());

                // 6. Main Thread Update
                if (newOffer != null) {
                    this.getServer().execute(() -> {
                        try {
                            TradeOfferList offers = merchant.getOffers();
                            int index = offers.indexOf(offer);

                            if (index != -1) {
                                // Swap the trade in the list logic
                                offers.set(index, newOffer);
                                EM4ES.LOGGER.info("Restocked map trade.");

                                // --- SAFETY CHECK ---
                                // We must verify the player is STILL trading with this specific merchant.
                                // If they closed the window during the async search, 'merchant.getCustomer()' will be null.
                                if (merchant.getCustomer() == player) {

                                    // Also ensure the screen they have open is actually a Merchant Screen
                                    if (player.currentScreenHandler instanceof MerchantScreenHandler) {

                                        // Send the update packet using the Sync ID of the currently open window
                                        player.sendTradeOffers(
                                                player.currentScreenHandler.syncId,
                                                offers,
                                                0, // <--- levelProgress (Visual XP bar fill). 0 is safe for a refresh.
                                                merchant.getExperience(), // Total Experience
                                                merchant.isLeveledMerchant(),
                                                merchant.canRefreshTrades()
                                        );
                                    }
                                }
                            }
                        } catch (Exception e) {
                            EM4ES.LOGGER.error("Failed to update trade UI", e);
                        }
                    });
                }
            });
        }
    }

    private int getRadiusForMerchant(MerchantEntity merchant) {
        if (merchant instanceof WanderingTraderEntity) {
            return EM4ES.WANDERING_TRADER_SEARCH_RADIUS;
        }
        else if (merchant instanceof VillagerEntity villager) {
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