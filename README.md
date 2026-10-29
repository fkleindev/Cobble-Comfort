# Cobble Comfort

A client-side quality-of-life mod for **Minecraft 26.3 (Fabric)**: a map and minimap, a storage memory for your
chests, a customisable HUD, smarter inventories, chat comfort and lots of small touches. Everything can be switched on
or off and tuned in the settings. The mod is available in English and German.

It works on any server, including vanilla ones. An optional server plugin lets players with the mod see each other on
the map.

## Download and installation

1. Install [Fabric Loader](https://fabricmc.net/use/) (0.20.1 or newer) for Minecraft 26.3.
2. Put these into your `mods` folder:
   - `cobble-comfort-<version>.jar` from the [latest release](https://github.com/fkleindev/Cobble-Comfort/releases/latest)
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl)
   - optional: [Mod Menu](https://modrinth.com/mod/modmenu), to open the settings from the mod list
3. Start the game. Java 25 or newer is required (the official launcher brings it along).

**Settings:** through Mod Menu, or bind a key to *Open settings* under Options → Controls → Key Binds → Cobble Comfort.
There is also a key for the HUD editor.

## Features

### Map and minimap
- Minimap (round or square, optionally rotating) with coordinates, biome, players, mobs with their faces, a view cone
  and your movement trail.
- Full-screen map (**M**) with cave view, the area below the Nether roof, height shading and a chunk grid. It opens
  out of the minimap, turning north-up as it grows.
- Waypoints (**B** for a new one, **U** for the list) with categories, colours, beams in the world, name and distance.
  Right-click the map for a menu, double-click to set one. New waypoints drop onto the map with a small bounce.
  Waypoints outside the minimap show as small arrows on its edge, which pulse when a waypoint leaves the view.
- Automatic waypoints for your death points and for your current respawn point (bed or respawn anchor).
- **Death inventory:** hover a death waypoint on the map to see what you dropped, with valuable items first. Items you
  pick up again are ticked off ("7/11 stacks back"). A timer shows when the rest despawns. It only runs while you are
  near, because items only despawn while their chunk is loaded, and it also gives the earliest time in case the chunk
  stayed loaded. A popup warns shortly before the despawn. The waypoint turns green when everything is back and grey
  when the items are lost.
- Waypoints appear in the vanilla locator bar above the hotbar, and other players there show as their heads. Look
  straight at one to see its name and distance.
- Waypoints shared in chat in the Xaero format become clickable, and Xaero waypoints can be imported.
- With the world seed: unexplored terrain and structure markers (villages, temples, strongholds, ...). Structures you
  find while exploring are marked too.
- Nether and overworld coordinates are converted for you.

### Storage memory
- Remembers what is in every chest, barrel, shulker box, furnace, hopper, minecart and so on that you open, plus your
  ender chest.
- Search everything you have stored (**N**). The containers holding an item are outlined in the world, shown on the
  map, and a compass arrow points the way.
- Name your containers. See their contents in the look info and get a hint in your inventory when more of an item is
  stored elsewhere.

### Trade memory
- Remembers the offers of every villager and wandering trader you trade with: price (with your discounts), stock,
  job and level.
- Search them in the storage memory's **Traders** tab ("Where was the librarian with Mending?"), cheapest first.
  Click a trader to see it on the map.
- Traders show up on the map with their job block. Hover one to see its offers.
- Item tooltips name who sells the item or buys it for emeralds, with price and distance.
- Name your traders in the trading screen. Traders are followed while they walk around nearby, and forgotten when
  they die or change jobs.

### Pet memory
- Remembers your wolves, cats, parrots and nautiluses (the ones you tamed), and the horses, donkeys, mules, llamas,
  camels and happy ghasts you have ridden or opened, each with its name and where you last saw it.
- They show on the map. Hover one to see its health, whether it sits or is leashed, and a horse's speed and jump
  height.
- Search them in the storage memory's **Pets** tab and click one to see it on the map.
- When a pet dies, a popup tells you, and a grey grave with the death message stays on the map until you remove it.
  Living pets breathe softly on the map, and a fresh grave rises from a little puff of dust.

### Furnace memory
- Furnaces, blast furnaces, smokers and brewing stands you look into are remembered with an estimate of when they will
  be done.
- A label above the block, markers on the map and minimap, and a popup with a sound when something is finished.
- Time only counts while you are near enough for the furnace to actually run.

### HUD
- **HUD editor:** move, scale and hide every HUD element, with snapping, a grid and presets.
- **Look info:** what you are looking at, with the right tool, crop growth, redstone power, breaking progress, health,
  armour, villager level, horse speed and jump height, and container contents.
- **Info HUD:** pick your lines from coordinates (also in the other dimension), direction, biome, light level, time and
  day (with a hint when you can sleep), weather, FPS, ping, chunk, speed, real time and the targeted block. **F6**
  copies your coordinates.
- **Phantom warning:** an info HUD line shows how long ago you last slept, from the second day on, with a
  countdown to when phantoms can spawn. During the last day a small countdown appears on the HUD, and a popup tells you
  once phantoms can come. Then little phantoms glide through the panel.
- **Armour HUD** with durability, a low-durability warning and an arrow counter.
- **Effect HUD** with remaining time, good and bad effects kept apart, and a warning before an effect runs out.
- **Food HUD:** saturation, exhaustion, a preview of what eating restores, and the best food in your inventory.
- **Resource tracker (K):** set collection goals and watch your progress, counting shulker boxes, bundles and nearby
  chests.
- **Pickup log:** a small list like "+32 Cobblestone" that merges amounts and shows your total.
- **Smooth hotbar:** gliding selection, a slightly enlarged selected item and slot numbers. A tool that breaks
  shatters into shards in its slot.
- **HUD animations:** lost health stays bright for a moment and then runs down, healed hearts pop, the experience bar
  fills smoothly with a counting, pulsing level and a "+7" for picked-up experience, shanks and armour pop and fade,
  bubbles pop back, and boss bars glide and run down like the hearts. A level up sends a beam of light up from the
  level number, and at level 30 an enchanting table glows above it.

### Inventory
- **Inventory tools:** search field (also searches tooltips and shulker contents), sort buttons or middle click to sort,
  and slot locks that protect items from being moved or dropped.
- **Moving items:** in chests, barrels, shulker boxes and other storage, one button deposits everything the container
  already holds some of, another tops up your stacks from it; the items fly over one after another. Hold Shift and drag
  over slots to move them all at once.
- **Shulker preview:** see the contents in the tooltip, as a panel while holding the box, and a fill bar.
- **Map and book preview:** maps show their picture in the tooltip, with markers, your own position, scale, centre
  and distance. Written books and books and quills show a page (scroll to turn pages), and enchanted books a compact
  list of enchantments with their level. Hold Shift for a larger view.
- **Rarity:** coloured frames for uncommon, rare and epic items, with optional pulse and shimmer.
- **Inventory effects:** the carried item tilts and pops, slots squash and flash, items fly, with particles and sounds.
- **Workstations and trading:** furnace flames and arrows move smoothly instead of in pixel steps and a finished item
  glints. Results in the anvil, grindstone and smithing table land with a clonk and sparks, and the anvil's XP cost
  counts up. Offers a villager unlocks slide into the list, and its level bar fills smoothly.
- **Smooth GUIs:** inventories, menus and chat open with a short animation. Tooltips fade in, follow the mouse softly and glide from
  one item to the next. Lists, the chat, the creative inventory and the mod's own screens scroll smoothly, and recipe book
  pages slide in.
  Buttons fade their highlight, sliders glide, checkboxes and switches animate, and creative and recipe book tabs slide
  out when picked.
- **Durability and cooldowns:** durability bars glide down and blink shortly before the item breaks; cooldowns (ender
  pearl, shield, wind charge, ...) close a ring around the slot instead of covering the item, with a flash when ready.

### Enchanting
- **Enchantment descriptions:** what each enchantment does, its maximum level, what it applies to and what it conflicts
  with (hold Shift).
- **Enchanting table:** bookshelf counter and markers that show which shelves count or are blocked, the chances for each
  slot, and readable runes.

### Chat
- Player heads in front of messages.
- Timestamps, and repeated messages stacked as "×3".
- Your name and your own keywords highlighted, with a sound.
- Chat search (**Ctrl+F**) and right-click to copy a message.
- Chat tabs: All, Chat, Private, Trade, System and your own tabs by keyword, with unread counts. The closed chat still
  shows everything. In the Private tab, what you type goes to your last partner, and incoming private messages ping
  and blink violet. The marker glides to the chosen tab, and counts hop when something new comes in.
- Longer history that is kept when you disconnect.

### Toasts and player list
- Toasts slide in and out smoothly, and the others move up when one has gone. Advancements made in a row stack onto one
  toast with a "+2", recipe toasts show how many recipes they hold, toasts stay a shorter time, and recipe toasts can be
  switched off.
- The player list fades in, shows the ping as a coloured number, lets players glide to their new place and fade in when
  they join, and frames heads in their team colour.

### Crosshair and transitions
- **Hit marker:** short strokes around the crosshair when an attack lands, yellow for critical hits and red and larger
  for kills.
- **Cooldown ring:** with the attack indicator set to Crosshair, a thin ring around the crosshair closes while the attack
  charges.
- **Transitions:** the world fades in from black after loading and changing dimension, the death screen fades in over a
  greyed world, waking up fades back in, and titles and action bar messages slide in (new action bar messages cross-fade).

### Other
- **Zoom (C):** hold or toggle, scroll to change the zoom level, smooth transition, optional cinematic bars.
- **Fullbright (G):** see in the dark, with a smooth fade.

All keys can be changed under Controls.

## Server plugin: see each other on the map

**Cobble Comfort Sync** is an optional plugin for Spigot or Paper servers (1.21 or newer). It shares player
positions between clients running the mod, so friends show up on the minimap and map beyond render distance and in
other dimensions.

- **Opt-in:** a player is only shared after switching on *Share position with other players* in the mod (in the map's
  layer sidebar or the settings), and only those who share see the others.
- Players without the mod are never sent anything and never show up.
- Shared are the name, world, position, facing and, unless switched off, health and armour.

### For server admins

Put `cobble-comfort-sync-<version>.jar` from the [latest release](https://github.com/fkleindev/Cobble-Comfort/releases/latest)
into the server's `plugins` folder and restart. Settings are in `plugins/CobbleComfortSync/config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `interval-ticks` | `10` | How often positions are sent (20 = once a second). |
| `max-distance` | `0` | Only share players within this many blocks in the same world (0 = no limit, all worlds). |
| `hide-spectators` | `true` | Do not share players in spectator mode. |
| `hide-invisible` | `true` | Do not share players with the invisibility effect. |
| `share-health` | `true` | Send health and armour too. |

Players hidden by vanish plugins are not sent to those who cannot see them.

- `/cobblecomfort` lists who is sharing, and `/cobblecomfort reload` reloads the config (permission
  `cobblecomfort.admin`, ops by default).
- `cobblecomfort.share` (everyone by default) allows sharing and seeing others.

## Building from source

```
./gradlew build
```

The mod ends up in `build/libs/`, the plugin in `server-plugin/build/libs/`.

## License

MIT
