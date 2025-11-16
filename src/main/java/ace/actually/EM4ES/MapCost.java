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

    // A default cost of 10 Trial Keys, used if a structure is not specifically configured.
    public static final MapCost DEFAULT = new MapCost(Items.TRIAL_KEY, 10);

    /**
     * Parses a cost string from the config file (e.g., "10 minecraft:emerald").
     * If the string is invalid, it returns the default cost.
     */
    public static MapCost fromString(String value) {
        // Safety check for null or empty values from the config
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT;
        }

        try {
            String[] parts = value.trim().split(" ");
            if (parts.length != 2) {
                EM4ES.LOGGER.warn("Invalid cost format '{}'. Expected 'count item_id'. Using default.", value);
                return DEFAULT;
            }

            int count = Integer.parseInt(parts[0]);
            Identifier itemId = Identifier.tryParse(parts[1]);

            if (itemId == null) {
                EM4ES.LOGGER.warn("Invalid item ID format '{}' in cost config. Using default.", parts[1]);
                return DEFAULT;
            }

            // This line will now work because the import is correct.
            Item item = Registries.ITEM.get(itemId);

            if (item == Items.AIR) {
                EM4ES.LOGGER.warn("Unknown item ID '{}' in cost config. Using default.", parts[1]);
                return DEFAULT;
            }

            return new MapCost(item, count);
        } catch (NumberFormatException e) {
            EM4ES.LOGGER.error("Failed to parse count from cost string '{}'. Using default.", value, e);
            return DEFAULT;
        } catch (Exception e) {
            EM4ES.LOGGER.error("An unexpected error occurred while parsing cost string '{}'. Using default.", value, e);
            return DEFAULT;
        }
    }
}