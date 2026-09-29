# No Conflict — Full Codebase Audit

**Date:** 2026-07-18
**Scope:** All source under `src/main/java`, resources, mixin config, build scripts (Forge 1.20.1, mod version 0.3.11)

Severity legend:
- 🔴 **Critical** — actively causes wrong behavior in common paths
- 🟠 **Major** — broken edge case, design gap, or compat hazard
- 🟡 **Minor** — polish, robustness, dead code, cosmetics

---

## Resolution status (fix pass, 2026-07-18)

**Fixed:** 1.1–1.8, 2.1–2.6, 3.1–3.6, 4.1 (ObfuscationReflectionHelper + logging), 4.2 (relabeled),
4.3, 4.4 (relabeled Q), 4.5 (F3 actions no longer extend KeyMapping; RadialAction interface),
5.1 (client commands), 5.2 (displayTest added, bogus mixinConfigs line removed), 6.1 (renamed to
`defaultTapBehavior`), 6.2, 6.3 (validators), 6.4 (`/noconflict clear [key]`), §7 (documented in
README "Known Limitations"), §8: localization/lang file added, empty handlers removed, AT removed
(mixin @Shadow suffices), empty catches now log at debug, `heldForMenu` map removed, changelog
updated, `.gitignore` verified correct (build/run are untracked).

**Intentionally not changed:**
- 1.8 (partial): conflicts now respect differing explicit Forge key modifiers, but *triggering* a
  tap still doesn't require the modifier to be physically held — full modifier matching would need
  per-modifier interception state and wasn't worth the risk yet.
- 4.4 (partial): F3+L (profiling) still not offered; add an `F3Action` in `F3MenuManager` if wanted.
- 4.2 (partial): F4 still uses `/gamemode` (needs OP) — the vanilla switcher genuinely cannot stay
  open without the physical F3 key held; now documented in README.
- 3.2: overlay is *blocked* on server-backed container screens rather than rendered without a
  screen swap; a true overlay rewrite (no `setScreen`) is the long-term fix.
- §8: no mod icon/displayURL (needs art/URL from the author); `org.gradle.daemon=false` kept
  (Forge MDK default, historically safer with ForgeGradle).

---

## 1. Input pipeline

### 🔴 1.1 Every key/mouse press is processed TWICE (mixin + Forge event)

`KeyboardHandlerMixin` injects at the **HEAD** of `KeyboardHandler.keyPress` and calls `KeyHandler.onRawInput(...)`. But Forge *also* fires `InputEvent.Key` at the **end** of that same method, and `KeyHandler.onKeyInput` (`KeyHandler.java:363`) subscribes to it and calls `onRawInput` again. Same story for mouse: `MouseHandlerMixin` at HEAD of `MouseHandler.onPress`, plus `InputEvent.MouseButton.Pre` handled at `KeyHandler.java:371`.

So `onRawInput` runs **twice per physical press and twice per release**. Most duplicate work is absorbed by accident (`keyPressTimes.remove` returns null on the second release call), but the PRESS path for hold-mode is not guarded:

```java
// KeyHandler.java:279 — runs twice per press, no isDown() guard
if (mc.screen == null && holdSelection != null && holdSelection == tapSelection && ...) {
    holdSelection.setKey(key);
    holdSelection.setDown(true);
    KeyMapping.click(key);                    // ← twice
    ((KeyMappingExtensions) holdSelection).incrementClickCounter();  // ← twice
}
```

Result: a hold-mode keybind gets its `clickCount` incremented at least twice per press → toggle-style keybinds can toggle twice (appear to do nothing), and consumers of `consumeClick()` fire multiple times.

**Fix:** keep only one entry point. The mixins are the ones you actually need (they run *before* vanilla processes the key, which the GUI `setKey` trick at `KeyHandler.java:286` depends on). Delete the `onKeyInput` / `onMouseButtonInput` event handlers, or add a reentrancy/dedup guard.

### 🔴 1.2 Quick tap in hold mode fires the action on press AND again on release

When `holdSelection == tapSelection` (the normal result of toggling "Hold" in the menu):
- On PRESS (`KeyHandler.java:279`): the mapping is activated (`setDown(true)` + `click` + `incrementClickCounter`).
- On quick RELEASE (`KeyHandler.java:319–350`): `setDown(false)` runs, then the generic tap path runs **unconditionally** — `incrementClickCounter()` + a `setDown(true/false)` pulse + a synthetic `InputEvent.Key`.

So a single quick tap produces two (with issue 1.1, up to four) activations. The release path should skip the tap trigger when `selection == holdSelection` and the press-time activation already happened.

### 🟠 1.3 `KeyMapping.click(key)` can trigger unrelated keybinds

`KeyHandler.java:199` and `:282` call `KeyMapping.click(key)`. That is a static lookup in `KeyMapping.MAP` by physical key — but the hold selection's `setKey(key)` just before it does **not** update `MAP` (that requires `resetMapping()`). So `click(key)` either no-ops, or worse, hits whatever mapping is *still* bound to that key — i.e. an **ignored** mapping (ignored categories/keybinds keep their real binding). Example: conflicted key shares `W` with `key.forward` (movement is ignored by default) → your `click(W)` increments `key.forward`'s click count on top of vanilla's own native handling.

The manual `incrementClickCounter()` already does the intended job; `KeyMapping.click(key)` should be removed.

### 🟠 1.4 Synthetic `InputEvent.Key` is posted with `action = RELEASE (0)` only

`KeyHandler.java:202,347` post `new InputEvent.Key(key, scanCode, 0, 0)`. Many mods listen for `action == GLFW_PRESS` in their raw input handler — those mods will never see the tap. Also `modifiers` is always 0, so shift/ctrl-sensitive handlers misbehave. Consider posting a PRESS followed by a RELEASE, with real modifiers.

Additionally, the synthetic event re-enters your own `onKeyInput` → `onRawInput(RELEASE)`, which updates `lastReleaseTimes`. After a hold activation (`KeyHandler.java:202`), this pollutes double-tap detection: the "release" timestamp is when the hold *started*, so a quick re-press can spuriously register as a double-tap.

### 🟠 1.5 Distinct hold vs. tap selections silently don't work

If the user sets hold = mapping A and tap = mapping B (left-click A, release over B), holding the key **opens the radial menu** instead of activating A:

```java
// KeyHandler.java:195 — hold only activates when it EQUALS the tap selection
if (holdSelection != null && holdSelection == tapSelection && !isDoubleTapHold) { ... }
else { keyToOpen = key; }
```

The separate `holdSelections` map, the "(Hold)" indicator, and the menu UI all imply hold and tap can be independent, but a hold selection that differs from the tap selection is dead state. Either activate the hold selection on long-press when `hold != tap`, or prevent the UI from producing that combination.

### 🟠 1.6 `isSimulatingF3` is never set — dead guard, vestigial code

`KeyHandler.java:44` declares it, `:365` and `:373` check it, nothing ever sets it to `true`. Either the F3 simulation it protected is gone (it now uses reflection on `handleDebugKeys`) and this should be deleted, or the guard was supposed to be armed somewhere and got lost in the radial-menu overhaul.

### 🟡 1.7 Mouse keys mishandled in the pending-tap GUI path

`KeyHandler.java:116–118`: `mc.screen.keyPressed(pendingTapKey.getValue(), 0, 0)` — if the trigger key is a **mouse button**, `getValue()` is a button index (0–2) being passed as a keyboard keycode (0–2 are not valid GLFW keycodes). The synthetic `InputEvent.Key` at `:123` has the same problem. Mouse-triggered pending taps need `mouseClicked`/`mouseReleased` and `InputEvent.MouseButton` instead.

### 🟡 1.8 Conflict detection ignores Forge key modifiers

`ConflictManager.doMappingsConflict` (`ConflictManager.java:120`) only compares conflict contexts. Forge `KeyMapping`s can bind `B` vs `SHIFT+B` as distinct non-conflicting binds (`KeyModifier`), but this mod treats them as conflicting and unbinds both. Also, when a tap is triggered, the required modifier is never checked or synthesized. Use `KeyMapping.same()` or include `getKeyModifier()` in the conflict test.

---

## 2. ConflictManager / state lifecycle

### 🟠 2.1 Unbinding a key in the Controls screen doesn't stick

`originalKeys` is intentionally never cleared (`ConflictManager.java:45`). Scenario: user opens Controls (→ `suspend()` restores real keys), sets a conflicted mapping to **NONE**, closes the screen (→ `resume()` → `initialize()`). Since `getKey() == UNKNOWN`, `initialize` falls back to the **stale** `originalKeys` entry (`ConflictManager.java:62`) and resurrects the old binding — the mapping stays in the conflict set and the radial menu. The user cannot unbind a conflicted key.

**Fix:** during `resume()` (the one moment when real keys are guaranteed on-screen-truth), re-snapshot: clear `originalKeys` and only re-capture currently bound keys.

### 🟠 2.2 Suppressed mappings become permanently unreachable, silently

When a mapping conflicts with an *ignored* mapping (e.g. any mod keybind sharing `W` with `key.forward`), it goes into `suppressedConflicts` and is set to UNKNOWN forever (`ConflictManager.java:285–289`, re-enforced every tick at `:328`). No radial menu opens for that key, no message is shown — the mod's action simply becomes unusable with zero indication. The config comment ("the game will handle the keys normally") does not match this behavior. At minimum warn in log/chat; better, still offer the radial menu on long-press for suppressed keys.

### 🟠 2.3 User selections are not persisted

`selections` / `holdSelections` live only in memory. Every game restart the user must re-pick everything, unless they hand-write `defaultSelections` entries in the config. This is arguably the biggest UX gap in the mod: serialize selections to the config (or a dedicated json in `config/`) whenever they change, and load them instead of only `DEFAULT_SELECTIONS` on first init.

### 🟠 2.4 Programmatic rebinds by other mods are clobbered every tick

`enforceResolutions()` runs every client tick and forces conflicted mappings back to UNKNOWN using the stale snapshot. If another mod (or a config-sync feature) rebinds its own `KeyMapping` at runtime, the change is silently reverted; `initialize()` only re-scans on `resume()` from the vanilla `KeyBindsScreen`. Consider re-scanning periodically or listening for changes, and recognize keybind screens that don't extend `KeyBindsScreen` (custom mod settings screens) — those never trigger `suspend()`.

### 🟡 2.5 Double initialization at startup

`ConflictManager.initialize()` is called from `FMLClientSetupEvent` (`NoConflict.java:57`) **and** from the first client tick (`KeyHandler.java:55–58`). `initialize` is idempotent so this works, but one of the two should go (the tick-based one is the safer of the two, since all keybind registration and options loading are certainly done by then).

### 🟡 2.6 Dead `screen == null` branch in `onScreenOpen`

`KeyHandler.java:384`: `ScreenEvent.Opening` never fires with a null screen (`setScreen(null)` doesn't post it), so the `resume()` there is dead; the real resume happens in `onScreenClose`. Harmless, but misleading.

---

## 3. RadialMenuScreen

### 🟠 3.1 ESC closes everything instead of returning to the parent screen

`closeRadialMenu()` restores `parentScreen`, but pressing **ESC** goes through vanilla `Screen.keyPressed` → `onClose()` → `setScreen(null)`. With `allowGuiOverlay` enabled and the menu over an inventory, ESC drops the user to the world instead of back to the inventory. Override `onClose()` to call `closeRadialMenu()`.

### 🟠 3.2 Overlaying container screens breaks them

`mc.setScreen(new RadialMenuScreen(...))` calls `removed()` on the current screen. For `AbstractContainerScreen` (chests, machines, etc.), `removed()` **closes the container on the server**. Restoring the parent screen afterwards shows a dead/desynced GUI. `allowGuiOverlay` defaults to false, so most users are safe, but the feature is broken for containers. Either render the radial as an overlay without `setScreen` swapping, or blacklist `AbstractContainerScreen` subclasses by default.

### 🟡 3.3 Parent screen rendered without resize handling

`render()` calls `parentScreen.render(guiGraphics, -1, -1, partialTicks)`. If the window is resized while the radial menu is open, only the radial screen gets `resize()`; the parent renders at stale dimensions until restored.

### 🟡 3.4 Duplicated release logic

`executeSelection` (`RadialMenuScreen.java:322–336`): the `hasClickedDuringThisOpen && selectedIndex != indexClickedDuringThisOpen` branch and the final `else` branch are character-for-character identical. Collapse them.

### 🟡 3.5 Hardcoded center dead-zone

The 30px center radius (`RadialMenuScreen.java:132,161`) ignores `textScale`, `radius`, and GUI scale. On large GUI scales the "X" hitbox is disproportionately small.

### 🟡 3.6 Long unbroken names overflow

Item labels wrap on spaces only (`split(" ")`); translated keybind names without spaces (common in some languages/mods) render as one long overflowing line.

---

## 4. F3 radial menu

### 🟠 4.1 SRG reflection lookup is fragile

`KeyHandler.java:79–91` searches for `handleDebugKeys` or hardcoded SRG `m_90933_` on `mc.keyboardHandler.getClass()`. Problems:
- If another mod subclasses/replaces the keyboard handler, `getDeclaredMethods()` on the subclass won't find the vanilla-declared method. Use `KeyboardHandler.class`.
- Hand-rolled SRG matching is what `ObfuscationReflectionHelper.findMethod(KeyboardHandler.class, "m_90933_", int.class)` exists for — it also works in dev without the name-pair dance.
- The silent fallback (`:100–102`) reimplements only T/A/D; every other action silently no-ops if reflection fails. Log a warning when the method can't be found.

### 🟠 4.2 F4 gamemode "toggle" diverges from vanilla and needs OP

`KeyHandler.java:96` sends `/gamemode` — fails with a server error for non-OP players, only toggles creative↔survival (no spectator/adventure, no switcher UI). The label says "Gamemode Switcher (F4)" but no switcher opens. Consider at least labeling it honestly ("Toggle Creative") or hiding it when the player lacks permission.

### 🟡 4.3 Frame-time graph can only be turned on, never off

`KeyHandler.java:72–74` sets `renderDebug = true; renderFpsChart = true` unconditionally. Vanilla F3+Alt toggles. Selecting the option twice should turn it off.

### 🟡 4.4 Mislabeled/missing actions

- "Show Debug Profiler (Q)" — F3+Q is the **help list** in vanilla; the profiler is F3+L (not offered).
- F3+L (profiling), F3+F (unused), F3+Esc (pause without menu) are absent — fine, but worth a conscious decision.

### 🟡 4.5 `F3ActionMapping` registers ghost KeyMappings globally

Constructing `KeyMapping` subclasses (`F3MenuManager.java:16`) inserts them into the static `KeyMapping.ALL`/`MAP` with the raw category `"F3 Actions"` and untranslated names. They're not in `options.keyMappings` so they stay out of the Controls screen and options.txt, but any mod iterating `KeyMapping.ALL` (sorting by category translation, conflict scanners like Controlling) will see 13 ghost mappings with non-translation-key names. A lightweight record/POJO (name + target key) rendered by the menu would avoid touching vanilla statics entirely.

---

## 5. Commands / sides

### 🟠 5.1 `/noconflict` is a server command that calls client-only code

`NoConflictCommand` is registered via `RegisterCommandsEvent` (server dispatcher) but every executor touches `net.minecraft.client.Minecraft` (`NoConflictCommand.java:43,56,76`). On a **dedicated server**: registration likely survives (lazy resolution), but any player executing it crashes the server thread with `NoClassDefFoundError`/bad access — and in single-player it reads client state from the server thread. Register it as a **client command** via `RegisterClientCommandsEvent` instead; the output is purely informational and client-local anyway.

### 🟡 5.2 mods.toml side/display metadata

- No `displayTest` set. A pure client mod should declare `displayTest="IGNORE_ALL_VERSION"` (and ideally `clientSideOnly=true` on modern FML) so servers listing/joining don't flag a mismatch.
- `properties.noconflict.mixinConfigs="noconflict.mixins.json"` (`mods.toml:4`) is not a recognized FML key — the mixin config is actually wired through the Gradle mixin plugin's `MixinConfigs` manifest attribute. The line is a no-op; delete it to avoid confusion.
- Dependencies are declared `side="BOTH"`; `side="CLIENT"` is more accurate.

---

## 6. Config

### 🟡 6.1 Naming inconsistency around default behavior

The enum is `DefaultHoldBehavior`, the constant is `DEFAULT_TAP_BEHAVIOR`, and the config path is `"defaultHoldBehavior"` while the comment describes *tap* behavior (`Config.java:75–77`). Pick one name; renaming the path will reset existing configs, so if you rename, do it before wide release.

### 🟡 6.2 `holdTime` minimum of 0 breaks tapping

`defineInRange("holdTime", 200, 0, 2000)` — at 0 the menu opens on every press and taps become impossible. A floor of ~50ms is safer.

### 🟡 6.3 Color parsing accepts garbage silently

`getIntColor` falls back to white with no log. Also `Long.parseLong(...,16)` throws on values like `"0x44FFFFFF"` or 9+ digit strings — caught, but a config validator (`define(path, default, validator)`) would surface mistakes at load time.

### 🟡 6.4 No in-game selection reset

There's no command/UI to clear all selections or a single key's selection without opening the menu on that key. A `/noconflict clear [key]` (client command) would help debugging and user recovery, and would pair with persistence (2.3).

---

## 7. Compatibility notes (known limitations worth documenting in README)

- **Mods that read their own `KeyMapping.getKey()`** for HUD hints or raw-GLFW polling (`InputConstants.isKeyDown` with a stored code) will see UNBOUND or bypass the mod entirely — the conflicting binding still fires. This is inherent to the unbind-and-intercept design; document it and grow `ignoredKeybinds` guidance.
- **Mods that poll `isDown()` in a tick handler** never observe a tap: the synthetic `setDown(true) → setDown(false)` pulse (`KeyHandler.java:345–349`) happens within one event dispatch, between ticks. Only `consumeClick()`-style consumers see taps. Hold mode covers Create-style bindings; anything tap-selected that polls `isDown` is a silent no-op.
- **GUI-context keybinds** (e.g. mod hotkeys usable inside inventories): if conflicted and no tap selection exists, they are dead inside GUIs and the menu can't be opened there while `allowGuiOverlay=false` (the default). There is no path for the user to resolve such a conflict without enabling the overlay.
- **Custom controls screens** that don't extend `KeyBindsScreen` (some mods roll their own) never trigger `suspend()`, so users editing binds there see UNBOUND entries and their edits get overwritten by `enforceResolutions`.

---

## 8. Polish / housekeeping

- 🟡 **No localization**: `"Press and hold to assign an action!"` (`KeyHandler.java:355`), `"(Hold)"`, `"Radial Menu"`, F3 action names, and command output are all hardcoded English. There is no `assets/noconflict/lang/en_us.json` at all. Move strings to translation keys.
- 🟡 **No mod icon / logoFile**, no `displayURL` in mods.toml — cosmetic but expected on CurseForge/Modrinth.
- 🟡 Empty `commonSetup` / `onServerStarting` in `NoConflict.java` — delete.
- 🟡 Empty `Config.onLoad` handler — either validate config there or delete the subscriber.
- 🟡 The access transformer on `clickCount` **and** the `@Shadow` in `KeyMappingMixin` are redundant with each other — the mixin alone suffices; drop the AT (one less thing to maintain across mappings).
- 🟡 Empty `catch (Exception e) {}` blocks at `KeyHandler.java:292,341` — at least trace-log; silent swallows made 1.6-style dead code hard to notice.
- 🟡 `heldForMenu` values are written but only ever null-checked; the map could be a `Set` or folded into `keyPressTimes` removal logic.
- 🟡 `changelog.txt` still says "Initial project setup" at v0.3.11.
- 🟡 `run/saves`, `run/logs`, and `build/` artifacts appear in the repo listing — verify `.gitignore` actually excludes them (stale `build/libs/noconflict-0.0.1.jar` suggests some may be tracked).
- 🟡 `org.gradle.daemon=false` in gradle.properties makes every build cold-start; that's a workspace preference, but most devs want it on.

---

## 9. Prioritized fix order

1. **Deduplicate input handling** (1.1) — everything else in the input path is unmeasurable until each press is processed exactly once.
2. **Fix double-fire in hold mode** (1.2) and remove `KeyMapping.click(key)` (1.3).
3. **Persist selections** (2.3) — biggest user-facing win.
4. **Fix unbind-in-controls-screen resurrection** (2.1).
5. **Client-command the `/noconflict` command** (5.1) — dedicated-server crash.
6. **Decide hold≠tap semantics** (1.5) and surface suppressed conflicts (2.2).
7. **ESC/parent-screen handling in the radial menu** (3.1, 3.2).
8. F3 menu robustness (4.1–4.3), synthetic event action/modifiers (1.4), modifier-aware conflicts (1.8).
9. Polish: localization, config naming, metadata, dead code (§6, §8).
