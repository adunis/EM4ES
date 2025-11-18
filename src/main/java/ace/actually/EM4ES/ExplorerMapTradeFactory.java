package ace.actually.EM4ES;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ExplorerMapTradeFactory implements TradeOffers.Factory {
    private final int maxUses;
    private final int searchRadiusInChunks;
    private final int mapCount;

    public ExplorerMapTradeFactory(int maxUses, int searchRadius, int mapCount) {
        this.maxUses = maxUses;
        this.searchRadiusInChunks = searchRadius;
        this.mapCount = mapCount;
    }

    public ExplorerMapTradeFactory(int maxUses, int searchRadius) {
        this(maxUses, searchRadius, 1);
    }

    @Nullable
    @Override
    public TradeOffer create(Entity entity, Random random) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !(entity instanceof VillagerEntity villager)) {
            return null;
        }

        Set<Identifier> alreadyOffered = ((VillagerDataAccessor) villager).getOfferedStructureMaps();

        ServerChunkManager chunkManager = world.getChunkManager();
        ChunkPos centerChunk = entity.getChunkPos();

        // We only want to find one new structure to offer, up to mapCount
        // The loop should iterate through potential chunks and try to find a *new* structure
        // that hasn't been offered before and whose map can be created.

        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        for (int radius = 0; radius <= this.searchRadiusInChunks; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Only check the outer ring if radius > 0
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    ChunkPos currentChunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                    WorldChunk chunk = chunkManager.getWorldChunk(currentChunkPos.x, currentChunkPos.z, false);

                    if (chunk != null) {
                        Map<Structure, LongSet> references = chunk.getStructureReferences();
                        if (references != null && !references.isEmpty()) {

                            for (Structure structure : references.keySet()) {
                                Identifier structureId = structureRegistry.getId(structure);

                                // Check if the structure is valid, not already offered, and has a defined cost
                                if (structureId != null && !alreadyOffered.contains(structureId) && EM4ES.getCostForStructure(structureId) != null) {
                                    RegistryEntry<Structure> entryOpt = structureRegistry.getEntry(structure);

                                    // Get the StructureStart object to confirm it's a valid, generated structure instance
                                    StructureStart structureStart = chunk.getStructureStart(entryOpt.value());

                                    if (structureStart != null) {
                                        BlockPos structurePos = structureStart.getPos().getStartPos();

                                        // Try to create the map
                                        ItemStack mapStack = EM4ES.makeMapFromPos(world, structurePos, structureId);

                                        // If the map was successfully created (not empty), we found a valid trade!
                                        if (!mapStack.isEmpty()) {
                                            // Add this new structure to the villager's memory to avoid re-offering it
                                            alreadyOffered.add(structureId);

                                            // Create and return the trade offer
                                            MapCost cost = EM4ES.getCostForStructure(structureId);
                                            TradedItem buyItem = new TradedItem(cost.item(), cost.count());
                                            return new TradeOffer(buyItem, Optional.empty(), mapStack, this.maxUses, 15, 0.2F);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // If we iterate through all chunks within the search radius and don't find a new, valid structure to offer, return null.
        return null;
    }
}