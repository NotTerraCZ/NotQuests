# NotQuests

A quest book for **NeoForge 1.21.1**. Quests sit on a map connected by lines – finishing one quest unlocks the next. Build them in-game with the editor, or write simple config files (works great with AI – see below).

> Independent mod inspired by FTB Quests. Not affiliated with FTB, no FTB code or assets.

## Features

- Quest map with chapters and chapter groups
- 11 task types: item, kill, location, dimension, biome, structure, advancement, checkmark, XP, statistic, observation
- 10 reward types: item, XP, XP levels, command, random / choice / all items from a reward table, loot table, advancement, toast
- Full in-game editor – right-click menus, drag & drop, copy / paste
- Dependencies – finishing a quest unlocks the next one, also across chapters (a quest can require a whole chapter, so chapters can't be skipped)
- Teams with shared progress
- Pin (bookmark) quests – they stay in the corner of the screen with their progress and what is still missing
- **JEI / EMI support** – click an item in a quest to see how to get it (R = recipes, U = uses)
- Completion toasts, Claim All button
- Quest Book item and a book button in the inventory
- Animated icons – a quest that wants several things shows each of them in turn, "any log" tasks show every log
- Simple config files + `/notquests reload`

## How to use

- Open the book with the `` ` `` key (left of 1), the book button in your inventory or the Quest Book item.
- Operators: click the **pencil** in the book to edit. Right-click the map to add quests. To connect quests, Ctrl+click a quest, right-click the next one → **Require Selected**.
- Quests from other chapters: right-click a quest → **Require Whole Chapter** or **Require Quest From Chapter**. Such quests get a small chain icon. Chapter categories are **chapter groups** (**+ Group** under the chapter list).
- Pin a quest with the **pin** icon in its window. It stays in the top-left corner with its progress (position and size: `config/notquests-client.toml`).
- With JEI or EMI installed: click an item in a quest, or hover it and press **R** (recipes) / **U** (uses).
- Quest files are in `config/notquests/quests/`. After editing them run `/notquests reload`.
- Install on the **server and the client**.

## Config example

On the first start NotQuests puts exactly these files into `config/notquests/quests/`. Files you write by hand stay as they are – a file is only saved again when you change that chapter in the in-game editor (or when a quest in it has no id yet).

```
config/notquests/quests/
├── data.snbt               book settings + chapter groups
├── chapters/*.snbt         one file per chapter
└── reward_tables/*.snbt    one file per reward table
```

```
// config/notquests/quests/data.snbt
{
	title: "My Pack Quests",
	icon: "minecraft:writable_book",
	give_book_on_first_join: true,
	chapter_groups: [
		{ id: "group_main", title: "Main Story", icon: "minecraft:nether_star" }
	]
}
```

```
// config/notquests/quests/chapters/the_beginning.snbt
{
	id: "begin",                        // unique id, any text
	title: "&aThe Beginning",           // & = color codes
	icon: "minecraft:oak_sapling",
	group: "group_main",                // group id from data.snbt
	quests: [
		{
			id: "begin_welcome",
			title: "&eWelcome!",
			description: ["Welcome to the pack!", "Click the checkmark, then claim your reward."],
			x: 0, y: 0,                     // position on the map (x = right, y = down)
			size: 1.5,
			shape: "gear",
			tasks: [ { type: "checkmark" } ],
			rewards: [ { type: "item", item: "minecraft:bread", count: 8 } ]
		},
		{
			id: "begin_wood",
			title: "Timber!",
			x: 2.5, y: -1.25,
			dependencies: ["begin_welcome"], // unlocks after these quests
			tasks: [ { type: "item", item_tag: "minecraft:logs", count: 16 } ],
			rewards: [ { type: "xp", xp: 50 } ]
		},
		{
			id: "begin_stone",
			title: "Stone Age",
			x: 5, y: -1.25,
			dependencies: ["begin_wood"],
			tasks: [ { type: "item", item: "minecraft:cobblestone", count: 32, consume_items: true } ],
			rewards: [ { type: "random", table_id: "starter_loot" } ]
		},
		{
			id: "begin_hunter",
			title: "Monster Hunter",
			x: 2.5, y: 1.25,
			dependencies: ["begin_welcome"],
			tasks: [ { type: "kill", entity: "minecraft:zombie", value: 5 } ],
			rewards: [ { type: "xp_levels", xp_levels: 3 } ]
		},
		{
			id: "begin_iron",
			title: "Iron Age",
			x: 7.5, y: 0,
			dependencies: ["begin_stone", "begin_hunter"],
			tasks: [ { type: "item", item_tag: "c:ingots/iron", count: 8 } ],
			rewards: [ { type: "command", title: "Iron Pickaxe", icon: "minecraft:iron_pickaxe", command: "/give {p} minecraft:iron_pickaxe" } ]
		},
		{
			id: "begin_nether",
			title: "Into the Nether",
			x: 10, y: 0,
			size: 1.25,
			dependencies: ["begin_iron"],
			tasks: [ { type: "dimension", dimension: "minecraft:the_nether" } ],
			rewards: [ { type: "choice", table_id: "starter_loot" } ]
		}
	]
}
```

```
// config/notquests/quests/reward_tables/starter_loot.snbt
{
	id: "starter_loot",
	title: "Starter Loot",
	rewards: [
		{ item: "minecraft:bread", count: 6, weight: 10 },   // higher weight = more common
		{ item: "minecraft:iron_ingot", count: 3, weight: 5 },
		{ item: "minecraft:diamond", weight: 1 }
	]
}
```

```
// ALL TASK TYPES
{ type: "checkmark" }                                                         // player clicks it
{ type: "item", item: "minecraft:diamond", count: 3 }                         // just have it in the inventory
{ type: "item", item: "minecraft:cobblestone", count: 64, consume_items: true } // player hands it in
{ type: "item", item_tag: "c:gems/diamond", count: 3 }                        // any item from a tag
{ type: "item", item: "minecraft:crafting_table", only_from_crafting: true }
{ type: "kill", entity: "minecraft:zombie", value: 10 }                       // or a tag: "#minecraft:skeletons"
{ type: "location", dimension: "minecraft:overworld", location: [0, 60, 0, 16, 16, 16] } // x, y, z, width, height, depth
{ type: "dimension", dimension: "minecraft:the_end" }
{ type: "biome", biome: "minecraft:desert" }                                  // or "#minecraft:is_forest"
{ type: "structure", structure: "#minecraft:village" }                        // or "minecraft:stronghold"
{ type: "advancement", advancement: "minecraft:story/mine_diamond" }
{ type: "xp", value: 10 }                                                     // player pays 10 levels
{ type: "stat", stat: "minecraft:jump", value: 500 }
{ type: "observation", observe_type: "block", to_observe: "minecraft:diamond_ore", timer: 40 } // look at it for 2 s

// ALL REWARD TYPES
{ type: "item", item: "minecraft:diamond", count: 2 }
{ type: "xp", xp: 100 }
{ type: "xp_levels", xp_levels: 5 }
{ type: "command", command: "/give {p} minecraft:elytra", title: "Elytra", icon: "minecraft:elytra" }
{ type: "random", table_id: "starter_loot" }                                  // random item from a reward table
{ type: "choice", table_id: "starter_loot" }                                  // player picks one
{ type: "all_table", table_id: "starter_loot" }                               // everything from the table
{ type: "loot_table", loot_table: "minecraft:chests/simple_dungeon" }
{ type: "advancement", advancement: "minecraft:story/root" }
{ type: "toast", title: "Well done!", description: "You found the secret." }

// MORE QUEST OPTIONS
// dependencies: ["iron_age", "begin"]  (quests from any chapter, or a chapter id = the whole chapter must be done)
// optional: true, invisible: true, hide_until_deps_complete: true, can_repeat: true,
// dependency_requirement: "one_completed"  (all_completed, one_completed, all_started, one_started)
// shape: circle, square, rsquare, diamond, pentagon, hexagon, octagon, heart, gear
```

## Make quests with AI

Copy the config example into ChatGPT / Claude / Gemini and write for example:

> Make me a NotQuests chapter in exactly this format: 10 quests about the Nether, only vanilla 1.21.1 items, connected with dependencies.

Save the result into `config/notquests/quests/chapters/` and run `/notquests reload`. If a file has a mistake, the log tells you the line.

## Commands

Every command works as `/notquests …` and as the shorter `/quest …`.

- `/quest open_book`
- `/quest editing_mode` (op)
- `/quest reload` (op)
- `/quest change_progress <players> complete|reset <quest|chapter|*>` (op)
- `/quest team create|invite|join|leave|kick|rename|info|list`

## Download

Get the jar from [Releases](../../releases) (or from CurseForge) and put it into the `mods` folder of the client and the server.

## Building

Needs Java 21.

```
./gradlew build
```

The mod jar ends up in `build/libs/`.

For development: `./gradlew runClient` / `./gradlew runServer` (add `-PrecipeViewer=jei` or `-PrecipeViewer=emi` to test with a recipe viewer).

Author: **NotTerra** · License: **MIT**
