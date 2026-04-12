package net.camacraft.noconflict.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.Config;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConflictManager {
    private static final Map<InputConstants.Key, List<KeyMapping>> conflicts = new HashMap<>();
    
    // Mappings that share a key with an ignored mapping (e.g. Movement) are suppressed to prevent vanilla double-triggering
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
        originalKeys.clear();
        
        List<? extends String> ignoredCategories = Config.IGNORED_CATEGORIES.get();
        List<? extends String> ignoredKeybinds = Config.IGNORED_KEYBINDS.get();

        Map<InputConstants.Key, Boolean> hasIgnored = new HashMap<>();
        Map<InputConstants.Key, List<KeyMapping>> tempConflicts = new HashMap<>();

        for (KeyMapping mapping : mc.options.keyMappings) {
            InputConstants.Key key = mapping.getKey();
            originalKeys.put(mapping, key);
            
            if (key != InputConstants.UNKNOWN) {
                if (ignoredCategories.contains(mapping.getCategory()) || ignoredKeybinds.contains(mapping.getName())) {
                    hasIgnored.put(key, true);
                } else {
                    tempConflicts.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
                }
            }
        }
        
        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : tempConflicts.entrySet()) {
            InputConstants.Key key = entry.getKey();
            if (hasIgnored.getOrDefault(key, false)) {
                // If the key has an ignored mapping (like Movement), suppress all other conflicting mappings!
                // This prevents vanilla from triggering both actions simultaneously.
                suppressedConflicts.put(key, entry.getValue());
            } else if (entry.getValue().size() > 1) {
                conflicts.put(key, entry.getValue());
            }
        }
        
        // Apply default config selections on first load
        if (!defaultSelectionsApplied) {
            applyDefaultSelections();
            defaultSelectionsApplied = true;
        }
        
        validateSelections(selections);
        validateSelections(holdSelections);

        applyResolutions();
    }
    
    private static void applyDefaultSelections() {
        Minecraft mc = Minecraft.getInstance();
        List<? extends String> defaultSelectionsList = Config.DEFAULT_SELECTIONS.get();
        
        for (String entry : defaultSelectionsList) {
            String[] parts = entry.split(":");
            if (parts.length == 3) {
                String physicalKeyName = parts[0];
                String mode = parts[1];
                String keybindName = parts[2];
                
                InputConstants.Key physicalKey = null;
                try {
                    physicalKey = InputConstants.getKey(physicalKeyName);
                } catch (Exception e) {
                    continue; // Invalid key format
                }
                
                KeyMapping targetMapping = null;
                for (KeyMapping mapping : mc.options.keyMappings) {
                    if (mapping.getName().equals(keybindName)) {
                        targetMapping = mapping;
                        break;
                    }
                }
                
                if (physicalKey != null && targetMapping != null) {
                    if (mode.equalsIgnoreCase("tap")) {
                        selections.put(physicalKey, targetMapping);
                    } else if (mode.equalsIgnoreCase("hold")) {
                        holdSelections.put(physicalKey, targetMapping);
                    }
                }
            }
        }
    }

    private static void validateSelections(Map<InputConstants.Key, KeyMapping> mapToValidate) {
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

    public static List<KeyMapping> getConflictingMappings(InputConstants.Key key) {
        return conflicts.getOrDefault(key, new ArrayList<>());
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
        applyResolutions();
    }

    public static void suspend() {
        if (suspended) return;
        suspended = true;
        // Restore real keys. This makes them visible and editable in the options menu.
        for (Map.Entry<KeyMapping, InputConstants.Key> entry : originalKeys.entrySet()) {
            entry.getKey().setKey(entry.getValue());
        }
    }

    public static void resume() {
        if (!suspended) return;
        suspended = false;
        
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
            for (KeyMapping mapping : entry.getValue()) {
                mapping.setKey(InputConstants.UNKNOWN);
            }
        }
        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : suppressedConflicts.entrySet()) {
            for (KeyMapping mapping : entry.getValue()) {
                mapping.setKey(InputConstants.UNKNOWN);
            }
        }
    }
}