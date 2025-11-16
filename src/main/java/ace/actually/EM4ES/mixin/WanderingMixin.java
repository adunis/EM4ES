package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.ExplorerMapTradeFactory;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WanderingTraderEntity.class)
public abstract class WanderingMixin extends MerchantEntity {

    public WanderingMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "fillRecipes", at = @At("TAIL"))
    protected void addCustomMapTrades(CallbackInfo ci) {
        TradeOfferList offers = this.getOffers();

        // --- NEW LOGIC: ADD 3 MAPS ---
        for (int i = 0; i < 3; i++) {
            // Create a trade for a single-use map that costs 10 Trial Keys and searches 1000 blocks.
            TradeOffer newOffer = new ExplorerMapTradeFactory(
                    1,     // maxUses
                    1000   // searchRadius
            ).create(this, this.random);

            if (newOffer != null) {
                offers.add(newOffer);
            }
        }
    }
}