# CarpetUtility 1.1.0

Enhanced version of CarpetUtility with:

## New features

### `/namegen`
- Generates a random name suitable for a Carpet fake player / bot
- **Only usable by players named `Fins` or `SR888V`**
- `/namegen` → prints a name + the spawn command
- `/namegen spawn` → generates a name and immediately runs `/player <name> spawn`

### Improved pathfinding on `/carpetutility <bot> walkto ...`
- **Mine through walls**: if the bot cannot go around a solid obstacle (or has steered too many times), it will switch to a pickaxe (if available) and dig the blocking blocks
- **Pillar up**: if the target player/position is significantly above the bot and the bot is close horizontally, it will place blocks under itself and jump to climb up

### Original features kept
- `/carpetutility <bot> walkto <player|pos>`
- `/carpetutility stop <bot>`
- `/carpetutility status`
- Bridging over holes
- Cobweb scoreboard tracker (`on_cobweb` objective)

## Building

This is the source for a Fabric mod targeting Minecraft 1.21.11 + Carpet.

1. Create a standard Fabric Loom project for 1.21.11
2. Put these sources under `src/main/java`
3. Put `src/main/resources` content (fabric.mod.json, mixins, assets)
4. Add dependency on Carpet (`carpet` mod)
5. `./gradlew build`

The original JAR used intermediary mappings. These sources use readable Yarn-style names so they are easy to edit. Adjust method names if your mappings differ slightly for 1.21.11.

## Commands summary

```
/namegen                  - generate a bot name (Fins / SR888V only)
/namegen spawn            - generate + spawn the bot

/carpetutility status
/carpetutility stop <bot>
/carpetutility <bot> walkto <player>
/carpetutility <bot> walkto <x> <y> <z>
```

Give the bot blocks (for bridging & pillaring) and a pickaxe (for mining walls).
