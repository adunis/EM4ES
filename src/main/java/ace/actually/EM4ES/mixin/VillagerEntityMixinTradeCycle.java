package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.EM4ES;
import ace.actually.EM4ES.ExplorerMapTradeFactory;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixinTradeCycle {

    @Shadow public abstract VillagerData getVillagerData();

    // This code activates AFTER a trade has been used.
    @Inject(method = "afterUsing", at = @At("TAIL"))
    private void replaceUsedMapTrade(TradeOffer offer, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        VillagerData data = this.getVillagerData();

        // Check if the villager is a cartographer and if the trade is one of our maps
        if (data.getProfession() != VillagerProfession.CARTOGRAPHER || !(offer.getSellItem().getItem() instanceof FilledMapItem)) {
            return;
        }

        // If the trade is exhausted (i.e., uses >= maxUses)
        if (offer.isDisabled()) {
            EM4ES.LOGGER.info("Map trade exhausted. Attempting to replace with a new one.");
            TradeOfferList offers = villager.getOffers();

            // Remove the old exhausted trade
            offers.remove(offer);

            // Determine search parameters based on the villager's current level
            int level = data.getLevel();
            int searchRadius = 0;
            int maxUses = 1; // Now always 1 to ensure replacement

            switch (level) {
                case 1 -> searchRadius = EM4ES.CARTOGRAPHER_L1_SEARCH_RADIUS;
                case 2 -> searchRadius = EM4ES.CARTOGRAPHER_L2_SEARCH_RADIUS;
                case 3 -> searchRadius = EM4ES.CARTOGRAPHER_L3_SEARCH_RADIUS;
                case 4 -> searchRadius = EM4ES.CARTOGRAPHER_L4_SEARCH_RADIUS;
                case 5 -> searchRadius = EM4ES.CARTOGRAPHER_L5_SEARCH_RADIUS;
                default -> { return; } // No action for invalid levels
            }

            // Use the same search logic with expandable radius to find a new trade
            int currentSearchRadius = searchRadius;
            TradeOffer newTrade = null;
            int retries = 0;

            while (newTrade == null && retries < 5) {
                if (retries > 0) {
                    EM4ES.LOGGER.warn("Replacement search failed at radius {}. Increasing and retrying...", currentSearchRadius);
                    currentSearchRadius = (int) (currentSearchRadius * 1.5);
                }
                ExplorerMapTradeFactory factory = new ExplorerMapTradeFactory(maxUses, currentSearchRadius);
                newTrade = factory.create(villager, villager.getRandom());
                retries++;
            }

            if (newTrade != null) {
                offers.add(newTrade);
                EM4ES.LOGGER.info("Map replacement successful!");
            } else {
                EM4ES.LOGGER.error("Could not find a new map to replace the old one.");
            }
        }
    }
}