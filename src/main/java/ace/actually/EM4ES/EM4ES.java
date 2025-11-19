package ace.actually.EM4ES;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapColorComponent;
import net.minecraft.component.type.MapDecorationsComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCustomDataLootFunction;
import net.minecraft.loot.function.SetNameLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

    // Add this to your settings variables
    public static float LOOT_CHANCE = 0.05f;

    @Override
    public void onInitialize() {

        loadConfig();

        UnidentifiedMapHandler.register();

        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            // Check if it's a chest loot table (not mob drops, fishing, etc.)
            if (source.isBuiltin() && key.getValue().getPath().startsWith("chests/")) {

                // Create the NBT tag to identify our special paper
                NbtCompound nbt = new NbtCompound();
                nbt.putBoolean("EM4ES_Unidentified", true);

                // Build the pool
                LootPool.Builder pool = LootPool.builder()
                        .with(ItemEntry.builder(Items.PAPER)
                                // Set the chance (config value)
                                .conditionally(net.minecraft.loot.condition.RandomChanceLootCondition.builder(LOOT_CHANCE))
                                // Give it a cool name
                                .apply(SetNameLootFunction.builder(Text.literal("Unidentified Structure Map").formatted(Formatting.GOLD), SetNameLootFunction.Target.CUSTOM_NAME))
                                // Apply the NBT so we know it's ours
                                .apply(SetCustomDataLootFunction.builder(nbt))
                        )
                        .rolls(ConstantLootNumberProvider.create(1));

                tableBuilder.pool(pool);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
    }

    /**
     * Generates the config file if missing and loads Structure Costs (which require Registry access).
     */
    private void onServerStarted(MinecraftServer server) {
        File configFile = new File("./config/EM4ES/EM4ES.properties");
        try {
            if (!configFile.exists()) {
                LOGGER.info("EM4ES Config not found. Creating default...");
                configFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("# EM4ES Configuration\n\n");
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

                    writer.write("# --- Loot Table Settings ---\n");
                    writer.write("# Chance (0.0 to 1.0) to find an Unidentified Map in chests.\n");
                    writer.write("loot.chance = 0.05\n\n");

                    writer.write("# --- Structure Costs ---\n");
                    writer.write("default.cost = 1 minecraft:emerald\n\n");

                    Registry<Structure> structureRegistry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
                    for (Identifier id : structureRegistry.getIds()) {
                        writer.write(id.toString() + " = 1 minecraft:emerald\n");
                    }
                }
            }

            // Load Costs (We re-read the file to get the costs + any user changes)
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

                // We ignore settings keys (like trader.mapCount) here, we only want structure IDs
                if (!key.contains(":")) continue;

                Identifier structureId = Identifier.tryParse(key);
                if (structureId != null) {
                    loadedCosts.put(structureId, MapCost.fromString(value));
                    validIds.add(structureId);
                }
            }

            STRUCTURE_COSTS = Collections.synchronizedMap(loadedCosts);
            VALID_STRUCTURE_IDS = Collections.unmodifiableList(validIds);

            LOGGER.info("EM4ES Costs Loaded. Structures found: " + VALID_STRUCTURE_IDS.size());

        } catch (Exception e) {
            LOGGER.error("FATAL: Failed to handle config file!", e);
        }
    }

    private void loadConfig() {
        File configFile = new File("./config/EM4ES/EM4ES.properties");
        if (!configFile.exists()) {
            // Config doesn't exist yet, use defaults.
            // The file will be created later in onServerStarted.
            return;
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(fis);

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

            LOOT_CHANCE = Float.parseFloat(props.getProperty("loot.chance", "0.05"));

            LOGGER.info("EM4ES Config loaded early (Loot Chance: " + LOOT_CHANCE + ")");

        } catch (Exception e) {
            LOGGER.error("Failed to load config early", e);
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