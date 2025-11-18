// src/main/java/ace/actually/EM4ES/ExplorerMapTradeFactory.java
package ace.actually.EM4ES;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.entity.Entity;
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
        if (!(entity.getWorld() instanceof ServerWorld world) || !(entity instanceof VillagerDataAccessor accessor)) {
            return null;
        }

        EM4ES.LOGGER.info("Starting map trade search for entity {} with radius {} chunks.", entity.getUuidAsString(), this.searchRadiusInChunks);

        Set<Identifier> alreadyOffered = accessor.getOfferedStructureMaps();
        EM4ES.LOGGER.info("Entity has already been offered {} maps.", alreadyOffered.size());

        ServerChunkManager chunkManager = world.getChunkManager();
        ChunkPos centerChunk = entity.getChunkPos();
        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        for (int radius = 0; radius <= this.searchRadiusInChunks; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    ChunkPos currentChunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                    WorldChunk chunk = chunkManager.getWorldChunk(currentChunkPos.x, currentChunkPos.z, false);

                    if (chunk != null) {
                        Map<Structure, LongSet> references = chunk.getStructureReferences();
                        if (references != null && !references.isEmpty()) {
                            EM4ES.LOGGER.info("Found {} structure references in chunk at {}", references.size(), currentChunkPos.toString());

                            for (Structure structure : references.keySet()) {
                                Identifier structureId = structureRegistry.getId(structure);

                                if (structureId == null) continue;

                                if (alreadyOffered.contains(structureId)) {
                                    //EM4ES.LOGGER.info("Skipping {}: Already offered.", structureId);
                                    continue;
                                }

                                if (EM4ES.getCostForStructure(structureId) == null) {
                                    //EM4ES.LOGGER.info("Skipping {}: No cost defined.", structureId);
                                    continue;
                                }

                                EM4ES.LOGGER.info("Found potential new structure: {}", structureId);
                                RegistryEntry<Structure> entryOpt = structureRegistry.getEntry(structure);
                                StructureStart structureStart = chunk.getStructureStart(entryOpt.value());

                                if (structureStart != null) {
                                    BlockPos structurePos = structureStart.getPos().getStartPos();
                                    ItemStack mapStack = EM4ES.makeMapFromPos(world, structurePos, structureId);

                                    if (!mapStack.isEmpty()) {
                                        EM4ES.LOGGER.info("SUCCESS! Creating trade for map to {} at {}", structureId, structurePos);
                                        alreadyOffered.add(structureId);
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
        EM4ES.LOGGER.info("Search finished. No new valid structures found to offer a map for.");
        return null;
    }
}