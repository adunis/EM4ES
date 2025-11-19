package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.EM4ES;
import ace.actually.EM4ES.ExplorerMapTradeFactory;
import ace.actually.EM4ES.StructureSearchResult;
import ace.actually.EM4ES.VillagerDataAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(WanderingTraderEntity.class)
public abstract class WanderingTraderEntityMixinTradeCycle extends MerchantEntity {

    public WanderingTraderEntityMixinTradeCycle(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "afterUsing", at = @At("TAIL"))
    private void replaceUsedMapTrade(TradeOffer offer, CallbackInfo ci) {
        if (this.getWorld().isClient) return;

        // 1. Check if it is a Map trade
        if (offer.getSellItem().getItem() != Items.FILLED_MAP) {
            return;
        }

        WanderingTraderEntity trader = (WanderingTraderEntity) (Object) this;

        // 2. If Trade is Exhausted (Disabled)
        if (offer.isDisabled()) {
            TradeOfferList offers = trader.getOffers();
            int tradeIndex = offers.indexOf(offer);
            if (tradeIndex == -1) return;

            EM4ES.LOGGER.info("Wandering Trader map trade exhausted. Starting Async Restock...");

            // --- STEP A: IMMEDIATE VISUAL FEEDBACK (Main Thread) ---
            TradeOffer placeholder = createRestockingPlaceholder();
            offers.set(tradeIndex, placeholder);
            refreshPlayerUI(trader);

            // --- STEP B: ASYNC SEARCH ---
            ServerWorld serverWorld = (ServerWorld) this.getWorld();
            VillagerDataAccessor accessor = (VillagerDataAccessor) trader;

            EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {
                // Use the configured radius for Wandering Traders
                int radiusInChunks = Math.max(1, EM4ES.WANDERING_TRADER_SEARCH_RADIUS / 16);

                // 1. Find Structure (Background Thread)
                StructureSearchResult result = ExplorerMapTradeFactory.findStructure(
                        serverWorld,
                        trader.getBlockPos(),
                        accessor.getOfferedStructureMaps(),
                        radiusInChunks
                );

                // --- STEP C: UPDATE TRADES (Main Thread) ---
                this.getServer().execute(() -> {
                    try {
                        if (result != null) {
                            ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(1, radiusInChunks);
                            TradeOffer newMapTrade = factory.createTradeFromSearch(serverWorld, result, accessor.getOfferedStructureMaps());

                            if (newMapTrade != null) {
                                int currentIndex = offers.indexOf(placeholder);
                                if (currentIndex != -1) {
                                    offers.set(currentIndex, newMapTrade);
                                    refreshPlayerUI(trader);
                                    EM4ES.LOGGER.info("Wandering Trader restock successful.");
                                }
                            }
                        } else {
                            // If nothing found, remove the placeholder so it doesn't stay there forever
                            offers.remove(placeholder);
                            refreshPlayerUI(trader);
                        }
                    } catch (Exception e) {
                        EM4ES.LOGGER.error("Error updating Wandering Trader UI", e);
                    }
                });
            });
        }
    }

    private void refreshPlayerUI(WanderingTraderEntity trader) {
        if (trader.getCustomer() instanceof ServerPlayerEntity player) {
            if (player.currentScreenHandler instanceof MerchantScreenHandler) {
                // Wandering Traders are not Leveled merchants, so we pass 0/false for those values
                player.sendTradeOffers(
                        player.currentScreenHandler.syncId,
                        trader.getOffers(),
                        0,     // levelProgress
                        0,     // experience
                        false, // isLeveled
                        false  // canRefresh
                );
            }
        }
    }

    private TradeOffer createRestockingPlaceholder() {
        ItemStack icon = new ItemStack(Items.CLOCK);
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Restocking...").formatted(Formatting.YELLOW));
        TradeOffer offer = new TradeOffer(
                new TradedItem(Items.BAMBOO, 64), // Dummy cost
                Optional.empty(),
                icon,
                0,
                0,
                0.0f
        );
        offer.disable();
        return offer;
    }
}