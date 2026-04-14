# No Conflict

This mod completely eliminates the age-old problem of keybind conflicts in Minecraft. Instead of forcing players to figure the best configuration for hundreds of keybinds in every single modpack, No Conflict takes over, and allows hot-swapping between keybinds!

# How It Works
When you bind multiple actions to the same key, No Conflict intercepts the key press. Instead of triggering all the conflicting actions simultaneously (or none at all), holding the key down smoothly opens a sleek, customizable **Radial Menu**. This menu dynamically displays all the actions you have bound to that specific key.

You can then quickly flick your mouse to select the action you want to perform and then release the key you were holding to select that action.

No Conflict has two modes (three if you count F3 mode). Tap mode is the default mode when you select an action from the radial menu. Hold mode can be enabled (for one action at a time) by clicking an entry while on the radial menu and then releasing the held key.
- **Tap Mode:** Pressing the key quickly performs the selected action. Open the radial menu again by holding the key down.
- **Hold Mode:** When hold mode is enabled (by clicking on an action in the radial menu), the radial menu will no longer open while holding the key down. Instead, holding down the key will act as if you're holding down the selected entry in the radial menu! To open the radial menu while in hold mode, you must quickly double click the key and hold on the second tap (double tap delay is configurable in the client config).

We even built a specialized mode for the `F3` key! Holding `F3` will open a radial menu containing every single F3 debug combination (like Reload Chunks, Show Hitboxes, TPS/FPS Charts), meaning you no longer have to awkwardly stretch your fingers to hit `F3 + ALT` or `F3 + A`.

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
- `defaultHoldBehavior` (Default: `DO_NOTHING`): What should happen when a conflicted key is held down but no master keybind is set for it.

## Filters & Defaults
- `ignoredCategories` (Default: `["key.categories.movement"]`): A list of keybind categories to ignore. If a key has an action in this category, all other conflicting actions on that key are gracefully suppressed to prevent annoying vanilla double-triggering.
- `ignoredKeybinds` (Default: `["key.attack", "key.use"]`): Specific keybind names to ignore conflicts for (like left/right click).
- `defaultSelections`: A list to manually define default selections for conflicts when the game loads (). Format: `trigger_key:mode:keybind_name`. Example: `key.keyboard.1:tap:key.hotbar.1`.

## Radial Menu Settings
- `radius` (Default: `80`): The base distance from the center for the options in the radial menu.
- `backgroundOpacity` (Default: `0.5`): The darkness of the screen when the menu is open (0.0 to 1.0).
- `allowGuiOverlay` (Default: `false`): Whether the radial menu is allowed to open on top of other GUIs (like the Inventory).
- `guiOverlayBlacklist`: Screens where the radial menu is explicitly forbidden from opening.

## Radial Menu Visuals
All colors use the ARGB hex format (e.g., `FFFFFFFF` for solid white, `44FFFFFF` for translucent white).

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
