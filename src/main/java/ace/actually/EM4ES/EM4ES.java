package ace.actually.EM4ES;

import com.google.common.collect.ImmutableList;
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
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.gen.structure.Structure;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class EM4ES implements ModInitializer {

    public static final String MOD_ID = "em4es";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final Random RANDOM = new Random();

    // --- NEW CONFIGURABLE SETTINGS ---
    // These will hold the values loaded from our config file.
    public static int WANDERING_TRADER_MAP_COUNT = 3;
    public static int WANDERING_TRADER_SEARCH_RADIUS = 2500;
    public static int CARTOGRAPHER_L1_MAP_COUNT = 3;
    public static int CARTOGRAPHER_SEARCH_RADIUS = 1500;
    public static int FACTORY_MAX_ATTEMPTS = 3;

    private static volatile Map<Identifier, MapCost> STRUCTURE_COSTS = Collections.synchronizedMap(new HashMap<>());
    private static volatile List<Identifier> VALID_STRUCTURE_IDS = ImmutableList.of();
    private static MapCost defaultCost = MapCost.DEFAULT;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

        // This trade registration logic now uses the configurable values, but it's defined
        // when the game starts, before the config is loaded. The values will be updated
        // later in onServerStarted, and will be correct when trades are actually generated.
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 1, factories -> {
            for (int i = 0; i < CARTOGRAPHER_L1_MAP_COUNT; i++) {
                factories.add(new ExplorerMapTradeFactory(5, CARTOGRAPHER_SEARCH_RADIUS));
            }
        });
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 2, factories -> factories.add(new ExplorerMapTradeFactory(4, CARTOGRAPHER_SEARCH_RADIUS)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 3, factories -> factories.add(new ExplorerMapTradeFactory(3, CARTOGRAPHER_SEARCH_RADIUS)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 4, factories -> factories.add(new ExplorerMapTradeFactory(2, CARTOGRAPHER_SEARCH_RADIUS)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 5, factories -> factories.add(new ExplorerMapTradeFactory(1, CARTOGRAPHER_SEARCH_RADIUS)));
    }

    private void onServerStarted(MinecraftServer server) {
        File configFile = new File("./config/EM4ES/StructureCosts.properties");

        try {
            if (!configFile.exists()) {
                LOGGER.info("Structure cost config not found. Creating a default one...");
                configFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(configFile)) {
                    // --- WRITE NEW CONFIG KEYS TO FILE ---
                    writer.write("# This file configures the costs and behavior for explorer maps.\n\n");
                    writer.write("# --- General Settings ---\n");
                    writer.write("factory.maxAttempts = 3\n\n");
                    writer.write("# --- Wandering Trader Settings ---\n");
                    writer.write("trader.mapCount = 3\n");
                    writer.write("trader.searchRadius = 2500\n\n");
                    writer.write("# --- Cartographer Settings ---\n");
                    writer.write("cartographer.level1.mapCount = 3\n");
                    writer.write("cartographer.searchRadius = 1500\n\n");
                    writer.write("# --- Structure Costs ---\n");
                    writer.write("# The format is: structure_id = count item_id\n");
                    writer.write("default.cost = 10 minecraft:trial_key\n\n");

                    Registry<Structure> structureRegistry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
                    for (Identifier id : structureRegistry.getIds()) {
                        writer.write(id.toString() + " = 10 minecraft:trial_key\n");
                    }
                }
            }

            // --- LOAD ALL CONFIG VALUES ---
            Properties props = new Properties();
            try (var fis = new FileInputStream(configFile)) {
                props.load(fis);
            }

            // Load general settings with safe fallbacks
            WANDERING_TRADER_MAP_COUNT = Integer.parseInt(props.getProperty("trader.mapCount", "3"));
            WANDERING_TRADER_SEARCH_RADIUS = Integer.parseInt(props.getProperty("trader.searchRadius", "2500"));
            CARTOGRAPHER_L1_MAP_COUNT = Integer.parseInt(props.getProperty("cartographer.level1.mapCount", "3"));
            CARTOGRAPHER_SEARCH_RADIUS = Integer.parseInt(props.getProperty("cartographer.searchRadius", "1500"));
            FACTORY_MAX_ATTEMPTS = Integer.parseInt(props.getProperty("factory.maxAttempts", "3"));

            LOGGER.info("Loaded EM4ES settings: Trader Maps={}, Trader Radius={}, Cartographer L1 Maps={}, Cartographer Radius={}, Max Retries={}",
                    WANDERING_TRADER_MAP_COUNT, WANDERING_TRADER_SEARCH_RADIUS, CARTOGRAPHER_L1_MAP_COUNT, CARTOGRAPHER_SEARCH_RADIUS, FACTORY_MAX_ATTEMPTS);

            // Load structure costs (this part is unchanged)
            List<String> lines = Files.readAllLines(configFile.toPath());
            Map<Identifier, MapCost> loadedCosts = new HashMap<>();
            List<Identifier> validIds = new ArrayList<>();
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (key.equals("default.cost")) {
                    defaultCost = MapCost.fromString(value);
                    continue;
                }
                if (key.startsWith("trader.") || key.startsWith("cartographer.") || key.startsWith("factory.")) {
                    continue; // Skip general settings
                }
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

    // ... (The rest of your methods remain unchanged and correct)
    public static Identifier getRandomStructureId() {
        if (VALID_STRUCTURE_IDS.isEmpty()) return null;
        return VALID_STRUCTURE_IDS.get(RANDOM.nextInt(VALID_STRUCTURE_IDS.size()));
    }
    public static MapCost getCostForStructure(Identifier structureId) {
        return STRUCTURE_COSTS.getOrDefault(structureId, defaultCost);
    }
    public static ItemStack makeRandomMap(ServerWorld world, BlockPos from, Identifier structureId, int searchRadius) {
        TagKey<Structure> structureTag = TagKey.of(RegistryKeys.STRUCTURE, structureId);
        BlockPos pos = world.locateStructure(structureTag, from, searchRadius, false);
        if (pos != null) {
            ItemStack mapStack = FilledMapItem.createMap(world, pos.getX(), pos.getZ(), (byte) 2, true, true);
            FilledMapItem.fillExplorationMap(world, mapStack);
            addDecorationsAndColor(mapStack, pos, structureTag.id().toString(), structureTag.hashCode());
            mapStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(formatName(structureTag.id().getPath()) + " Map"));
            return mapStack;
        }
        return ItemStack.EMPTY;
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