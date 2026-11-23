# CarpetUtility

A dedicated Fabric mod for **Minecraft 1.21.11** that extends [Fabric Carpet](https://github.com/gnembon/fabric-carpet) with advanced fake player navigation, automated bridging over holes, obstacle avoidance, and continuous cobweb scoreboard tracking.

Works seamlessly on dedicated servers and clients, with native support for Carpet fake players (`EntityPlayerMPFake`) and optional support for Carpet+ / CarpetMod-Plus.

---

## Features

### 1. In-Game Commands (Carpet Style)
- `/carpetutility <botname> walkto <player>` — Orders a Carpet fake player to pathfind toward a target player in real time.
- `/carpetutility <botname> walkto <x> <y> <z>` — Orders the bot to pathfind to specific coordinates.
- `/carpetutility stop <botname>` — Halts bot walking and resets its Carpet action pack.
- `/carpetutility status` — Displays real-time status of all active walking bots (target, distance, bridging state).

### 2. Autonomous Bot Walking & Bridging
- **Direct Carpet ActionPack Integration**: Controls movement, look angle, sprinting, jumping, and sneaking via Carpet's native `EntityPlayerActionPack` API with command-level fallbacks.
- **Hole Bridging**: When the bot approaches a void or hole, it automatically crouches, selects a building block from its inventory, and places it beneath its next step.
- **Wall & Obstacle Avoidance**: Detects walls ahead; automatically jumps single-block elevations or intelligently steers around taller walls rather than running into them.
- **Configurable Bridging Blocks**: Supports standard building blocks (dirt, cobblestone, stone, planks, deepslate, netherrack, etc.).

### 3. Cobweb Scoreboard Tracker
- Automatically creates and maintains the `on_cobweb` scoreboard objective on the sidebar.
- Continuously checks every player (real players and Carpet bots) each tick:
  - Score is set to **`1`** if standing inside or on a cobweb (`minecraft:cobweb`).
  - Score is set to **`0`** otherwise.

---

## Installation

1. Install **Fabric Loader** (>= 0.18.2) for **Minecraft 1.21.11**.
2. Install **Fabric Carpet** (1.4.194+ for 1.21.11) into your `mods/` directory.
3. (Optional) Install **Carpet+** / **CarpetMod-Plus** for extra Carpet features.
4. Place `carpetutility-1.0.0.jar` into your `mods/` folder.
5. Start your Minecraft client or dedicated server.

---

## Quick Start & Usage

```mcfunction
# 1. Spawn a Carpet fake player
/player Bot1 spawn

# 2. Give the bot some building blocks for bridging (e.g. cobblestone or dirt)
/give Bot1 cobblestone 64

# 3. Order the bot to follow you
/carpetutility Bot1 walkto @s

# 4. Or order the bot to walk to specific coordinates
/carpetutility Bot1 walkto 150 70 -200

# 5. Check active bot status
/carpetutility status

# 6. Stop the bot anytime
/carpetutility stop Bot1
```

---

## Building from Source

To compile the JAR file locally using the included Gradle wrapper:

```bash
# Clone the repository
git clone https://github.com/Mhdajt/carpetutility.git
cd carpetutility

# Build with Gradle (requires Java 21)
./gradlew build
```

The compiled mod JAR will be generated at:
```
build/libs/carpetutility-1.0.0.jar
```

Alternatively, whenever changes are pushed to `main`, GitHub Actions automatically builds the mod JAR and attaches it as an artifact and GitHub Release.

---

## License

This project is licensed under the [MIT License](LICENSE).
