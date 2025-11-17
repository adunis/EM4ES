package ace.actually.EM4ES.mixin;

import ace.actually.EM4ES.EM4ES;
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

        // Use the configurable values loaded from the properties file.
        for (int i = 0; i < EM4ES.WANDERING_TRADER_MAP_COUNT; i++) {
            TradeOffer newOffer = new ExplorerMapTradeFactory(
                    1, // Single use
                    EM4ES.WANDERING_TRADER_SEARCH_RADIUS
            ).create(this, this.random);

            if (newOffer != null) {
                offers.add(newOffer);
            }
        }
    }
}