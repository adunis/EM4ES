package ace.actually.EM4ES;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * A simple record to hold the cost of a map trade.
 * It contains the item to be used as currency and the amount required.
 */
public record MapCost(Item item, int count) {

    // Hard fallback default: 12 Emeralds.
    // This is only used if the config string is completely corrupted or missing.
    public static final MapCost DEFAULT = new MapCost(Items.EMERALD, 12);

    /**
     * Parses a cost string from the config file (e.g., "10 minecraft:emerald").
     * Handles extra spaces and validates the item registry.
     */
    public static MapCost fromString(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        try {
            // Split by one or more spaces (handles "10   minecraft:emerald" correctly)
            String[] parts = value.trim().split("\\s+");

            if (parts.length < 2) {
                EM4ES.LOGGER.warn("Invalid cost format '{}'. Expected 'count item_id'. Using default.", value);
                return DEFAULT;
            }

            // 1. Parse Count
            int count = Integer.parseInt(parts[0]);

            // 2. Parse Item ID
            Identifier itemId = Identifier.tryParse(parts[1]);
            if (itemId == null) {
                EM4ES.LOGGER.warn("Invalid item ID format '{}'. Using default.", parts[1]);
                return DEFAULT;
            }

            // 3. Get Item from Registry
            Item item = Registries.ITEM.get(itemId);

            // Registry returns Items.AIR if the ID doesn't exist (e.g., typo or missing mod)
            if (item == Items.AIR) {
                EM4ES.LOGGER.warn("Item '{}' not found in registry. using default.", itemId);
                return DEFAULT;
            }

            return new MapCost(item, count);

        } catch (NumberFormatException e) {
            EM4ES.LOGGER.error("Failed to parse number in cost string '{}'. Using default.", value);
            return DEFAULT;
        } catch (Exception e) {
            EM4ES.LOGGER.error("Unexpected error parsing cost '{}'. Using default.", value, e);
            return DEFAULT;
        }
    }
}