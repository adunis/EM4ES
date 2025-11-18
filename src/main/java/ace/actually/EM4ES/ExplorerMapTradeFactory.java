package ace.actually.EM4ES;

import com.mojang.datafixers.util.Pair;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ExplorerMapTradeFactory implements TradeOffers.Factory {
    private final int maxUses;
    private final int searchRadiusInChunks;

    private static List<RegistryKey<Structure>> CACHED_STRUCTURE_KEYS = null;

    public ExplorerMapTradeFactory(int maxUses, int searchRadiusInChunks) {
        this.maxUses = maxUses;
        this.searchRadiusInChunks = searchRadiusInChunks;
    }

    @Nullable
    @Override
    public TradeOffer create(Entity entity, Random random) {
        if (!(entity.getWorld() instanceof ServerWorld world) || !(entity instanceof VillagerDataAccessor accessor)) {
            return null;
        }
        StructureSearchResult result = findStructure(world, entity.getBlockPos(), accessor.getOfferedStructureMaps(), searchRadiusInChunks);
        if (result == null) return null;
        return createTradeFromSearch(world, result, accessor.getOfferedStructureMaps());
    }

    // --- ASYNC SAFE: Find the location ---
    @Nullable
    public static StructureSearchResult findStructure(ServerWorld world, BlockPos origin, Set<Identifier> skipIds, int radiusChunks) {
        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        if (CACHED_STRUCTURE_KEYS == null) initializeCache(structureRegistry);

        ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();
        int searchRadiusBlocks = radiusChunks * 16;

        List<RegistryKey<Structure>> candidates = CACHED_STRUCTURE_KEYS.stream()
                .filter(key -> !skipIds.contains(key.getValue()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        Collections.shuffle(candidates);

        long startTime = System.currentTimeMillis();
        int checkedCount = 0;

        for (RegistryKey<Structure> key : candidates) {
            if (System.currentTimeMillis() - startTime > EM4ES.MAX_SEARCH_TIME_MS) break;
            if (checkedCount >= EM4ES.SEARCH_SAMPLE_SIZE) break;
            checkedCount++;

            try {
                Optional<RegistryEntry.Reference<Structure>> entryOpt = structureRegistry.getEntry(key);
                if (entryOpt.isEmpty()) continue;

                RegistryEntryList<Structure> searchList = RegistryEntryList.of(entryOpt.get());

                Pair<BlockPos, RegistryEntry<Structure>> result = chunkGenerator.locateStructure(
                        world, searchList, origin, searchRadiusBlocks, false
                );

                if (result != null) {
                    Identifier foundId = result.getSecond().getKey().orElseThrow().getValue();
                    return new StructureSearchResult(result.getFirst(), foundId);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return null;
    }

    // --- MAIN THREAD: Create Item (Optimized) ---
    @Nullable
    public TradeOffer createTradeFromSearch(ServerWorld world, StructureSearchResult result, Set<Identifier> alreadyOffered) {
        // Create basic map item (Scale 2, shows decorations)
        ItemStack mapStack = FilledMapItem.createMap(world, result.pos().getX(), result.pos().getZ(), (byte) 2, true, true);

        // --- CRITICAL PERFORMANCE FIX ---
        // We do NOT call FilledMapItem.fillExplorationMap(world, mapStack).
        // That method forces chunk generation on the main thread.
        // Skipping it leaves the map blank (paper color) but keeps the server running smooth.

        // Add the Target Icon and Name
        EM4ES.addDecorationsAndColor(mapStack, result.pos(), result.id().toString(), result.id().hashCode());
        mapStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(EM4ES.formatName(result.id().getPath()) + " Map"));

        if (!mapStack.isEmpty()) {
            alreadyOffered.add(result.id());
            MapCost cost = EM4ES.getCostForStructure(result.id());
            TradedItem buyItem = new TradedItem(cost.item(), cost.count());
            return new TradeOffer(buyItem, mapStack, this.maxUses, 15, 0.2F);
        }
        return null;
    }

    private static synchronized void initializeCache(Registry<Structure> registry) {
        if (CACHED_STRUCTURE_KEYS != null) return;
        List<RegistryKey<Structure>> keys = new ArrayList<>();
        for (Identifier id : registry.getIds()) {
            if (EM4ES.getCostForStructure(id) != null) {
                registry.getKey(registry.get(id)).ifPresent(keys::add);
            }
        }
        CACHED_STRUCTURE_KEYS = keys;
    }
}