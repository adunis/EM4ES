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
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class EM4ES implements ModInitializer {

    public static final String MOD_ID = "em4es";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final Random RANDOM = new Random();
    private static volatile List<Identifier> VALID_STRUCTURE_IDS = ImmutableList.of();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);

        // --- FINAL, CORRECT TRADE REGISTRATION ---

        final int searchRadius = 1000;

        // 1. Add 3 initial trades for a brand new (Level 1) Cartographer.
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 1,
                factories -> {
                    factories.add(new ExplorerMapTradeFactory(5, searchRadius));
                    factories.add(new ExplorerMapTradeFactory(5, searchRadius));
                    factories.add(new ExplorerMapTradeFactory(5, searchRadius));
                });

        // 2. Add 1 new trade each time the Cartographer levels up.
        //    The key is that we re-register for each level. The villager will gain
        //    access to these new pools as it levels up.
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 2,
                factories -> factories.add(new ExplorerMapTradeFactory(4, searchRadius)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 3,
                factories -> factories.add(new ExplorerMapTradeFactory(3, searchRadius)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 4,
                factories -> factories.add(new ExplorerMapTradeFactory(2, searchRadius)));
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.CARTOGRAPHER, 5,
                factories -> factories.add(new ExplorerMapTradeFactory(1, searchRadius)));
    }

    private void onServerStarted(MinecraftServer server) {
        // ... (This method is correct and does not need to be changed)
        File configFile = new File("./config/EM4ES/StructureWhitelist.cfg");
        try {
            if (!configFile.exists()) {
                LOGGER.info("Structure whitelist not found, creating one...");
                Registry<Structure> structureRegistry = server.getRegistryManager().get(RegistryKeys.STRUCTURE);
                List<String> structureIds = structureRegistry.getIds().stream()
                        .map(Identifier::toString)
                        .collect(Collectors.toList());
                FileUtils.writeLines(configFile, structureIds);
            }
            VALID_STRUCTURE_IDS = FileUtils.readLines(configFile, StandardCharsets.UTF_8).stream()
                    .map(Identifier::tryParse)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            LOGGER.info("Loaded {} valid structure identifiers.", VALID_STRUCTURE_IDS.size());
        } catch (IOException e) {
            LOGGER.error("Failed to read or create structure whitelist config.", e);
        }
    }

    // ... (The rest of this file is correct and does not need to be changed)
    public static ItemStack makeRandomMap(ServerWorld world, BlockPos from, int searchRadius) {
        if (VALID_STRUCTURE_IDS.isEmpty()) {
            LOGGER.error("Structure whitelist is empty. Cannot create map.");
            return ItemStack.EMPTY;
        }
        final int MAX_ATTEMPTS = 20;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            Identifier randomId = VALID_STRUCTURE_IDS.get(RANDOM.nextInt(VALID_STRUCTURE_IDS.size()));
            TagKey<Structure> structureTag = TagKey.of(RegistryKeys.STRUCTURE, randomId);
            BlockPos pos = world.locateStructure(structureTag, from, searchRadius, false);
            if (pos != null) {
                ItemStack mapStack = FilledMapItem.createMap(world, pos.getX(), pos.getZ(), (byte) 2, true, true);
                FilledMapItem.fillExplorationMap(world, mapStack);
                addDecorationsAndColor(mapStack, pos, structureTag.id().toString(), structureTag.hashCode());
                mapStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(formatName(structureTag.id().getPath()) + " Map"));
                return mapStack;
            }
        }
        LOGGER.error("Failed to find any valid structure within a {} block radius after {} attempts.", searchRadius, MAX_ATTEMPTS);
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
        name = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        return WordUtils.capitalizeFully(name.replace('_', ' '));
    }
}