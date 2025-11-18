package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.EM4ES;
import ace.actually.EM4ES.ExplorerMapTradeFactory;
import ace.actually.EM4ES.StructureSearchResult;
import ace.actually.EM4ES.VillagerDataAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixinTradeCycle extends MerchantEntity {

    public VillagerEntityMixinTradeCycle(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Shadow public abstract VillagerData getVillagerData();

    @Inject(method = "afterUsing", at = @At("TAIL"))
    private void replaceUsedMapTrade(TradeOffer offer, CallbackInfo ci) {
        if (this.getWorld().isClient) return;

        VillagerEntity villager = (VillagerEntity) (Object) this;
        VillagerData data = this.getVillagerData();

        // 1. Check if Cartographer and Map Trade
        if (data.getProfession() != VillagerProfession.CARTOGRAPHER || offer.getSellItem().getItem() != Items.FILLED_MAP) {
            return;
        }

        // 2. If Trade is Exhausted (Disabled)
        if (offer.isDisabled()) {
            TradeOfferList offers = villager.getOffers();
            int tradeIndex = offers.indexOf(offer);
            if (tradeIndex == -1) return;

            EM4ES.LOGGER.info("Map trade exhausted. Starting Async Restock...");

            // --- STEP A: IMMEDIATE VISUAL FEEDBACK (Main Thread) ---
            // Swap the "Sold Out" map with a "Restocking..." clock immediately.
            // This prevents the player from thinking the trade is just gone.
            TradeOffer placeholder = createRestockingPlaceholder();
            offers.set(tradeIndex, placeholder);

            // Force UI refresh so the player sees the clock instantly
            refreshPlayerUI(villager);

            // --- STEP B: PREPARE ASYNC DATA ---
            int level = data.getLevel();
            int searchRadiusBlocks = getRadiusForLevel(level);
            ServerWorld serverWorld = (ServerWorld) this.getWorld();
            VillagerDataAccessor accessor = (VillagerDataAccessor) villager;

            // --- STEP C: RUN SEARCH IN BACKGROUND (Async) ---
            // This moves the heavy math off the main thread. No more freezing!
            EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {

                int radiusInChunks = Math.max(1, searchRadiusBlocks / 16);

                // 1. Find Structure (Heavy Math, Read-Only)
                StructureSearchResult result = ExplorerMapTradeFactory.findStructure(
                        serverWorld,
                        villager.getBlockPos(),
                        accessor.getOfferedStructureMaps(),
                        radiusInChunks
                );

                // --- STEP D: APPLY RESULT (Main Thread) ---
                // We must sync back to the main thread to create the Map Item and update the list.
                this.getServer().execute(() -> {
                    try {
                        if (result != null) {
                            // 2. Create Map Item (World Write)
                            ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(1, radiusInChunks);
                            TradeOffer newMapTrade = factory.createTradeFromSearch(serverWorld, result, accessor.getOfferedStructureMaps());

                            if (newMapTrade != null) {
                                // Find our placeholder and swap it for the real map
                                int currentIndex = offers.indexOf(placeholder);
                                if (currentIndex != -1) {
                                    offers.set(currentIndex, newMapTrade);
                                    refreshPlayerUI(villager);
                                    EM4ES.LOGGER.info("Restock successful.");
                                }
                            }
                        } else {
                            // Optional: If nothing found, maybe turn the clock into "Out of Stock" barrier?
                            // For now, we leave the placeholder or remove it.
                            EM4ES.LOGGER.info("No replacement found.");
                        }
                    } catch (Exception e) {
                        EM4ES.LOGGER.error("Error updating trade UI", e);
                    }
                });
            });
        }
    }

    private void refreshPlayerUI(VillagerEntity villager) {
        if (villager.getCustomer() instanceof ServerPlayerEntity player) {
            if (player.currentScreenHandler instanceof MerchantScreenHandler) {
                player.sendTradeOffers(
                        player.currentScreenHandler.syncId,
                        villager.getOffers(),
                        0, // Level Progress
                        villager.getExperience(),
                        villager.isLeveledMerchant(),
                        villager.canRefreshTrades()
                );
            }
        }
    }

    private int getRadiusForLevel(int level) {
        switch (level) {
            case 1: return EM4ES.CARTOGRAPHER_L1_SEARCH_RADIUS;
            case 2: return EM4ES.CARTOGRAPHER_L2_SEARCH_RADIUS;
            case 3: return EM4ES.CARTOGRAPHER_L3_SEARCH_RADIUS;
            case 4: return EM4ES.CARTOGRAPHER_L4_SEARCH_RADIUS;
            case 5: return EM4ES.CARTOGRAPHER_L5_SEARCH_RADIUS;
            default: return 500;
        }
    }

    private TradeOffer createRestockingPlaceholder() {
        ItemStack icon = new ItemStack(Items.CLOCK);
        icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Restocking...").formatted(Formatting.YELLOW));

        // Dummy trade: 64 Bamboo -> Clock (Disabled)
        TradeOffer offer = new TradeOffer(
                new TradedItem(Items.BAMBOO, 64),
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