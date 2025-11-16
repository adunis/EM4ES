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
    // We no longer store the searchRadius here, as it will now be dynamic.

    // The initial search radius is now passed in, but it will increase on failure.
    public ExplorerMapTradeFactory(int maxUses, int initialSearchRadius) {
        this.maxUses = maxUses;
        // The searchRadius parameter is now used as the starting point.
        // We are keeping it in the constructor for the cartographer progression.
    }

    @Nullable
    @Override
    public TradeOffer create(Entity entity, Random random) {
        if (!(entity.getWorld() instanceof ServerWorld world)) {
            return null;
        }

        // --- NEW EXPANDING SEARCH LOGIC ---

        final int MAX_ATTEMPTS = 10;
        int currentSearchRadius = 1000; // Start with a 1000 block radius.
        final int RADIUS_INCREMENT = 750; // Increase the radius by this much on each failure.

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            // 1. Get a random structure ID.
            Identifier structureId = EM4ES.getRandomStructureId();
            if (structureId == null) {
                EM4ES.LOGGER.warn("Could not get a random structure to sell. Is the config empty?");
                return null;
            }

            // 2. Attempt to create a map with the CURRENT search radius.
            EM4ES.LOGGER.info("Attempt {} to find structure '{}' within {} blocks.", i + 1, structureId, currentSearchRadius);
            ItemStack mapStack = EM4ES.makeRandomMap(world, entity.getBlockPos(), structureId, currentSearchRadius);

            // 3. If we succeed, create the trade and return immediately.
            if (!mapStack.isEmpty()) {
                EM4ES.LOGGER.info("Success! Found structure '{}' on attempt {}.", structureId, i + 1);
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

            // 4. If we failed, INCREASE THE SEARCH RADIUS for the next attempt.
            currentSearchRadius += RADIUS_INCREMENT;
        }

        // 5. If we finish the loop without finding anything, we give up.
        EM4ES.LOGGER.warn("Failed to create a map trade after {} attempts. Max search radius reached: {}.", MAX_ATTEMPTS, currentSearchRadius - RADIUS_INCREMENT);
        return null;
    }
}