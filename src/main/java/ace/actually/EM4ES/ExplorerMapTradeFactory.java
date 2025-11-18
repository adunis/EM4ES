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
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ExplorerMapTradeFactory implements TradeOffers.Factory {
    private final int maxUses;
    private final int searchRadiusInBlocks;

    // Cache valid keys to avoid iterating the registry and re-checking costs on every single trade attempt.
    private static List<RegistryKey<Structure>> CACHED_STRUCTURE_KEYS = null;

    public ExplorerMapTradeFactory(int maxUses, int searchRadiusInChunks) {
        this.maxUses = maxUses;
        this.searchRadiusInBlocks = searchRadiusInChunks * 16; // Convert chunks to blocks
    }

    @Nullable
    @Override
    public TradeOffer create(Entity entity, Random random) {
        // 1. VALIDATION
        if (!(entity.getWorld() instanceof ServerWorld world) || !(entity instanceof VillagerDataAccessor accessor)) {
            return null;
        }

        BlockPos origin = entity.getBlockPos();
        Registry<Structure> structureRegistry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);

        // 2. INITIALIZE CACHE (ONCE)
        if (CACHED_STRUCTURE_KEYS == null) {
            initializeCache(structureRegistry);
        }

        Set<Identifier> alreadyOffered = accessor.getOfferedStructureMaps();
        ChunkGenerator chunkGenerator = world.getChunkManager().getChunkGenerator();

        // 3. FILTER CANDIDATES
        // Remove structures this specific villager has already sold.
        List<RegistryKey<Structure>> candidates = CACHED_STRUCTURE_KEYS.stream()
                .filter(key -> !alreadyOffered.contains(key.getValue()))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) return null;

        // 4. SHUFFLE
        // Essential because we stop on the first match. This ensures we get random structures,
        // not just the first one in the registry alphabet.
        Collections.shuffle(candidates);

        Pair<BlockPos, RegistryEntry<Structure>> bestMatch = null;
        long startTime = System.currentTimeMillis();
        int checkedCount = 0;

        // 5. SEARCH LOOP
        for (RegistryKey<Structure> key : candidates) {

            // A. Time Limit Check (Prevents server lag)
            if (System.currentTimeMillis() - startTime > EM4ES.MAX_SEARCH_TIME_MS) {
                EM4ES.LOGGER.debug("Search timed out (>{}ms). Stopping.", EM4ES.MAX_SEARCH_TIME_MS);
                break;
            }

            // B. Sample Size Check (Prevents checking 600 structures)
            if (checkedCount >= EM4ES.SEARCH_SAMPLE_SIZE) {
                EM4ES.LOGGER.debug("Search hit limit of {} attempts. Stopping.", EM4ES.SEARCH_SAMPLE_SIZE);
                break;
            }
            checkedCount++;

            try {
                Optional<RegistryEntry.Reference<Structure>> entryOpt = structureRegistry.getEntry(key);
                if (entryOpt.isEmpty()) continue;

                RegistryEntryList<Structure> searchList = RegistryEntryList.of(entryOpt.get());

                // C. Locate Structure (Heavy Operation)
                // 'false' for skipExistingChunks is usually faster for finding *new* things
                Pair<BlockPos, RegistryEntry<Structure>> result = chunkGenerator.locateStructure(
                        world, searchList, origin, searchRadiusInBlocks, false
                );

                if (result != null) {
                    bestMatch = result;
                    // D. STOP IMMEDIATELY
                    // We found a valid structure. Do not waste time looking for a "closer" one.
                    // This is the key to fixing the "Search takes 30 seconds" issue.
                    EM4ES.LOGGER.debug("Match found: {}. Stopping search immediately.", key.getValue());
                    break;
                }
            } catch (Exception e) {
                // If a specific modded structure crashes the locator, log it and skip to the next one
                // so the thread doesn't die.
                EM4ES.LOGGER.debug("Failed to locate structure {}: {}", key.getValue(), e.getMessage());
            }
        }

        // 6. CREATE TRADE OFFER
        if (bestMatch == null) {
            return null; // Found nothing within limits
        }

        BlockPos structurePos = bestMatch.getFirst();
        Identifier structureId = bestMatch.getSecond().getKey().orElseThrow().getValue();

        // Generate the map item
        ItemStack mapStack = EM4ES.makeMapFromPos(world, structurePos, structureId);

        if (!mapStack.isEmpty()) {
            // Mark as offered so this villager doesn't sell the same map twice
            alreadyOffered.add(structureId);

            // Get cost from config
            MapCost cost = EM4ES.getCostForStructure(structureId);
            TradedItem buyItem = new TradedItem(cost.item(), cost.count());

            return new TradeOffer(buyItem, mapStack, this.maxUses, 15, 0.2F);
        }

        return null;
    }

    /**
     * Caches the list of all valid structure keys from the registry.
     * Only includes structures that have a configured cost (which is all of them by default).
     */
    private synchronized void initializeCache(Registry<Structure> registry) {
        if (CACHED_STRUCTURE_KEYS != null) return;

        List<RegistryKey<Structure>> keys = new ArrayList<>();
        for (Identifier id : registry.getIds()) {
            if (EM4ES.getCostForStructure(id) != null) {
                registry.getKey(registry.get(id)).ifPresent(keys::add);
            }
        }
        CACHED_STRUCTURE_KEYS = keys;
        EM4ES.LOGGER.info("Initialized structure cache with {} entries.", keys.size());
    }
}