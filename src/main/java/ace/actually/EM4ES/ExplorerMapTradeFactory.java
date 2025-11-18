package ace.actually.EM4ES;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ExplorerMapTradeFactory implements TradeOffers.Factory {
    private final int maxUses;
    private final int searchRadiusInBlocks;

    public ExplorerMapTradeFactory(int maxUses, int searchRadiusInChunks) {
        this.maxUses = maxUses;
        this.searchRadiusInBlocks = searchRadiusInChunks * 16;
    }

    @Nullable
    @Override
    public TradeOffer create(Entity entity, Random random) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !(entity instanceof VillagerDataAccessor accessor)) {
            return null;
        }

        Set<Identifier> alreadyOffered = accessor.getOfferedStructureMaps();
        BlockPos origin = entity.getBlockPos();
        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        List<RegistryKey<Structure>> possibleStructures = new ArrayList<>();
        for (Identifier id : structureRegistry.getIds()) {
            if (!alreadyOffered.contains(id) && EM4ES.getCostForStructure(id) != null) {
                structureRegistry.getKey(structureRegistry.get(id)).ifPresent(possibleStructures::add);
            }
        }

        if (possibleStructures.isEmpty()) {
            EM4ES.LOGGER.warn("No new valid structure found to search for entity {}.", entity.getUuidAsString());
            return null;
        }

        Collections.shuffle(possibleStructures);
        List<RegistryKey<Structure>> searchSample = possibleStructures.subList(0, Math.min(possibleStructures.size(), EM4ES.SEARCH_SAMPLE_SIZE));

        EM4ES.LOGGER.info("Searching a sample of {} structures (out of {} possible).", searchSample.size(), possibleStructures.size());

        List<Pair<BlockPos, RegistryEntry<Structure>>> nearbyCandidates = new ArrayList<>();
        for (RegistryKey<Structure> structureKey : searchSample) {
            Optional<RegistryEntry.Reference<Structure>> entryOptional = structureRegistry.getEntry(structureKey);
            if (entryOptional.isPresent()) {
                RegistryEntryList<Structure> searchList = RegistryEntryList.of(entryOptional.get());
                Pair<BlockPos, RegistryEntry<Structure>> result = world.getChunkManager().getChunkGenerator().locateStructure(
                        world, searchList, origin, searchRadiusInBlocks, false
                );
                if (result != null) {
                    nearbyCandidates.add(result);
                }
            }
        }

        if (nearbyCandidates.isEmpty()) {
            EM4ES.LOGGER.warn("No structure found in the sample for entity {}.", entity.getUuidAsString());
            return null;
        }

        // --- THE FIX IS HERE ---
        // Sort candidates by horizontal distance (ignoring Y-axis)
        nearbyCandidates.sort(Comparator.comparingDouble(pair -> {
            BlockPos pos = pair.getFirst();
            double dx = pos.getX() - origin.getX();
            double dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz; // Manual calculation of squared distance on X and Z
        }));
        // --- END OF FIX ---

        Pair<BlockPos, RegistryEntry<Structure>> bestCandidate = nearbyCandidates.get(0);

        BlockPos structurePos = bestCandidate.getFirst();
        Identifier structureId = bestCandidate.getSecond().getKey().orElseThrow().getValue();

        EM4ES.LOGGER.info("Closest structure chosen from sample: {} at {}", structureId, structurePos);

        ItemStack mapStack = EM4ES.makeMapFromPos(world, structurePos, structureId);
        if (!mapStack.isEmpty()) {
            alreadyOffered.add(structureId);
            MapCost cost = EM4ES.getCostForStructure(structureId);
            TradedItem buyItem = new TradedItem(cost.item(), cost.count());

            return new TradeOffer(buyItem, mapStack, this.maxUses, 15, 0.2F);
        }

        return null;
    }
}