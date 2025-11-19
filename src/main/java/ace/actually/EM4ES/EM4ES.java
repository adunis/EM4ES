package ace.actually.EM4ES;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EM4ES implements ModInitializer {

    public static final String MOD_ID = "em4es";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    // --- Data Structures ---
    private static volatile Map<Identifier, MapCost> STRUCTURE_COSTS = Collections.synchronizedMap(new HashMap<>());
    public static volatile List<Identifier> VALID_STRUCTURE_IDS = ImmutableList.of();
    private static MapCost defaultCost = MapCost.DEFAULT;

    // --- Thread Pool ---
    public static final ExecutorService MAP_SEARCH_EXECUTOR = Executors.newSingleThreadExecutor();

    // --- Configurable Settings ---
    public static int SEARCH_SAMPLE_SIZE = 40;
    public static long MAX_SEARCH_TIME_MS = 1500;

    public static int WANDERING_TRADER_MAP_COUNT = 20;
    public static int WANDERING_TRADER_SEARCH_RADIUS = 2500;

    public static int CARTOGRAPHER_L1_MAP_COUNT = 5;
    public static int CARTOGRAPHER_L1_SEARCH_RADIUS = 500;
    public static int CARTOGRAPHER_L1_MAX_USES = 1;

    public static int CARTOGRAPHER_L2_MAP_COUNT = 5;
    public static int CARTOGRAPHER_L2_SEARCH_RADIUS = 750;
    public static int CARTOGRAPHER_L2_MAX_USES = 1;

    public static int CARTOGRAPHER_L3_MAP_COUNT = 5;
    public static int CARTOGRAPHER_L3_SEARCH_RADIUS = 1000;
    public static int CARTOGRAPHER_L3_MAX_USES = 1;

    public static int CARTOGRAPHER_L4_MAP_COUNT = 5;
    public static int CARTOGRAPHER_L4_SEARCH_RADIUS = 1500;
    public static int CARTOGRAPHER_L4_MAX_USES = 1;

    public static int CARTOGRAPHER_L5_MAP_COUNT = 3;
    public static int CARTOGRAPHER_L5_SEARCH_RADIUS = 2500;
    public static int CARTOGRAPHER_L5_MAX_USES = 1;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
    }

    private void onServerStarted(MinecraftServer server) {
        File configFile = new File("./config/EM4ES/EM4ES.properties");
        try {
            if (!configFile.exists()) {
                LOGGER.info("Structure cost config not found. Creating a new default one...");
                configFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("# This file configures the costs and behavior for explorer maps.\n");
                    writer.write("# Restart the server to apply changes.\n\n");

                    writer.write("# --- Performance Settings ---\n");
                    writer.write("search.sampleSize = 40\n");
                    writer.write("search.maxTimeMs = 1500\n\n");

                    writer.write("# --- Wandering Trader Settings ---\n");
                    writer.write("trader.mapCount = 3\n");
                    writer.write("trader.searchRadius = 1000\n\n");

                    writer.write("# --- Cartographer Settings ---\n");
                    writer.write("cartographer.level1.mapCount = 2\n");
                    writer.write("cartographer.level1.searchRadius = 500\n");
                    writer.write("cartographer.level1.maxUses = 1\n");
                    writer.write("cartographer.level2.mapCount = 2\n");
                    writer.write("cartographer.level2.searchRadius = 750\n");
                    writer.write("cartographer.level2.maxUses = 1\n");
                    writer.write("cartographer.level3.mapCount = 2\n");
                    writer.write("cartographer.level3.searchRadius = 1000\n");
                    writer.write("cartographer.level3.maxUses = 1\n");
                    writer.write("cartographer.level4.mapCount = 2\n");
                    writer.write("cartographer.level4.searchRadius = 1500\n");
                    writer.write("cartographer.level4.maxUses = 1\n");
                    writer.write("cartographer.level5.mapCount = 2\n");
                    writer.write("cartographer.level5.searchRadius = 2500\n");
                    writer.write("cartographer.level5.maxUses = 1\n\n");

                    writer.write("# --- Structure Costs ---\n");
                    writer.write("default.cost = 1 minecraft:emerald\n\n");

                    Registry<Structure> structureRegistry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
                    for (Identifier id : structureRegistry.getIds()) {
                        writer.write(id.toString() + " = 1 minecraft:emerald\n");
                    }
                }
            }

            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            }

            // Load Settings
            SEARCH_SAMPLE_SIZE = Integer.parseInt(props.getProperty("search.sampleSize", "40"));
            MAX_SEARCH_TIME_MS = Long.parseLong(props.getProperty("search.maxTimeMs", "1500"));

            WANDERING_TRADER_MAP_COUNT = Integer.parseInt(props.getProperty("trader.mapCount", "20"));
            WANDERING_TRADER_SEARCH_RADIUS = Integer.parseInt(props.getProperty("trader.searchRadius", "2500"));

            CARTOGRAPHER_L1_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level1.mapCount", "5"));
            CARTOGRAPHER_L1_SEARCH_RADIUS = Integer.parseInt(props.getProperty("cartographer.level1.searchRadius", "500"));
            CARTOGRAPHER_L1_MAX_USES = Integer.parseInt(props.getProperty("cartographer.level1.maxUses", "1"));

            CARTOGRAPHER_L2_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level2.mapCount", "5"));
            CARTOGRAPHER_L2_SEARCH_RADIUS = Integer.parseInt(props.getProperty("cartographer.level2.searchRadius", "750"));
            CARTOGRAPHER_L2_MAX_USES = Integer.parseInt(props.getProperty("cartographer.level2.maxUses", "1"));

            CARTOGRAPHER_L3_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level3.mapCount", "5"));
            CARTOGRAPHER_L3_SEARCH_RADIUS = Integer.parseInt(props.getProperty("cartographer.level3.searchRadius", "1000"));
            CARTOGRAPHER_L3_MAX_USES = Integer.parseInt(props.getProperty("cartographer.level3.maxUses", "1"));

            CARTOGRAPHER_L4_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level4.mapCount", "5"));
            CARTOGRAPHER_L4_SEARCH_RADIUS = Integer.parseInt(props.getProperty("cartographer.level4.searchRadius", "1500"));
            CARTOGRAPHER_L4_MAX_USES = Integer.parseInt(props.getProperty("cartographer.level4.maxUses", "1"));

            CARTOGRAPHER_L5_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level5.mapCount", "3"));
            CARTOGRAPHER_L5_SEARCH_RADIUS = Integer.parseInt(props.getProperty("cartographer.level5.searchRadius", "2500"));
            CARTOGRAPHER_L5_MAX_USES = Integer.parseInt(props.getProperty("cartographer.level5.maxUses", "1"));

            // Load Costs
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

                if (!key.contains(":")) continue;

                // FIX 1: Use Identifier.tryParse instead of new Identifier()
                Identifier structureId = Identifier.tryParse(key);

                if (structureId != null) {
                    // FIX 2: Use MapCost.fromString() instead of the broken parseCost method
                    loadedCosts.put(structureId, MapCost.fromString(value));
                    validIds.add(structureId);
                }
            }

            STRUCTURE_COSTS = Collections.synchronizedMap(loadedCosts);
            VALID_STRUCTURE_IDS = Collections.unmodifiableList(validIds);

            LOGGER.info("EM4ES Config Loaded: Structures={}, SampleSize={}, TimeLimit={}ms",
                    STRUCTURE_COSTS.size(), SEARCH_SAMPLE_SIZE, MAX_SEARCH_TIME_MS);

        } catch (Exception e) {
            LOGGER.error("FATAL: Failed to read, parse, or create config file!", e);
        }
    }

    // --- Helper Methods ---

    public static MapCost getCostForStructure(Identifier structureId) {
        return STRUCTURE_COSTS.getOrDefault(structureId, defaultCost);
    }

    public static ItemStack makeMapFromPos(net.minecraft.server.world.ServerWorld world, BlockPos pos, Identifier structureId) {
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

    // Removed the broken private parseCost method entirely.
    // We use MapCost.fromString() instead.
}