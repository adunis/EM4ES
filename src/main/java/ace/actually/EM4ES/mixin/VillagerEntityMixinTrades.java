package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.EM4ES;
import ace.actually.EM4ES.ExplorerMapTradeFactory;
import ace.actually.EM4ES.VillagerDataAccessor;
import com.google.common.collect.Lists;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixinTrades {

    @Inject(method = "fillRecipes", at = @At("TAIL"))
    private void manageMapTradesAsync(CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        VillagerDataAccessor accessor = (VillagerDataAccessor) villager;
        VillagerData data = villager.getVillagerData(); // Direct call to public method
        MinecraftServer server = villager.getServer();

        if (server == null || data.getProfession() != VillagerProfession.CARTOGRAPHER) return;

        if (accessor.isSearching()) {
            EM4ES.LOGGER.warn("Map generation request ignored for {}: a search is already in progress.", villager.getUuidAsString());
            return;
        }

        int currentLevel = data.getLevel();
        int lastLevel = accessor.getLastMapLevelGenerated();

        int totalRequiredMaps = 0;
        for (int level = 1; level <= currentLevel; level++) {
            totalRequiredMaps += getMapCountForLevel(level);
        }

        int currentMapCount = (int) villager.getOffers().stream().filter(o -> o.getSellItem().isOf(Items.FILLED_MAP)).count();
        int mapsToAdd = totalRequiredMaps - currentMapCount;

        if (currentLevel > lastLevel) {
            mapsToAdd = Math.max(mapsToAdd, getMapCountForLevel(currentLevel));
        }

        if (mapsToAdd <= 0) return;

        accessor.setSearching(true);
        TradeOffer placeholder = createPlaceholderTrade();
        villager.getOffers().add(placeholder);

        EM4ES.LOGGER.info("Starting asynchronous search for {} maps for villager {}.", mapsToAdd, villager.getUuidAsString());

        final int finalMapsToAdd = mapsToAdd;
        EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {
            List<TradeOffer> foundOffers = Lists.newArrayList();
            for (int i = 0; i < finalMapsToAdd; i++) {
                int searchRadius = getSearchRadiusForLevel(currentLevel);
                int maxUses = getMaxUsesForLevel(currentLevel);

                ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(maxUses, searchRadius);
                TradeOffer newTrade = factory.create(villager, villager.getRandom());
                if (newTrade != null) {
                    foundOffers.add(newTrade);
                }
            }

            server.execute(() -> {
                try {
                    villager.getOffers().remove(placeholder);
                    villager.getOffers().addAll(foundOffers);

                    if (currentLevel > lastLevel) {
                        accessor.setLastMapLevelGenerated(currentLevel);
                    }
                    EM4ES.LOGGER.info("Asynchronous search complete. Added {} maps to villager {}.", foundOffers.size(), villager.getUuidAsString());
                } finally {
                    accessor.setSearching(false);
                }
            });
        });
    }

    private TradeOffer createPlaceholderTrade() {
        ItemStack placeholderStack = new ItemStack(Items.MAP);
        placeholderStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§eMap search in progress..."));
        TradeOffer offer = new TradeOffer(new TradedItem(Items.PAPER), Optional.empty(), placeholderStack, 0, 0, 0.0f);
        offer.disable();
        return offer;
    }

    // Utility methods
    private int getMapCountForLevel(int level) { return switch (level) { case 1 -> EM4ES.CARTOGRAPHER_L1_MAP_COUNT; case 2 -> EM4ES.CARTOGRAPHER_L2_MAP_COUNT; case 3 -> EM4ES.CARTOGRAPHER_L3_MAP_COUNT; case 4 -> EM4ES.CARTOGRAPHER_L4_MAP_COUNT; case 5 -> EM4ES.CARTOGRAPHER_L5_MAP_COUNT; default -> 0; }; }
    private int getSearchRadiusForLevel(int level) { return switch (level) { case 1 -> EM4ES.CARTOGRAPHER_L1_SEARCH_RADIUS; case 2 -> EM4ES.CARTOGRAPHER_L2_SEARCH_RADIUS; case 3 -> EM4ES.CARTOGRAPHER_L3_SEARCH_RADIUS; case 4 -> EM4ES.CARTOGRAPHER_L4_SEARCH_RADIUS; case 5 -> EM4ES.CARTOGRAPHER_L5_SEARCH_RADIUS; default -> 100; }; }
    private int getMaxUsesForLevel(int level) { return switch (level) { case 1 -> EM4ES.CARTOGRAPHER_L1_MAX_USES; case 2 -> EM4ES.CARTOGRAPHER_L2_MAX_USES; case 3 -> EM4ES.CARTOGRAPHER_L3_MAX_USES; case 4 -> EM4ES.CARTOGRAPHER_L4_MAX_USES; case 5 -> EM4ES.CARTOGRAPHER_L5_MAX_USES; default -> 1; }; }
}