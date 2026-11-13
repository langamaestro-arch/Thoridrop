# Thoridrop V1

Minecraft Java mod for NeoForge. Target Minecraft version and compatible NeoForge build must be verified before implementation.

## Gameplay
- Timed supply drops, visible to every online player via a dedicated directional HUD above XP.
- Interacting with the supply crate starts five waves of hostile mobs.
- Only players who contribute to combat qualify for loot.
- Generate loot once per event and offer identical independent copies to every eligible player.
- Track claimed rewards per player UUID; persist event state and claims across restarts.
- Participants who die during active waves respawn at a safe location near the crate, retaining all items, armor, offhand and experience, with five seconds of protection.
- Do not change the global keepInventory gamerule or the player's permanent respawn point.

## Initial configurable defaults
- Drop interval: 60 minutes of active server time.
- Maximum spawn distance: 300 blocks.
- Waves: 5.
- Rarity: common 50%, rare 35%, legendary 15%.
- Reward claim window: 15 minutes.
- Suggested participation threshold: 5% of total event damage.

## Planned operator commands
`/thoridrop start`, `/thoridrop start legendary`, `/thoridrop stop`, `/thoridrop status`, `/thoridrop reload`.

## Release criteria
Verify compatible NeoForge APIs, successful client/server startup, synchronized HUD, wave progression, safe respawn, no inventory/XP loss or duplication, identical independent loot, and a successful GitHub Actions JAR build.

This document specifies requirements; it does not claim that features have been implemented.
