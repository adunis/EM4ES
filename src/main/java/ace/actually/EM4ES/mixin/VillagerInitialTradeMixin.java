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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(VillagerEntity.class)
public abstract class VillagerInitialTradeMixin extends MerchantEntity {

    public VillagerInitialTradeMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    /**
     * This method is called whenever a Villager levels up or gains a profession.
     * We hook in here to inject our custom maps immediately.
     */
    @Inject(method = "fillRecipes", at = @At("TAIL"))
    private void onFillRecipes(CallbackInfo ci) {
        if (this.getWorld().isClient) return;

        VillagerEntity villager = (VillagerEntity) (Object) this;

        // 1. Only affect Cartographers
        if (villager.getVillagerData().getProfession() != VillagerProfession.CARTOGRAPHER) {
            return;
        }

        // 2. Prevent Duplicate Searches (If they level up rapidly)
        VillagerDataAccessor accessor = (VillagerDataAccessor) villager;
        if (accessor.isSearching()) return;

        // 3. Get Config for this level
        int level = villager.getVillagerData().getLevel();
        int mapCount = getMapCountForLevel(level);
        int radiusBlocks = getRadiusForLevel(level);

        if (mapCount <= 0) return;

        accessor.setSearching(true);
        EM4ES.LOGGER.info("Starting async map generation for Cartographer Level {}...", level);

        // 4. Add Placeholder immediately
        TradeOffer placeholder = createPlaceholderTrade();
        villager.getOffers().add(placeholder);

        ServerWorld serverWorld = (ServerWorld) this.getWorld();

        // 5. Async Search
        EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {
            List<StructureSearchResult> results = new ArrayList<>();
            int radiusInChunks = Math.max(1, radiusBlocks / 16);

            // Find structures (Heavy Math)
            for (int i = 0; i < mapCount; i++) {
                StructureSearchResult result = ExplorerMapTradeFactory.findStructure(
                        serverWorld,
                        villager.getBlockPos(),
                        accessor.getOfferedStructureMaps(),
                        radiusInChunks
                );

                if (result != null) {
                    results.add(result);
                    // Mark as found locally so we don't find the same one twice in this loop
                    accessor.getOfferedStructureMaps().add(result.id());
                }
            }

            // 6. Sync Update (World Write)
            this.getServer().execute(() -> {
                try {
                    TradeOfferList offers = villager.getOffers();
                    // Remove placeholder
                    offers.remove(placeholder);

                    ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(1, radiusInChunks);
                    List<TradeOffer> newTrades = new ArrayList<>();

                    for (StructureSearchResult res : results) {
                        TradeOffer offer = factory.createTradeFromSearch(serverWorld, res, accessor.getOfferedStructureMaps());
                        if (offer != null) newTrades.add(offer);
                    }

                    if (!newTrades.isEmpty()) {
                        offers.addAll(newTrades);
                        EM4ES.LOGGER.info("Added {} maps to Cartographer.", newTrades.size());
                    }
                } catch (Exception e) {
                    EM4ES.LOGGER.error("Error finalizing cartographer trades", e);
                } finally {
                    accessor.setSearching(false);
                }
            });
        });
    }

    private int getMapCountForLevel(int level) {
        return switch (level) {
            case 1 -> EM4ES.CARTOGRAPHER_L1_MAP_COUNT;
            case 2 -> EM4ES.CARTOGRAPHER_L2_MAP_COUNT;
            case 3 -> EM4ES.CARTOGRAPHER_L3_MAP_COUNT;
            case 4 -> EM4ES.CARTOGRAPHER_L4_MAP_COUNT;
            case 5 -> EM4ES.CARTOGRAPHER_L5_MAP_COUNT;
            default -> 0;
        };
    }

    private int getRadiusForLevel(int level) {
        return switch (level) {
            case 1 -> EM4ES.CARTOGRAPHER_L1_SEARCH_RADIUS;
            case 2 -> EM4ES.CARTOGRAPHER_L2_SEARCH_RADIUS;
            case 3 -> EM4ES.CARTOGRAPHER_L3_SEARCH_RADIUS;
            case 4 -> EM4ES.CARTOGRAPHER_L4_SEARCH_RADIUS;
            case 5 -> EM4ES.CARTOGRAPHER_L5_SEARCH_RADIUS;
            default -> 500;
        };
    }

    private TradeOffer createPlaceholderTrade() {
        ItemStack placeholderIcon = new ItemStack(Items.FILLED_MAP);
        placeholderIcon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Searching for structures...").formatted(Formatting.GOLD));
        TradeOffer offer = new TradeOffer(new TradedItem(Items.PAPER), Optional.empty(), placeholderIcon, 0, 0, 0.0f);
        offer.disable();
        return offer;
    }
}