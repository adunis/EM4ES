package ace.actually.EM4ES;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ExplorerMapTradeFactory implements TradeOffers.Factory {
    private final int maxUses;
    private final int searchRadius;

    public ExplorerMapTradeFactory(int maxUses, int searchRadius) {
        this.maxUses = maxUses;
        this.searchRadius = searchRadius;
    }

    @Nullable
    @Override
    public TradeOffer create(Entity entity, Random random) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return null;
        }

        // Use the configurable max attempts from the main class
        for (int i = 0; i < EM4ES.FACTORY_MAX_ATTEMPTS; i++) {
            Identifier structureId = EM4ES.getRandomStructureId();
            if (structureId == null) {
                return null;
            }

            ItemStack mapStack = EM4ES.makeRandomMap(world, entity.getBlockPos(), structureId, this.searchRadius);

            if (!mapStack.isEmpty()) {
                MapCost cost = EM4ES.getCostForStructure(structureId);
                TradedItem buyItem = new TradedItem(cost.item(), cost.count());

                return new TradeOffer(
                        buyItem,
                        Optional.empty(),
                        mapStack,
                        maxUses,
                        15,
                        0.2F
                );
            }
        }

        EM4ES.LOGGER.debug("Failed to create a map trade after {} attempts within a {} block radius.", EM4ES.FACTORY_MAX_ATTEMPTS, this.searchRadius);
        return null;
    }
}