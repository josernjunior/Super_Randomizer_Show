# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Super Randomizer Show**: a randomizer mod for the game **Necesse** (Steam app 1169040). Mod ID `josezito.srs`, target game version 1.3.3, Java package `srs`. It adds no content of its own. Everything it does is patch vanilla behavior, deterministically from a seed. The user-facing description (in Portuguese) is in `README.md`. The original ExampleMod template code lives in `template-backup/`, outside the source tree, and is not compiled. Modding docs: https://necessewiki.com/Modding

## Build & run

You need a local Necesse install. `build.gradle` auto-detects it (on Windows: `C:/Program Files (x86)/Steam/steamapps/common/Necesse`) and stops at configuration time if `Necesse.jar` isn't there. If detection fails, set `gameDirectory` in `build.gradle`.

Use the Gradle wrapper (`./gradlew` in bash, `.\gradlew.bat` in PowerShell):

- `buildModJar`: compiles, generates `mod.info`, and writes `build/jar/<ModName>-<gameVersion>-<modVersion>.jar`. It deletes any other jars in `build/jar/`.
- `runClient` / `runDevClient`: builds, then starts the game through Steam (`StartSteamClient`) with the mod. The client can't start without Steam.
- `runServer`: builds, then starts the desktop server GUI with the mod.

There is no unit test suite and no linter.

### Testing without a GUI

The headless dedicated server exercises everything except textures and sounds. Pass `-datadir` so the cfg, world and logs go to a throwaway folder instead of the user's real `%APPDATA%\Necesse`. Run it from the game directory, with stdin feeding console commands:

```bash
G="/c/Program Files (x86)/Steam/steamapps/common/Necesse"
cd "$G" && (sleep 25; echo "srs test"; sleep 40; echo "stop") | "$G/jre/bin/java" -jar Server.jar -nogui \
  -datadir "C:\\some\\scratch\\dir" -mod "C:\\Dev\\SRandomizerShow\\build\\jar" -world srs_test
```

- The `srs test` console command (`srs/commands/SelfTest.java`) runs the randomized paths on the spawn level and reports any exception. It covers spawn-table remaps, killing every pooled mob (loot swaps), object drop pairs that would lose or double their drops, firing every swapped projectile and every replacement type, equipping every armor piece, applying every trinket/set buff, building every armor tooltip, and sampling translations.
- The spoiler file at `<datadir>/cfg/mods/josezito.srs-spoiler.txt` shows every decision. Two boots with the same seed must produce identical spoilers, apart from the "Gerado em" line.
- Texture and sound swaps are only built on clients. To check them offline, call `ResourceEncoder.loadResourceFile()` from a small harness with `Necesse.jar`, `lib/*` and `build/mod` on the classpath. Then call `SRS.freeze()`, `VisualRandomizer.build()` and `SoundRandomizer.build()`.

### Build constraints

- The mod source compiles at **Java 8 language level** (`sourceCompatibility = 1.8`), even though Gradle runs on JDK 17 and the game ships a Java 17 JRE. Don't use `var`, switch expressions, records, text blocks, `List.of`, etc. in `src/`.
- Mod metadata lives in the `project.ext` block at the top of `build.gradle`. It's turned into `build/mod/mod.info` on every compile, so never edit `mod.info` by hand. `modVersion` must be dot-separated: a comma (`0,7`) breaks `mod.info`, and the game then reads the version as `0`.
- `clientside = false`: server and clients both need the mod, and they need the same settings (see "Multiplayer" below).

### Reading game code

Necesse ships without sources. To decompile the whole jar, use IntelliJ's Fernflower. It needs IntelliJ's bundled JBR (Java 25), not the system JDK 17:

```bash
IJ="/c/Program Files/JetBrains/IntelliJ IDEA 2026.2.3"
"$IJ/jbr/bin/java" -cp "$IJ/plugins/java-decompiler/lib/java-decompiler.jar" \
  org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler -dgs=1 <Necesse.jar> <outdir>
```

This produces a jar of `.java` files; unzip it. Decompile into a scratch directory, never into the repo. For a quick signature check, `javap -p -cp <Necesse.jar> <class>` is enough.

## Architecture

**Boot order** (see the Javadoc on `srs/SuperRandomizerShow.java`). Necesse calls these in order:
1. `initSettings`
2. Necesse applies the cfg file.
3. `preInit`: guarantees a seed, freezes the settings, shuffles translations.
4. `init`: registers the packet and the `/srs` command. On clients it also builds the texture and sound swaps, because textures and sounds load between `init` and `initResources`.
5. `postInit`: builds mob pools, spawns, drops, shops, weapons, equipment, projectiles, quest rewards and fishing.
6. The game builds its recipe list (`RecipesGetDefaultRecipesPatch`).
7. `Recipes.closeModRecipeRegistry` runs, and its patch writes the spoiler.

**Settings: active vs stored.** `SRS.stored()` is the `ModSettings` object Necesse loads and saves. `/srs` edits it and writes the cfg file. `SRS.settings()` is a snapshot frozen in `preInit`, and every randomizer reads only that snapshot. Changes therefore apply after a restart, and Necesse re-saving cfg files mid-session can't revert pending changes.

**Determinism.** All randomness goes through `srs/core/SeedRandom`. It is keyed by `seed` plus a string (feature name plus *sorted string IDs*), never by numeric registry IDs or iteration order. Keep it that way, or the same seed will stop reproducing across machines and mod lists.

**One patch class per hook** in `srs/patches/`. Each one is a thin `@ModMethodPatch`/`@ModConstructorPatch` Byte Buddy advice that delegates to a static method in `srs/random/*`. Mod jars are appended to the system class loader, so inlined advice can call public mod classes directly. Advice tricks used here:
- `@Advice.Argument(readOnly = false)` to replace an argument
- `skipOn = OnNonDefaultValue` plus `@Advice.Return(readOnly = false)` to replace a result
- `@Advice.Thrown(readOnly = false)` to swallow an exception

**Where each feature hooks in:**

| # | Feature | Class | Hook |
|---|---|---|---|
| 1 | Mob spawns | `MobSpawnRandomizer` | `MobRegistry.getMob(String, Level)`. Remaps the ID only on the server, and only when a stack walk finds a whitelisted caller (see below). |
| 2 | Drops | `DropRandomizer` | A dying mob is marked in `Mob.onDeath`. The first `LootTable.getNewList` call for that mob returns the swapped mob's table. Objects and quantities use the official `ObjectLootTableDropsEvent` / `Mob*LootTableDropsEvent` listeners. Object groups (`objectGroup`) must only hold objects that drop something on their own: wild flowers/mushrooms, fruit bushes and self-dropping grass (`dropsOwnItem`) are separate groups. The `...r` second tiles of 2-tile rocks/clusters (`*RObject`), decorative rocks with no drops (`isDecorativeRock`), plain grass and cobwebs stay out, or broken objects lose their drops or drop twice. The self test checks every swapped pair for this. The random bonus item (`randomItemDrop`) is added in the `MobLootTableDropsEvent` listener, using real (non-seeded) randomness like normal loot. |
| 3 | Recipes | `RecipeRandomizer` | Swaps the final `Recipe.ingredients` arrays (via reflection) on the list returned by `Recipes.getDefaultRecipes()`. |
| 4 | Shops | `ShopRandomizer` | Remaps the item ID in `ShopManager.addSellingItem`/`addBuyingItem`. Shop contents are learned in `postInit` by constructing every `HumanShop` mob. |
| 5.1, 9 (weapons) | Weapon damage and crit | `WeaponRandomizer` | Modifies the result of `ToolItem.getFlatAttackDamage`. |
| 5.2, 5.3 | Enemy and boss damage | `MobDamageRandomizer` | Scales the `GameDamage` argument of `Mob.isServerHit` (every server-side hit) by a per-type factor for the attacker's `getAttackOwner()`. Only the outermost call scales it, because mounted targets re-enter `isServerHit` with the same damage. In `preswap` mode the factor is the original's base damage divided by the replacement's, using the inverse of the spawn mapping. Base damage is the median of every `GameDamage` found on a template mob, its AI (after `init()`) and its class's static fields. Boss starters measure their fighters (`MobPools.fighters`). Missing values are estimated from max health, clamped to the pool's measured range. |
| 6 | Health of enemies, villagers, critters, animals, bosses | `MobHealthRandomizer` | Scales the result of `Mob.getMaxHealth()` through a float array indexed by numeric mob ID, because it's called constantly. `getMaxHealthFlat()` is final, raw, and read-modify-written by `MobDifficultyChanges`, so scaling it would accumulate. `preswap` uses the base `getMaxHealthFlat()` of the original and the replacement. The slider multiplier applies in both modes. Boss starters pass their factor to their fighters (`MobPools.fighters`), and Sage and Grit's base health is Sage + Grit (its starter has a placeholder health). |
| 7, 8 | Defense (armor) and knockback taken, for the same 5 groups | `MobStatRandomizer` | Adjusts the results of `Mob.getArmor()` and `Mob.getKnockbackModifier()` as `value * mult[id] + add[id]`. `preswap` replaces the value instead of scaling it, because many mobs have 0 armor: `add = originalBase * multiplier - ownBase`. Settings are named `<group><Stat>`, `<group><Stat>Mode`, `<group><Stat>Min` and `<group><Stat>Max` (e.g. `bossKnockbackMax`), and are saved, loaded and shown in the settings screen generically (`addStats`/`loadStats`, `statBlocks`). Worm and chain body parts copy the head's raw armor, so they keep the unrandomized value. |
| 9, 12 (equipment) | Equipment crit and damage % | `EquipmentRandomizer` | `ArmorModifiers` constructor (only inside an "armor context") and `ActiveBuff.init` for `ArmorBuff`s. Writes the `ModifierContainer` internals directly, because `ActiveBuff.setModifier` would trigger a buff manager update. |
| 10 | Set bonuses | `EquipmentRandomizer` | Swaps `SetHelmetArmorItem.buffType`. |
| 11 | Equipment effects | `EquipmentRandomizer` | Swaps `SimpleTrinketItem.buffStringIDs`. `EquipmentActiveBuff.addArmorBuff` uses a proxy item of the swapped piece, then restores the original armor value. `ArmorItem.addModifierTooltips` is replaced so tooltips match. |
| 13 | Visuals | `VisualRandomizer` | Rewrites the path in `GameTexture.fromFileRawUnknown`. |
| 14 | Projectiles | `ProjectileRandomizer` | Swaps the entity in `EntityList.add`. Network spawns use `addHidden`, so clients never swap twice. |
| 15 | Translations | `TranslationRandomizer` | Rewrites the `TranslationCategory` maps of every `FileLanguage`, in `preInit` and after `FileLanguage.reload`. |
| 16 | Sounds | `SoundRandomizer` | Rewrites the path in `GameSound.fromFileRaw`. Categories come from folder and file name. |
| 17 | Quest rewards (Elder story objectives, adventure journal challenges) | `QuestRewardRandomizer` | No patch. In `postInit`, it wraps each reward `LootTable` in a `Mapped` item that rolls the original table and swaps every item for a seeded pick from `DropRandomizer.randomItemPool()`. Each pick is keyed by quest and original item. Elder tables are the static `LootTable` fields of each class in `StoryObjectiveRegistry`'s `sortedElements`. Journal tables are `MultiJournalChallenge.reward`. The quest/journal windows (`addPossibleLoot`) and the claim (`addItems`/`getNewList`) both go through the wrapper, so they always match. Named collections ("Frog costume") are listed item by item, because an empty preview hides the journal's claim button. `KEPT` keeps the Leather Dashers from `gettingstarted`, since `equippingtrinket` requires them. |
| 18 | Fishing: bait and rod power, catches | `FishingRandomizer` | In `postInit`, writes the new power into `BaitItem.fishingPower` and the final `FishingRodItem.fishingPower` (via reflection). `WaitFishingPhase` and the tooltips read those fields. Catches are swapped on exit of the instance method `FishingLootTable.getRandomItem(FishingSpot, GameRandom)`, which the static overloads, players and settlers all reach. `fish` mode is a permutation of the obtainable `FishItemInterface` items. `all` mode is a seeded pick from `DropRandomizer.randomItemPool()` for each caught item ID. `ReelFishingPhase` hard-codes `"icefish"` for the journal challenge, so its constructor patch also completes the challenge when the player catches what the icefish became. |

**Pools and safety rules worth knowing before changing things:**
- **Pools:** mobs only swap inside their pool (`MobPools`: hostile, critter, animal, boss, town). Hostile mobs come from the adventure journal, which filters out raiders, portals and boss minions. Animals are the non-hostile `FriendlyRopableMob`s (sheep, cows, chickens, ostriches, penguins…). Animals aren't critters and are only placed at world generation. That's why the generation classes (`GeneratorPlaceFactory`, `GenerationTools`, `CellAutomaton`, `ModularGeneration`) are whitelisted callers too: these swaps only affect newly generated regions. Item pools skip anything that fails `ItemRegistry.isObtainable`.
- **Mob caller whitelist:** mob spawn remaps rely on the caller whitelist in `MobSpawnRandomizer.allowedPools`. A lot of game code casts the result of `getMob` to a concrete class. Only add callers that treat the result as `Mob`/`HumanMob`, or a `ClassCastException` will crash world generation. `MobSave` (save loading) and client network spawns must never be remapped.
- **Texture families:** textures swap as families (`zombie`, `zombiearms_left`, `zombiearms_right`, …). A family only swaps with others from the same folder that have the same set of parts at the same pixel sizes. Armor sheets also stay within their `ArmorType` slot.
- **Troca Bugada Divertida (`funnyBuggedSwap`):** the deliberate exception to texture families. In `mobs/**` every part swaps on its own, grouped by part and size (`swapPartsSeparately`). It works even with `visuals` off.
- **Projectile safety:** replacements are created with the empty constructor plus `applyData`, so `ProjectileRandomizer.hasMissingData` excludes projectile types whose own fields stay null that way. That covers their own owner or damage, items and arrays; sounds, trails, textures, chains and the homing `target`/`targetPos` are allowed. For example, `BoneSpikesProjectile` has its own `Mob owner` and spawned ownerless spike mobs that crashed the server. Name rules, boomerangs and `KNOWN_BAD` are extra filters. At runtime, a replacement that throws in `add`/`init` is dropped, and its type stops being used.
- **Tick safety net:** `srs/core/SafeTick` wraps the per-entity consumers of the `projectile` and `mob` lists in `serverTick`, `clientTick` and `frameTick` (movement, where projectile hits happen). An entity that throws is removed and logged once, instead of Necesse stopping the server with "Stuck in crash loop".
- **Threads:** commands typed in a dedicated server's console run on the "Command scanner" thread, and touching levels from there while the server ticks deadlocks. Anything that touches the world from a command goes through `ServerTasks.submit`, which `ServerTickPatch` runs at the end of `Server.tick()`. Pass `pausewhenempty false` in the console before `srs test` when testing headless, otherwise the empty server doesn't tick entities. The self test flies every replacement projectile with `tickMovement` + `serverTick` until it ends, then ticks any mobs it created.

**Settings screen** (`srs/ui/`). `ModsFormSetupInfoPatch` hooks the exit of `ModsForm.setupInfo`, the method that fills the right-hand info panel when a mod is selected in the mods menu. `ModsMenuHook` then appends a "Configurar randomizer" button below the panel's content. The button adds the screen from `SRSConfigForm.createScreen` to the `ModsForm` switcher as a regular (not temporary) component, because it has to survive switching to a confirmation dialog and back. The screen is a `FormComponentList` holding the form plus one Necesse `FormTabTextComponent` per section: tabs draw above the form's top edge at absolute positions, so they can't be children of the form (same layout as `TabbedFormPreset`). `buildContent()` walks every section each time. `section()` collects the tab titles and turns `visible` on only for the selected tab, and every helper returns early when it's off. A tab switch first pulls the text inputs into `edit`. `close()` switches back to `main` and removes the screen. The boss and town NPC checkboxes come from `MobChoices`. Their names are read through `TranslationRandomizer.originalText`, which keeps the pre-shuffle `mob` category texts, so they stay readable when translations are shuffled. `/srs test` prints both lists. `SRSConfigForm` edits a copy of `SRS.stored()` and saves it the same way `/srs` does. It reuses the mods menu's private `confirmationForm` for the "unsaved changes" prompt, and `ModsForm.restartGameRunnable()` for the restart button. Form components (`FormSlider`'s texture, label and checkbox text wrapping) need the client's fonts and interface textures, so this screen can only be checked in the real client.

**Multiplayer.** On `ServerClientConnectedEvent`, the server sends `PacketSRSConfig` with its settings. If the client's active settings differ, the client stores the server's settings in its cfg and asks the player to restart. A local single-player server shares the JVM, so nothing happens there.

**Language.** Player-facing text (config comments, `/srs` output, chat messages, spoiler headings) is Brazilian Portuguese, written without accents. Code and comments are English.
