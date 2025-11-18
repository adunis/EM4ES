package ace.actually.EM4ES;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapColorComponent;
import net.minecraft.component.type.MapDecorationsComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.structure.Structure;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class EM4ES implements ModInitializer {

    public static final String MOD_ID = "em4es";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final Random RANDOM = new Random();

    // --- Config Fields ---
    private static volatile Map<Identifier, MapCost> STRUCTURE_COSTS = Collections.synchronizedMap(new HashMap<>());
    private static volatile List<Identifier> VALID_STRUCTURE_IDS = ImmutableList.of();
    private static MapCost defaultCost = MapCost.DEFAULT;

    // --- Configurable Gameplay Settings ---
    public static int WANDERING_TRADER_MAP_COUNT = 3;
    public static int CARTOGRAPHER_L1_MAP_COUNT = 3;
    public static int CARTOGRAPHER_L2_MAP_COUNT = 1;
    public static int CARTOGRAPHER_L3_MAP_COUNT = 1;
    public static int CARTOGRAPHER_L4_MAP_COUNT = 1;
    public static int CARTOGRAPHER_L5_MAP_COUNT = 1;
    public static int CARTOGRAPHER_SEARCH_RADIUS = 1500;
    public static int WANDERING_TRADER_SEARCH_RADIUS = 2500;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);


        // Livello 1: Novice (Raggio di 50 chunk, circa 800 blocchi)
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 1, factories -> {
            for (int i = 0; i < CARTOGRAPHER_L1_MAP_COUNT; i++)
                factories.add(new ExplorerMapTradeFactory(5, 50));
        });

        // Livello 2: Apprentice (Raggio di 75 chunk, circa 1200 blocchi)
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 2, factories -> {
            for (int i = 0; i < CARTOGRAPHER_L2_MAP_COUNT; i++)
                factories.add(new ExplorerMapTradeFactory(4, 75));
        });

        // Livello 3: Journeyman (Raggio di 100 chunk, circa 1600 blocchi)
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 3, factories -> {
            for (int i = 0; i < CARTOGRAPHER_L3_MAP_COUNT; i++)
                factories.add(new ExplorerMapTradeFactory(3, 100));
        });

        // Livello 4: Expert (Raggio di 125 chunk, circa 2000 blocchi)
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 4, factories -> {
            for (int i = 0; i < CARTOGRAPHER_L4_MAP_COUNT; i++)
                factories.add(new ExplorerMapTradeFactory(2, 125));
        });

        // Livello 5: Master (Raggio di 150 chunk, circa 2400 blocchi)
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 5, factories -> {
            for (int i = 0; i < CARTOGRAPHER_L5_MAP_COUNT; i++)
                factories.add(new ExplorerMapTradeFactory(1, 150));
        });
    }

    private void onServerStarted(MinecraftServer server) {
        File configFile = new File("./config/EM4ES/StructureCosts.properties");
        try {
            if (!configFile.exists()) {
                LOGGER.info("Structure cost config not found. Creating a new default one...");
                configFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("# This file configures the costs and behavior for explorer maps.\n\n");
                    writer.write("# --- Wandering Trader Settings ---\n");
                    writer.write("trader.mapCount = 3\n");
                    writer.write("trader.searchRadius = 2500\n\n");
                    writer.write("# --- Cartographer Settings ---\n");
                    writer.write("cartographer.searchRadius = 1500\n");
                    writer.write("cartographer.level1.mapCount = 3\n");
                    writer.write("cartographer.level2.mapCount = 1\n");
                    writer.write("cartographer.level3.mapCount = 1\n");
                    writer.write("cartographer.level4.mapCount = 1\n");
                    writer.write("cartographer.level5.mapCount = 1\n\n");
                    writer.write("# --- Structure Costs ---\n");
                    writer.write("default.cost = 10 minecraft:trial_key\n\n");

                    Registry<Structure> structureRegistry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
                    for (Identifier id : structureRegistry.getIds()) {
                        writer.write(id.toString() + " = 10 minecraft:trial_key\n");
                    }
                }
            }

            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }

            WANDERING_TRADER_MAP_COUNT = Integer.parseInt(props.getProperty("trader.mapCount", "10"));
            WANDERING_TRADER_SEARCH_RADIUS = Integer.parseInt(props.getProperty("trader.searchRadius", "2500"));
            CARTOGRAPHER_SEARCH_RADIUS = Integer.parseInt(props.getProperty("cartographer.searchRadius", "1500"));
            CARTOGRAPHER_L1_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level1.mapCount", "5"));
            CARTOGRAPHER_L2_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level2.mapCount", "3"));
            CARTOGRAPHER_L3_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level3.mapCount", "3"));
            CARTOGRAPHER_L4_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level4.mapCount", "3"));
            CARTOGRAPHER_L5_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level5.mapCount", "3"));

            List<String> lines = Files.readAllLines(configFile.toPath());
            Map<Identifier, MapCost> loadedCosts = new HashMap<>();
            List<Identifier> validIds = new ArrayList<>();
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty() || !line.contains("=")) continue;
                String[] parts = line.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (key.equals("default.cost")) {
                    defaultCost = MapCost.fromString(value);
                    continue;
                }
                if (!key.contains(":")) continue; // Skip general settings
                Identifier structureId = Identifier.tryParse(key);
                if (structureId != null) {
                    loadedCosts.put(structureId, MapCost.fromString(value));
                    validIds.add(structureId);
                }
            }
            STRUCTURE_COSTS = Collections.synchronizedMap(loadedCosts);
            VALID_STRUCTURE_IDS = Collections.unmodifiableList(validIds);
            LOGGER.info("Successfully loaded costs for {} structures.", STRUCTURE_COSTS.size());

        } catch (Exception e) {
            LOGGER.error("FATAL: Failed to read, parse, or create config file!", e);
        }
    }

    /**
     * Finds a specific structure using a spiraling, loaded-chunk-only search pattern.
     * This is the safest and most performant method, guaranteeing no lag from chunk generation.
     */
    public static ItemStack createMapForStructure(ServerWorld world, BlockPos searchCenter, Identifier structureId, int searchRadiusChunks) {
        Optional<RegistryEntry.Reference<Structure>> structureEntryOpt = world.getRegistryManager()
                .get(RegistryKeys.STRUCTURE)
                .getEntry(structureId);

        if (structureEntryOpt.isEmpty()) {
            LOGGER.warn("Attempted to find a map for an unknown or unregistered structure: {}", structureId);
            return ItemStack.EMPTY;
        }

        // Get the actual RegistryEntry from the Optional
        RegistryEntry<Structure> structureEntry = structureEntryOpt.get();

        ServerChunkManager chunkManager = world.getChunkManager();
        ChunkPos centerChunk = new ChunkPos(searchCenter);

        for (int radius = 0; radius <= searchRadiusChunks; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    ChunkPos currentChunkPos = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                    WorldChunk chunk = chunkManager.getWorldChunk(currentChunkPos.x, currentChunkPos.z, false);

                    if (chunk != null) {
                        // --- THE FIX IS HERE ---
                        // The method returns a Map of the raw Structure, not the RegistryEntry.
                        Map<Structure, LongSet> references = chunk.getStructureReferences();

                        // We must get the raw .value() from our RegistryEntry to check the map.
                        if (references != null && references.containsKey(structureEntry.value())) {

                            // IMPORTANT: We use the RegistryEntry to get the start pos, not the raw value.
                            BlockPos structurePos = chunk.getStructureStart(structureEntry.value()).getPos().getStartPos();
                            LOGGER.info("Found structure {} in loaded chunk at {}. Creating map.", structureId, structurePos);
                            return makeMapFromPos(world, structurePos, structureId);
                        }
                    }
                }
            }
        }

        LOGGER.debug("Could not find structure {} in any loaded chunk within a {} chunk radius of {}.", structureId, searchRadiusChunks, searchCenter);
        return ItemStack.EMPTY;
    }



    public static Identifier getRandomStructureId() {
        if (VALID_STRUCTURE_IDS.isEmpty()) return null;
        return VALID_STRUCTURE_IDS.get(RANDOM.nextInt(VALID_STRUCTURE_IDS.size()));
    }

    public static MapCost getCostForStructure(Identifier structureId) {
        return STRUCTURE_COSTS.getOrDefault(structureId, defaultCost);
    }

    public static ItemStack makeMapFromPos(ServerWorld world, BlockPos pos, Identifier structureId) {
        ItemStack mapStack = FilledMapItem.createMap(world, pos.getX(), pos.getZ(), (byte) 2, true, true);
        FilledMapItem.fillExplorationMap(world, mapStack);
        addDecorationsAndColor(mapStack, pos, structureId.toString(), structureId.hashCode());
        mapStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(formatName(structureId.getPath()) + " Map"));
        return mapStack;
    }

    public static void addDecorationsAndColor(ItemStack stack, BlockPos pos, String key, int seed) {
        RegistryEntry<MapDecorationType> decorationType = MapDecorationTypes.TARGET_X;
        stack.apply(DataComponentTypes.MAP_DECORATIONS, MapDecorationsComponent.DEFAULT, component -> {
            var newDecoration = new MapDecorationsComponent.Decoration(decorationType, pos.getX(), pos.getZ(), 180.0f);
            return component.with(key, newDecoration);
        });
        Random seededRandom = new Random(seed);
        int mapColor = (seededRandom.nextInt(255) << 16) | (seededRandom.nextInt(255) << 8) | seededRandom.nextInt(255);
        stack.set(DataComponentTypes.MAP_COLOR, new MapColorComponent(mapColor));
    }

    public static String formatName(String name) {
        name = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name.trim();
        return WordUtils.capitalizeFully(name.replace('_', ' '));
    }
}