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
import net.minecraft.server.MinecraftServer;
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
        ServerWorld serverWorld = (ServerWorld) this.getWorld();

        if (accessor.isSearching()) return;
        accessor.setSearching(true);

        EM4ES.LOGGER.info("Starting async map generation for Wandering Trader...");

        TradeOffer placeholder = createPlaceholderTrade();
        trader.getOffers().add(placeholder);

        EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {
            List<StructureSearchResult> results = new ArrayList<>();
            int radiusInChunks = Math.max(1, EM4ES.WANDERING_TRADER_SEARCH_RADIUS / 16);

            // 1. ASYNC: Find multiple structures
            for (int i = 0; i < EM4ES.WANDERING_TRADER_MAP_COUNT; i++) {
                StructureSearchResult result = ExplorerMapTradeFactory.findStructure(
                        serverWorld,
                        trader.getBlockPos(),
                        accessor.getOfferedStructureMaps(),
                        radiusInChunks
                );
                if (result != null) {
                    results.add(result);
                    // Important: Add to the exclusion set locally so we don't find the same one twice in this loop
                    accessor.getOfferedStructureMaps().add(result.id());
                }
            }

            // 2. SYNC: Create Maps
            server.execute(() -> {
                try {
                    TradeOfferList offers = trader.getOffers();
                    offers.remove(placeholder);

                    ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(1, radiusInChunks);
                    List<TradeOffer> newTrades = new ArrayList<>();

                    for (StructureSearchResult res : results) {
                        TradeOffer offer = factory.createTradeFromSearch(serverWorld, res, accessor.getOfferedStructureMaps());
                        if (offer != null) newTrades.add(offer);
                    }

                    if (!newTrades.isEmpty()) {
                        offers.addAll(newTrades);
                        EM4ES.LOGGER.info("Added {} maps.", newTrades.size());
                    } else {
                        EM4ES.LOGGER.info("No maps found.");
                    }
                } finally {
                    accessor.setSearching(false);
                }
            });
        });
    }

    private TradeOffer createPlaceholderTrade() {
        ItemStack placeholderIcon = new ItemStack(Items.FILLED_MAP);
        placeholderIcon.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Map search in progress...").formatted(Formatting.GOLD));
        TradeOffer offer = new TradeOffer(new TradedItem(Items.PAPER), Optional.empty(), placeholderIcon, 0, 0, 0.0f);
        offer.disable();
        return offer;
    }
}