package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.EM4ES;
import ace.actually.EM4ES.ExplorerMapTradeFactory;
import ace.actually.EM4ES.VillagerDataAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(WanderingTraderEntity.class)
public abstract class WanderingMixin extends MerchantEntity {

    public WanderingMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "fillRecipes", at = @At("TAIL"))
    protected void addCustomMapTradesAsync(CallbackInfo ci) {
        if (this.getWorld().isClient) return;

        WanderingTraderEntity trader = (WanderingTraderEntity) (Object) this;
        VillagerDataAccessor accessor = (VillagerDataAccessor) trader;
        MinecraftServer server = this.getServer();

        // 1. PREVENT DUPLICATE SEARCHES
        if (accessor.isSearching()) return;
        accessor.setSearching(true);

        EM4ES.LOGGER.info("Starting async map generation for Wandering Trader...");

        // 2. ADD PLACEHOLDER IMMEDIATELY
        // This gives the player immediate visual feedback that something is happening.
        TradeOffer placeholder = createPlaceholderTrade();
        trader.getOffers().add(placeholder);

        // 3. RUN SEARCH IN BACKGROUND THREAD
        EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {
            List<TradeOffer> newTrades = new ArrayList<>();

            // Config is in Blocks (e.g., 2500), Factory needs Chunks (2500 / 16)
            int radiusInChunks = Math.max(1, EM4ES.WANDERING_TRADER_SEARCH_RADIUS / 16);

            ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(1, radiusInChunks);

            // Try to generate the amount of maps defined in config
            for (int i = 0; i < EM4ES.WANDERING_TRADER_MAP_COUNT; i++) {
                TradeOffer offer = factory.create(trader, trader.getRandom());
                if (offer != null) {
                    newTrades.add(offer);
                }
            }

            // 4. UPDATE TRADES ON MAIN SERVER THREAD
            if (server != null) {
                server.execute(() -> {
                    try {
                        TradeOfferList offers = trader.getOffers();

                        // Remove the "Searching..." placeholder
                        offers.remove(placeholder);

                        // Add the real maps
                        if (!newTrades.isEmpty()) {
                            offers.addAll(newTrades);
                            EM4ES.LOGGER.info("Replaced placeholder with {} maps.", newTrades.size());
                        } else {
                            EM4ES.LOGGER.info("Search finished but no maps were found.");
                        }
                    } catch (Exception e) {
                        EM4ES.LOGGER.error("Error updating trades on main thread", e);
                    } finally {
                        // Release the lock so it could theoretically search again if logic permitted
                        accessor.setSearching(false);
                    }
                });
            }
        });
    }

    /**
     * Creates a fake trade that says "Map search in progress..."
     * It is disabled so players cannot buy it.
     */
    private TradeOffer createPlaceholderTrade() {
        ItemStack placeholderIcon = new ItemStack(Items.FILLED_MAP);

        // Set the name to gold color
        placeholderIcon.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Map search in progress...").formatted(Formatting.GOLD));

        // Create the trade: 1 Paper -> "Searching..." Icon
        TradeOffer offer = new TradeOffer(
                new TradedItem(Items.PAPER),
                Optional.empty(),
                placeholderIcon,
                0,
                0,
                0.0f
        );

        // Disable the trade so it can't be clicked
        offer.disable();

        return offer;
    }
}