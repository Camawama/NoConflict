# No Conflict

This mod completely eliminates the age-old problem of keybind conflicts in Minecraft. Instead of forcing players to figure the best configuration for hundreds of keybinds in every single modpack, No Conflict takes over, and allows hot-swapping between keybinds!

# How It Works
When you bind multiple actions to the same key, No Conflict intercepts the key press. Instead of triggering all the conflicting actions simultaneously (or none at all), holding the key down smoothly opens a sleek, customizable **Radial Menu**. This menu dynamically displays all the actions you have bound to that specific key.

You can then quickly flick your mouse to select the action you want to perform and then release the key you were holding to select that action.

No Conflict has two modes (three if you count F3 mode). Tap mode is the default mode when you select an action from the radial menu. Hold mode can be enabled (for one action at a time) by clicking an entry while on the radial menu and then releasing the held key.
- **Tap Mode:** Pressing the key quickly performs the selected action. Open the radial menu again by holding the key down.
- **Hold Mode:** When hold mode is enabled (by clicking on an action in the radial menu), the radial menu will no longer open while holding the key down. Instead, holding down the key will act as if you're holding down the selected entry in the radial menu! To open the radial menu while in hold mode, you must quickly double click the key and hold on the second tap (double tap delay is configurable in the client config).

We even built a specialized mode for the `F3` key! Holding `F3` will open a radial menu containing every single F3 debug combination (like Reload Chunks, Show Hitboxes, TPS/FPS Charts), meaning you no longer have to awkwardly stretch your fingers to hit `F3 + ALT` or `F3 + A`.

## Nothing is unbound (0.4.0)

No Conflict never touches a keybind's key: every action keeps the key you gave it, so every mod still shows its real key ("Tab", never "Not Bound"), and keybind screens (vanilla's, Controlling's, any mod's own) show and edit the real bindings. Instead, an action that shares a key with others answers to that key only while the key is *routed* to it: the questions a press asks a keybind (`matches`, `matchesMouse`, Forge's `isActiveAndMatches`, and through them vanilla's clicks and held state) say "not yours" to every action the press isn't meant for.

- **A tap** is known to be a tap when you let go, so No Conflict then replays your press through the game's own keyboard (or mouse) handler with the key routed to the tap selection, and releases it at the end of that tick. Every way a mod listens sees one ordinary press of its key: its keybind's clicks and held state, a raw key listener asking `matches` (VMod's toolgun menu does this), a mixin at the head of the keyboard handler, Forge's input events.
- **A hold** is routed to the hold selection once the key has been down for `holdTime` (the press it missed is replayed then), or at once for an action chosen for both tap and hold, and it lets go with the key.
- **Inside a screen** the press goes to the tap selection at once (a screen can't wait for the release).
- The physical presses still go through the game as always, so a mod that reads raw key codes in real time keeps seeing them; they just reach no action the key isn't routed to.

# Features
- Bind as many actions as you want to a single key!
- Intuitive, snappy Radial Menu with smooth animations.
- Assign specific actions to be the default "Tap" or "Hold" execution for a key.
- Dedicated F3 Debug Radial Menu.
- Highly customizable visuals, including animations, background opacity, colors, and scaling.
- Intelligent Category Filtering: Keybinds in essential categories (like Movement) can be ignored so they are never interrupted by a radial menu.
- Right click an action on the radial menu to manually select it, even if you don't release the held key.

# Configuration

The mod is highly configurable via the `noconflict-client.toml` file located in your `config` folder.

## General
- `f3RadialMenu` (Default: `true`): Whether holding the F3 key should open a special radial menu type with debug actions.
- `holdTime` (Default: `200`): The time in milliseconds a key must be held down to open the radial menu when in tap mode. If pressed quickly, it triggers the action.
- `doubleTapTime` (Default: `300`): The maximum time in milliseconds between two taps to register as a double-tap. Required to open the menu when an action is set to Hold mode.
- `defaultTapBehavior` (Default: `DO_NOTHING`): What should happen when a conflicted key is tapped but no selection has been made for it (`DO_NOTHING` or `TRIGGER_ALL`).

## Filters & Defaults
- `ignoredCategories` (Default: `["key.categories.movement"]`): A list of keybind categories to ignore. Keys with an action in these categories are handled by the game normally and never open a radial menu.
- `ignoredKeybinds` (Default: `["key.attack", "key.use"]`): Specific keybind names to ignore conflicts for (like left/right click).
- `ignoredConflictBehavior` (Default: `KEEP_BOUND`): What to do with keybinds that share a key with an ignored keybind. `KEEP_BOUND` leaves them bound so both actions trigger natively — many mods overlay movement keys on purpose (e.g. Grappling Hook's climb on Sneak) and only act in context. `UNBIND` unbinds them to prevent double-triggering.
- `defaultSelections`: A list to manually define default selections for conflicts when the game loads. Format: `trigger_key:mode:keybind_name`. Example: `key.keyboard.1:tap:key.hotbar.1`.

Selections you make on the radial menu are saved automatically to `config/noconflict-selections.json` and restored on the next launch (they take priority over `defaultSelections`).

## Radial Menu Settings
- `radius` (Default: `80`): The base distance from the center for the options in the radial menu.
- `backgroundOpacity` (Default: `0.5`): The darkness of the screen when the menu is open (0.0 to 1.0).
- `allowGuiOverlay` (Default: `false`): Whether the radial menu is allowed to open on top of other GUIs (like the Inventory).
- `guiOverlayBlacklist`: Screens where the radial menu is explicitly forbidden from opening.

## Radial Menu Visuals
All colors use the ARGB hex format (e.g., `FFFFFFFF` for solid white, `44FFFFFF` for translucent white). A 6-digit RGB value (e.g., `FFFFFF`) is treated as fully opaque.

- `textScale` (Default: `1.0`): The scale of the text.
- `animationSpeed` (Default: `7.0`): Controls the snappiness of the hover animations.
- `separatorColor` (Default: `44FFFFFF`): The color of the dividing lines between menu segments.
- `regularItemColor` (Default: `FFFFFFFF`): Color of unselected items.
- `hoveredItemColor` (Default: `FFFFFF00`): Color when hovering over an unselected item.
- `selectedItemColor` (Default: `FF00AAAA`): Color of the currently active default action.
- `selectedHoveredItemColor` (Default: `FF55FFFF`): Color when hovering over the active default action.
- `holdIndicatorColor` (Default: `FF55FF55`): The color of the `(Hold)` text tag.

## Radial Menu Audio
- `toggleSound` (Default: `true`): Whether a UI click sound plays when making a selection.
- `toggleVolume` (Default: `0.5`): Volume of the toggle sound (0.0 to 1.0).

# Commands

All commands are client-side:
- `/noconflict list keys` — list every keybind ID.
- `/noconflict list categories` — list every keybind category.
- `/noconflict find <category>` — list the keybinds in a category with their bound keys.
- `/noconflict clear` — clear all saved tap/hold selections.
- `/noconflict clear <key>` — clear the selections for one physical key (e.g. `key.keyboard.b`).

# Known Limitations

These follow from the unbind-and-intercept design and are worth knowing about:

- **Mods that poll `isDown()` in a tick handler** observe a *tap* as exactly one tick of the key being down (the replayed press is held until the end of that tick). For "while held" style keybinds (charging, clinging, sprint-like abilities), enable **Hold mode** for them on the radial menu — then they mirror the physical key for the whole press and behave natively.
- **Mods that read raw GLFW state** (`InputConstants.isKeyDown` on their key) or compare a raw key code with their keybind's key themselves (instead of asking the keybind with `matches`) bypass No Conflict and still trigger alongside your selection, on the physical press and on each tap No Conflict replays on that key. Add such keybinds to `ignoredKeybinds`.
- **Keybinds that conflict with an ignored keybind** (e.g. anything sharing a key with movement keys, which are ignored by default) get no radial menu. By default they stay natively bound so all actions on the key trigger together, like vanilla (`ignoredConflictBehavior = KEEP_BOUND`); set it to `UNBIND` to silence them instead (they keep their key, but nothing reaches them). Either way it is logged at startup.
- **Esc, F1, F3 and F4** are keys the game acts on by their code, not through a keybind: a selection on one of them is delivered to its keybind directly (clicks, held state, Forge's input event) instead of replaying the key, which would pause, hide the HUD or toggle the debug screen a second time.
- **GUI-context keybinds** (hotkeys used inside inventories) can only be resolved through the radial menu if `allowGuiOverlay` is enabled; server-backed container screens (chests etc.) never allow the overlay because swapping screens away from them would desync the container.
- **The F4 entry on the F3 menu** toggles creative/survival via `/gamemode`, so it needs operator permission on servers.
