package ace.actually.EM4ES;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnidentifiedMapHandler {

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            // 1. Server check
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

            ItemStack stack = player.getStackInHand(hand);

            // 2. Check Item Type
            if (!stack.isOf(Items.PAPER)) {
                return TypedActionResult.pass(stack);
            }

            EM4ES.LOGGER.info("Right-clicked Paper. Checking for data...");

            // 3. Check Data Component (1.21 Logic)
            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);

            if (customData == null) {
                EM4ES.LOGGER.info("FAIL: Paper has no custom_data component.");
                return TypedActionResult.pass(stack);
            }

            if (!customData.contains("EM4ES_Unidentified")) {
                EM4ES.LOGGER.info("FAIL: Paper has custom_data, but missing 'EM4ES_Unidentified' tag. Tags found: " + customData.toString());
                return TypedActionResult.pass(stack);
            }

            EM4ES.LOGGER.info("SUCCESS: Valid map found! Starting search...");

            // 4. Consume Item
            stack.decrement(1);

            // 5. Give Placeholder
            ItemStack placeholder = new ItemStack(Items.FILLED_MAP);
            placeholder.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Deciphering Map...").formatted(Formatting.YELLOW));
            if (!player.getInventory().insertStack(placeholder)) {
                player.dropItem(placeholder, false);
            }

            // 6. Start Search
            ServerWorld serverWorld = (ServerWorld) world;
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

            EM4ES.MAP_SEARCH_EXECUTOR.submit(() -> {
                List<Identifier> candidates = new ArrayList<>(EM4ES.VALID_STRUCTURE_IDS);
                if (candidates.isEmpty()) return;
                Collections.shuffle(candidates);
                Identifier targetId = candidates.get(0);

                // Search wide radius (3000 blocks)
                StructureSearchResult result = ExplorerMapTradeFactory.findStructure(
                        serverWorld,
                        player.getBlockPos(),
                        Collections.emptySet(),
                        3000 / 16
                );

                serverWorld.getServer().execute(() -> {
                    ItemStack finalMap;
                    if (result != null) {
                        finalMap = EM4ES.makeMapFromPos(serverWorld, result.pos(), result.id());
                        serverPlayer.sendMessage(Text.literal("You found a map to " + EM4ES.formatName(result.id().getPath()) + "!").formatted(Formatting.GREEN), true);
                    } else {
                        finalMap = new ItemStack(Items.PAPER);
                        finalMap.set(DataComponentTypes.CUSTOM_NAME, Text.literal("The map crumbled to dust... (No structure found)").formatted(Formatting.GRAY));
                    }

                    if (!serverPlayer.getInventory().insertStack(finalMap)) {
                        serverPlayer.dropItem(finalMap, false);
                    }

                    // Cleanup placeholder
                    for (int i = 0; i < serverPlayer.getInventory().size(); i++) {
                        ItemStack s = serverPlayer.getInventory().getStack(i);
                        if (s.isOf(Items.FILLED_MAP) && s.getName().getString().contains("Deciphering")) {
                            serverPlayer.getInventory().removeStack(i, 1);
                            break;
                        }
                    }
                });
            });

            return TypedActionResult.success(stack);
        });
    }
}