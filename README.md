# EM4ES: Explorer Maps for Every Structure

![CurseForge](https://img.shields.io/badge/CurseForge-Coming%20Soon-orange) ![Modrinth](https://img.shields.io/badge/Modrinth-Coming%20Soon-green) ![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-blue) ![Requires](https://img.shields.io/badge/Requires-Fabric%20API-red)

Tired of explorer maps only leading to the same two structures? Have you ever wished you could get a map to a Bastion, an Ancient City, or even structures from your favorite mods? **EM4ES** is the solution!

This lightweight, server-side mod expands the exploration experience by introducing new trades for maps that can lead to **any structure in the game**. It stands for **E**xplorer **M**aps **4** **E**very **S**tructure.

## Features

*   **Dynamic Explorer Maps:** Adds new explorer maps that can point to any structure defined in a powerful config file, including those from other mods!
*   **Lag-Free Async Generation:** Map searches run on a background thread. The server will **never freeze** while locating structures.
    *   When a map is generated or restocked, you will see a "Searching..." placeholder trade.
    *   Once the background calculation finishes, it seamlessly updates to the real map without stopping the server tick loop.
*   **New Villager & Trader Stock:** Integrates these maps seamlessly into vanilla gameplay through two sources:
    *   **Cartographer Villagers:** Cartographers now have a progressive trade system for these special maps.
    *   **Wandering Traders:** The world's traveler now sells a variety of maps to distant and exciting locations.
*   **Progressive Unlocks:**
    *   A brand new **Novice (Level 1) Cartographer** will offer **3** unique structure maps (configurable).
    *   As you level up a Cartographer, it will unlock additional map trades at each new professional level (Apprentice, Journeyman, Expert, and Master).
    *   The **Wandering Trader** will spawn with a stock of **5** random structure maps (configurable).
*   **Fully Configurable Economy:** The cost for every single map is completely customizable! By default, all maps cost **1 Emerald**, but you can change this to any item and any amount you want.
*   **Controlled Search Radius:** To ensure balanced gameplay, search radii are configurable per trader type and villager level (e.g., Novice searches 500 blocks, Master searches 2500 blocks).
*   **🔄 Infinite Restocking System:** Never run out of adventures! **EM4ES** includes a restocking system that allows you to farm maps without lag. Instead of the trade simply locking (Red X), it will briefly turn into a **"Restocking..."** icon (represented by a Clock).

## How It Works

1.  **Find a Trader:** Locate a Wandering Trader or a Cartographer Villager.
2.  **Check Their Trades:** They will now offer Explorer Maps with names like "Pillager Outpost Map" or "Ancient City Map".
    *   *Note:* If you see a "Searching..." or "Restocking..." placeholder, wait a second! The server is finding the best location for you in the background.
3.  **Pay the Price:** Purchase the map for the price defined in the config (by default, 10 Trial Keys).
4.  **Explore!** Follow the map just like you would a vanilla treasure map to find your destination.

This system provides a renewable and engaging way for players to find and explore all the content their world has to offer.

## Advanced Configuration

**EM4ES** features a powerful configuration system that gives you complete control over the mod's economy. On the first server startup, it will generate a properties file:

**File Location:** `config/EM4ES/StructureCosts.properties`

This file allows you to set a custom price for every single structure map.

### How to Edit Costs

The format is simple: `structure_id = count item_id`

When the file is first created, it will look like this:

```properties
# This file configures the costs for explorer maps.
# The format is: structure_id = count item_id
# Example: minecraft:ancient_city = 1 minecraft:echo_shard

default.cost = 1 minecraft:trial_key

minecraft:ancient_city = 10 minecraft:trial_key
minecraft:bastion_remnant = 10 minecraft:trial_key
minecraft:village_plains = 10 minecraft:trial_key
# ... and so on for every structure from every mod
```

You can edit any line to change the cost for that specific structure's map.

#### **Examples:**

*   **Make Mansion maps rare and expensive:**
    Change the line to: `minecraft:mansion = 50 minecraft:trial_key`

*   **Make Village maps cheap and use a different currency:**
    Change the line to: `minecraft:village_plains = 10 minecraft:emerald`

*   **Make Ancient City maps a true endgame prize:**
    Change the line to: `minecraft:ancient_city = 1 minecraft:nether_star`

*   **Change the default price for all un-edited maps:**
    Modify the special `default.cost` key at the top of the file:
    `default.cost = 2 minecraft:diamond`

### Enabling/Disabling Maps

To prevent a map for a certain structure from ever being sold, simply **delete the line** for that structure from the `StructureCosts.properties` file.

This allows server owners and modpack makers to tailor the exploration and economy perfectly for their players.

## Requirements

*   **Minecraft:** 1.21.1
*   **Mod Loader:** Fabric
*   **API:** [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) (Required)

## Installation

1.  Ensure you have the Fabric Loader and Fabric API installed.
2.  Download the latest version of **EM4ES** from the releases page.
3.  Place the downloaded `.jar` file into your `mods` folder.
4.  Launch the game or server once to generate the config file, then customize it to your liking.

## Example Configuration (Cobbleverse Server)

Here is an example of a highly customized config used in a modpack with Cobblemon, Dungeons Arise, and many structure mods.

```properties
# This file configures the costs and behavior for explorer maps.

# --- General Settings ---
search.sampleSize = 40
search.maxTimeMs = 1500

# --- Wandering Trader Settings ---
trader.mapCount = 5
trader.searchRadius = 5000

# --- Cartographer Settings ---
cartographer.level1.mapCount = 2
cartographer.level1.searchRadius = 500
# ... (Levels 2-5 Configurable)

# --- Structure Costs ---
# The format is: structure_id = count item_id

default.cost = 1 minecraft:trial_key

# ---------------------------------------------------------------------------------
# --- Legendary & Mythical Locations (Absurd Endgame Multiplayer Cost) ---
# ---------------------------------------------------------------------------------
cobbleverse:legendary/groudon = 64 minecraft:netherite_block
cobbleverse:legendary/kyogre = 64 minecraft:conduit
cobbleverse:sky_pillar = 64 minecraft:nether_star
```
