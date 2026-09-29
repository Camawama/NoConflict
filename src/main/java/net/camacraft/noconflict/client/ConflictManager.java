package net.camacraft.noconflict.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.camacraft.noconflict.Config;
import net.camacraft.noconflict.KeyHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConflictManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<InputConstants.Key, List<KeyMapping>> conflicts = new HashMap<>();

    // Mappings that share a key with an ignored mapping AND conflict contextually. Under
    // IgnoredConflictBehavior.UNBIND they are unbound to prevent vanilla double-triggering;
    // under KEEP_BOUND (default) they stay natively bound and this map stays empty for them.
    private static final Map<InputConstants.Key, List<KeyMapping>> suppressedConflicts = new HashMap<>();

    // Maps physical key to the mapping selected for TAP behavior
    private static final Map<InputConstants.Key, KeyMapping> selections = new HashMap<>();

    // Maps physical key to the mapping selected for HOLD behavior
    private static final Map<InputConstants.Key, KeyMapping> holdSelections = new HashMap<>();

    private static final Map<KeyMapping, InputConstants.Key> originalKeys = new HashMap<>();

    // Are we currently showing the true keybinds (e.g. inside the controls screen)?
    private static boolean suspended = false;

    // Are we currently in the middle of saving options.txt?
    private static boolean saving = false;

    // Has the initial load config been applied yet?
    private static boolean defaultSelectionsApplied = false;

    public static void initialize() {
        Minecraft mc = Minecraft.getInstance();

        // Don't re-initialize if we are just saving or suspended
        if (saving || suspended) return;

        conflicts.clear();
        suppressedConflicts.clear();
        // We DO NOT clear originalKeys here. This makes initialize() safe to call multiple times,
        // even if the keys are currently set to UNKNOWN from a previous initialization pass.
        // (resume() clears it explicitly, because that's the one moment the real keys are on screen.)

        List<? extends String> abandonedKeys = Config.ABANDONED_KEYS.get();
        List<? extends String> ignoredCategories = Config.IGNORED_CATEGORIES.get();
        List<? extends String> ignoredKeybinds = Config.IGNORED_KEYBINDS.get();

        Map<InputConstants.Key, List<KeyMapping>> allMappingsOnKey = new HashMap<>();

        for (KeyMapping mapping : mc.options.keyMappings) {
            InputConstants.Key key = mapping.getKey();

            // Capture the true physical key if it's currently bound
            if (key != InputConstants.UNKNOWN) {
                originalKeys.put(mapping, key);
            } else {
                // If it's UNKNOWN, fallback to the physical key we recorded previously (if any)
                key = originalKeys.get(mapping);
            }

            if (key != null && key != InputConstants.UNKNOWN) {
                if (abandonedKeys.contains(key.getName())) {
                    continue; // Completely abandon this physical key
                }
                allMappingsOnKey.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
            }
        }

        boolean keepIgnoredConflictsBound =
                Config.IGNORED_CONFLICT_BEHAVIOR.get() == Config.IgnoredConflictBehavior.KEEP_BOUND;
        Map<InputConstants.Key, List<KeyMapping>> nativeOverlaps = new HashMap<>();

        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : allMappingsOnKey.entrySet()) {
            InputConstants.Key key = entry.getKey();
            List<KeyMapping> mappings = entry.getValue();

            for (KeyMapping mapping : mappings) {
                boolean hasConflict = false;
                boolean isSuppressedByIgnored = false;

                boolean isThisIgnored = ignoredCategories.contains(mapping.getCategory()) || ignoredKeybinds.contains(mapping.getName());

                if (isThisIgnored) {
                    continue; // We don't add ignored mappings to conflicts or suppressedConflicts
                }

                for (KeyMapping other : mappings) {
                    if (mapping == other) continue;

                    if (doMappingsConflict(mapping, other)) {
                        hasConflict = true;
                        boolean isOtherIgnored = ignoredCategories.contains(other.getCategory()) || ignoredKeybinds.contains(other.getName());
                        if (isOtherIgnored) {
                            isSuppressedByIgnored = true;
                            break; // Suppressed by an ignored mapping, no need to check further
                        }
                    }
                }

                if (isSuppressedByIgnored) {
                    if (keepIgnoredConflictsBound) {
                        // Overlapping an ignored keybind is treated as intentional (e.g. Grappling
                        // Hook's climb on Sneak): leave the mapping natively bound so both actions
                        // trigger, exactly as they would without No Conflict installed.
                        if (mapping.getKey() == InputConstants.UNKNOWN) {
                            mapping.setKey(key);
                        }
                        nativeOverlaps.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
                    } else {
                        suppressedConflicts.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
                    }
                } else if (hasConflict) {
                    conflicts.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
                }
            }
        }

        warnAboutSuppressedConflicts();
        logNativeOverlaps(nativeOverlaps);

        // Apply default config selections and persisted selections on first load
        if (!defaultSelectionsApplied) {
            applyDefaultSelections();
            defaultSelectionsApplied = true;
        }

        validateSelections(selections);
        validateSelections(holdSelections);

        applyResolutions();
    }

    private static void warnAboutSuppressedConflicts() {
        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : suppressedConflicts.entrySet()) {
            String names = entry.getValue().stream().map(KeyMapping::getName).collect(Collectors.joining(", "));
            LOGGER.warn("NoConflict: unbound [{}] on '{}' because they conflict with an ignored keybind. "
                            + "Add them to ignoredKeybinds (or remove the ignored entry) in noconflict-client.toml to change this.",
                    names, entry.getKey().getName());
        }
    }

    private static void logNativeOverlaps(Map<InputConstants.Key, List<KeyMapping>> nativeOverlaps) {
        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : nativeOverlaps.entrySet()) {
            String names = entry.getValue().stream().map(KeyMapping::getName).collect(Collectors.joining(", "));
            LOGGER.info("NoConflict: [{}] on '{}' overlap an ignored keybind and are left bound natively "
                            + "(all actions on this key trigger together). Set ignoredConflictBehavior to UNBIND "
                            + "in noconflict-client.toml to unbind them instead.",
                    names, entry.getKey().getName());
        }
    }

    private static boolean doMappingsConflict(KeyMapping a, KeyMapping b) {
        IKeyConflictContext ctxA = a.getKeyConflictContext();
        IKeyConflictContext ctxB = b.getKeyConflictContext();
        if (!ctxA.conflicts(ctxB) && !ctxB.conflicts(ctxA)) return false;

        // Forge treats bindings with different explicit modifiers (e.g. B vs SHIFT+B) as
        // non-conflicting; NONE vs a modified binding still conflicts (holding SHIFT+B triggers B too).
        KeyModifier modA = a.getKeyModifier();
        KeyModifier modB = b.getKeyModifier();
        return modA == modB || modA == KeyModifier.NONE || modB == KeyModifier.NONE;
    }

    private static void applyDefaultSelections() {
        List<? extends String> defaultSelectionsList = Config.DEFAULT_SELECTIONS.get();

        for (String entry : defaultSelectionsList) {
            String[] parts = entry.split(":");
            if (parts.length != 3) continue;

            InputConstants.Key physicalKey = parseKey(parts[0]);
            KeyMapping targetMapping = findMapping(parts[2]);

            if (physicalKey != null && targetMapping != null) {
                if (parts[1].equalsIgnoreCase("tap")) {
                    selections.put(physicalKey, targetMapping);
                } else if (parts[1].equalsIgnoreCase("hold")) {
                    holdSelections.put(physicalKey, targetMapping);
                }
            }
        }

        // Persisted selections from previous sessions override config defaults
        SelectionStorage.load();
        applyStoredSelections(SelectionStorage.getTapSelections(), selections);
        applyStoredSelections(SelectionStorage.getHoldSelections(), holdSelections);
    }

    private static void applyStoredSelections(Map<String, String> stored, Map<InputConstants.Key, KeyMapping> target) {
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            InputConstants.Key key = parseKey(entry.getKey());
            KeyMapping mapping = findMapping(entry.getValue());
            if (key != null && mapping != null) {
                target.put(key, mapping);
            }
        }
    }

    private static InputConstants.Key parseKey(String name) {
        try {
            return InputConstants.getKey(name);
        } catch (Exception e) {
            LOGGER.warn("NoConflict: invalid key name '{}' in selections/config", name);
            return null;
        }
    }

    private static KeyMapping findMapping(String name) {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.getName().equals(name)) {
                return mapping;
            }
        }
        return null;
    }

    private static void validateSelections(Map<InputConstants.Key, KeyMapping> mapToValidate) {
        // Note: this only prunes the in-memory maps. SelectionStorage keeps its entries so that
        // selections pointing at a temporarily uninstalled mod's keybinds survive on disk.
        List<InputConstants.Key> toRemove = new ArrayList<>();
        for (Map.Entry<InputConstants.Key, KeyMapping> entry : mapToValidate.entrySet()) {
            if (!conflicts.containsKey(entry.getKey()) || !conflicts.get(entry.getKey()).contains(entry.getValue())) {
                toRemove.add(entry.getKey());
            }
        }
        for (InputConstants.Key key : toRemove) {
            mapToValidate.remove(key);
        }
    }

    public static boolean hasConflict(InputConstants.Key key) {
        return conflicts.containsKey(key);
    }

    /** True if this mapping is under No Conflict's control (its KeyMapping key is forced UNKNOWN). */
    public static boolean isManagedMapping(KeyMapping mapping) {
        for (List<KeyMapping> list : conflicts.values()) {
            if (list.contains(mapping)) return true;
        }
        for (List<KeyMapping> list : suppressedConflicts.values()) {
            if (list.contains(mapping)) return true;
        }
        return false;
    }

    /**
     * True if presses of {@code key} reach this mapping, through its tap or hold selection or the radial
     * menu. Unlike {@link #isManagedMapping}, false for the suppressed conflicts (UNBIND), which nothing reaches,
     * and false while suspended: the controls screen then shows every true key, so a mapping without one there
     * is one the user has just unbound.
     */
    public static boolean isRoutedOn(InputConstants.Key key, KeyMapping mapping) {
        if (suspended) return false;
        List<KeyMapping> mappings = conflicts.get(key);
        return mappings != null && mappings.contains(mapping);
    }

    /** The real physical key the user bound this mapping to, or null if never seen bound. */
    public static InputConstants.Key getOriginalKey(KeyMapping mapping) {
        return originalKeys.get(mapping);
    }

    public static List<KeyMapping> getConflictingMappings(InputConstants.Key key) {
        return conflicts.getOrDefault(key, new ArrayList<>());
    }

    public static List<KeyMapping> getActiveMappings(InputConstants.Key key) {
        List<KeyMapping> active = new ArrayList<>();
        List<KeyMapping> mappings = conflicts.get(key);
        if (mappings != null) {
            for (KeyMapping mapping : mappings) {
                try {
                    if (mapping.getKeyConflictContext().isActive()) {
                        active.add(mapping);
                    }
                } catch (Exception e) {
                    // Ignore badly coded contexts that crash on title screen or when mc.level is null
                }
            }
        }
        return active;
    }

    public static KeyMapping getSelection(InputConstants.Key key) {
        return selections.get(key);
    }

    public static void setSelection(InputConstants.Key key, KeyMapping mapping) {
        if (mapping == null) {
            selections.remove(key);
        } else {
            selections.put(key, mapping);
        }
        SelectionStorage.setTap(key.getName(), mapping == null ? null : mapping.getName());
        applyResolutions();
    }

    public static KeyMapping getHoldSelection(InputConstants.Key key) {
        return holdSelections.get(key);
    }

    public static void setHoldSelection(InputConstants.Key key, KeyMapping mapping) {
        if (mapping == null) {
            holdSelections.remove(key);
        } else {
            holdSelections.put(key, mapping);
        }
        SelectionStorage.setHold(key.getName(), mapping == null ? null : mapping.getName());
        applyResolutions();
    }

    public static void clearAllSelections() {
        selections.clear();
        holdSelections.clear();
        SelectionStorage.clearAll();
        applyResolutions();
    }

    /** Returns true if anything was actually cleared for this key. */
    public static boolean clearSelectionsFor(InputConstants.Key key) {
        boolean removedTap = selections.remove(key) != null;
        boolean removedHold = holdSelections.remove(key) != null;
        SelectionStorage.setTap(key.getName(), null);
        SelectionStorage.setHold(key.getName(), null);
        applyResolutions();
        return removedTap || removedHold;
    }

    public static Set<String> getSelectedKeyNames() {
        Set<String> names = new HashSet<>();
        for (InputConstants.Key key : selections.keySet()) names.add(key.getName());
        for (InputConstants.Key key : holdSelections.keySet()) names.add(key.getName());
        return names;
    }

    public static void suspend() {
        if (suspended) return;
        suspended = true;
        // Restore real keys. This makes them visible and editable in the options menu.
        for (Map.Entry<KeyMapping, InputConstants.Key> entry : originalKeys.entrySet()) {
            entry.getKey().setKey(entry.getValue());
        }
        KeyMapping.resetMapping();
    }

    public static void resume() {
        if (!suspended) return;
        suspended = false;

        // The controls screen just closed, so every mapping currently shows its true key.
        // Re-snapshot from scratch: without this, unbinding a conflicted key in the controls
        // screen would be undone by the stale originalKeys entry.
        originalKeys.clear();

        // Re-read whatever the user actually changed in the keybinds menu
        initialize();
    }

    public static void onSaveStart() {
        if (saving) return;
        saving = true;
        // We temporarily put the real keys back so options.txt saves properly
        // Note: Options.save() is called frequently by the KeyBindsScreen when keys are changed!
        if (!suspended) {
            for (Map.Entry<KeyMapping, InputConstants.Key> entry : originalKeys.entrySet()) {
                entry.getKey().setKey(entry.getValue());
            }
            KeyMapping.resetMapping();
        }
    }

    public static void onSaveEnd() {
        if (!saving) return;
        saving = false;
        // Put the UNKNOWN back if we aren't actively suspended (e.g. in the controls screen)
        applyResolutions();
    }

    private static void applyResolutions() {
        if (suspended || saving) return;

        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : conflicts.entrySet()) {
            InputConstants.Key key = entry.getKey();
            KeyMapping selection = selections.get(key);
            KeyMapping holdSelection = holdSelections.get(key);

            // If nothing is selected for this key and the config dictates TRIGGER_ALL,
            // rebind them to their true physical keys so vanilla handles them natively.
            if (selection == null && holdSelection == null && Config.DEFAULT_TAP_BEHAVIOR.get() == Config.DefaultTapBehavior.TRIGGER_ALL) {
                for (KeyMapping mapping : entry.getValue()) {
                    mapping.setKey(key);
                }
            } else {
                for (KeyMapping mapping : entry.getValue()) {
                    mapping.setKey(InputConstants.UNKNOWN);
                }
            }
        }

        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : suppressedConflicts.entrySet()) {
            for (KeyMapping mapping : entry.getValue()) {
                mapping.setKey(InputConstants.UNKNOWN);
            }
        }

        // We MUST rebuild the static KeyMapping MAP so vanilla's key processor stops routing
        // physical key presses to these conflicted keybinds!
        KeyMapping.resetMapping();
    }

    public static void enforceResolutions() {
        if (suspended || saving) return;
        boolean needsReset = false;
        boolean needsReinit = false;

        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : conflicts.entrySet()) {
            InputConstants.Key key = entry.getKey();

            // Skip keys KeyHandler is temporarily binding for a native GUI action, an active hold,
            // or a synthetic tap that is being held down until the end of the current tick
            if (KeyHandler.isPressedInGui(key) || KeyHandler.isHoldActive(key) || KeyHandler.isSyntheticTapActive(key)) {
                continue;
            }

            KeyMapping selection = selections.get(key);
            KeyMapping holdSelection = holdSelections.get(key);

            InputConstants.Key expected =
                    (selection == null && holdSelection == null && Config.DEFAULT_TAP_BEHAVIOR.get() == Config.DefaultTapBehavior.TRIGGER_ALL)
                            ? key : InputConstants.UNKNOWN;

            for (KeyMapping mapping : entry.getValue()) {
                InputConstants.Key current = mapping.getKey();
                if (current.equals(expected)) continue;

                if (isExternalRebind(mapping, current)) {
                    // Another mod rebound this mapping at runtime; adopt the new binding instead of stomping it
                    originalKeys.put(mapping, current);
                    needsReinit = true;
                } else {
                    mapping.setKey(expected);
                    needsReset = true;
                }
            }
        }

        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : suppressedConflicts.entrySet()) {
            for (KeyMapping mapping : entry.getValue()) {
                InputConstants.Key current = mapping.getKey();
                if (current == InputConstants.UNKNOWN) continue;

                if (isExternalRebind(mapping, current)) {
                    originalKeys.put(mapping, current);
                    needsReinit = true;
                } else {
                    mapping.setKey(InputConstants.UNKNOWN);
                    needsReset = true;
                }
            }
        }

        if (needsReinit) {
            // Re-scan conflicts with the adopted bindings (also reapplies resolutions + resetMapping)
            initialize();
            return;
        }

        if (needsReset) {
            KeyMapping.resetMapping();
        }
    }

    private static boolean isExternalRebind(KeyMapping mapping, InputConstants.Key current) {
        if (current == InputConstants.UNKNOWN) return false;
        InputConstants.Key original = originalKeys.get(mapping);
        return original != null && !current.equals(original);
    }
}
