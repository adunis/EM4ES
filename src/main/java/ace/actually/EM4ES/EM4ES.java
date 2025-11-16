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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EM4ES implements ModInitializer {

    public static final String MOD_ID = "em4es";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final Random RANDOM = new Random();

    private static volatile Map<Identifier, MapCost> STRUCTURE_COSTS = Collections.synchronizedMap(new HashMap<>());
    private static volatile List<Identifier> VALID_STRUCTURE_IDS = ImmutableList.of();
    private static MapCost defaultCost = MapCost.DEFAULT;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

        final int searchRadius = 1000;

        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 1, factories -> {
            factories.add(new ExplorerMapTradeFactory(5, searchRadius));
            factories.add(new ExplorerMapTradeFactory(5, searchRadius));
            factories.add(new ExplorerMapTradeFactory(5, searchRadius));
        });
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 2, factories -> factories.add(new ExplorerMapTradeFactory(4, searchRadius)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 3, factories -> factories.add(new ExplorerMapTradeFactory(3, searchRadius)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 4, factories -> factories.add(new ExplorerMapTradeFactory(2, searchRadius)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 5, factories -> factories.add(new ExplorerMapTradeFactory(1, searchRadius)));
    }

    /**
     * This is the new, robust config loader. It reads the file line-by-line to prevent any parsing errors.
     */
    private void onServerStarted(MinecraftServer server) {
        File configFile = new File("./config/EM4ES/StructureCosts.properties");

        try {
            if (!configFile.exists()) {
                LOGGER.info("Structure cost config not found. Creating a default one...");
                configFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("# This file configures the costs for explorer maps.\n");
                    writer.write("# The format is: structure_id = count item_id\n");
                    writer.write("# To disable a map, simply delete its line from this file.\n\n");
                    writer.write("default.cost = 1 minecraft:trial_key\n\n");

                    Registry<Structure> structureRegistry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
                    for (Identifier id : structureRegistry.getIds()) {
                        writer.write(id.toString() + " = 1 minecraft:trial_key\n");
                    }
                }
            }

            LOGGER.info("Loading structure costs from {}...", configFile.getAbsolutePath());
            List<String> lines = Files.readAllLines(configFile.toPath());

            Map<Identifier, MapCost> loadedCosts = new HashMap<>();
            List<Identifier> validIds = new ArrayList<>();

            for (String line : lines) {
                // Ignore comments and empty lines
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    LOGGER.warn("Skipping malformed line in config: '{}'", line);
                    continue;
                }

                String key = parts[0].trim();
                String value = parts[1].trim();

                if (key.equals("default.cost")) {
                    defaultCost = MapCost.fromString(value);
                    LOGGER.info("Default map cost set to: {} {}", defaultCost.count(), defaultCost.item());
                    continue;
                }

                Identifier structureId = Identifier.tryParse(key);
                if (structureId != null) {
                    MapCost cost = MapCost.fromString(value);
                    loadedCosts.put(structureId, cost);
                    validIds.add(structureId);
                    LOGGER.debug("Loaded cost for '{}': {} {}", key, cost.count(), cost.item());
                } else {
                    LOGGER.warn("Skipping invalid structure ID key in config: '{}'", key);
                }
            }

            STRUCTURE_COSTS = Collections.synchronizedMap(loadedCosts);
            VALID_STRUCTURE_IDS = Collections.unmodifiableList(validIds);
            LOGGER.info("Successfully loaded costs for {} structures.", STRUCTURE_COSTS.size());

        } catch (IOException e) {
            LOGGER.error("FATAL: Failed to read or create structure cost config file!", e);
        }
    }

    public static Identifier getRandomStructureId() {
        if (VALID_STRUCTURE_IDS.isEmpty()) {
            return null;
        }
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