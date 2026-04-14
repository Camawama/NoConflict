package net.camacraft.noconflict.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.Config;
import net.camacraft.noconflict.KeyHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConflictManager {
    private static final Map<InputConstants.Key, List<KeyMapping>> conflicts = new HashMap<>();
    
    // Mappings that share a key with an ignored mapping AND conflict contextually are suppressed to prevent vanilla double-triggering
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
                    suppressedConflicts.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
                } else if (hasConflict) {
                    conflicts.computeIfAbsent(key, k -> new ArrayList<>()).add(mapping);
                }
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
    
    private static boolean doMappingsConflict(KeyMapping a, KeyMapping b) {
        net.minecraftforge.client.settings.IKeyConflictContext ctxA = a.getKeyConflictContext();
        net.minecraftforge.client.settings.IKeyConflictContext ctxB = b.getKeyConflictContext();
        return ctxA.conflicts(ctxB) || ctxB.conflicts(ctxA);
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
        KeyMapping.resetMapping();
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
            if (selection == null && holdSelection == null && Config.DEFAULT_TAP_BEHAVIOR.get() == Config.DefaultHoldBehavior.TRIGGER_ALL) {
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
        
        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : conflicts.entrySet()) {
            InputConstants.Key key = entry.getKey();
            
            // Skip enforcing if KeyHandler is temporarily binding this key for a native GUI action
            if (KeyHandler.isPressedInGui(key)) {
                continue;
            }
            
            KeyMapping selection = selections.get(key);
            KeyMapping holdSelection = holdSelections.get(key);
            
            if (selection == null && holdSelection == null && Config.DEFAULT_TAP_BEHAVIOR.get() == Config.DefaultHoldBehavior.TRIGGER_ALL) {
                for (KeyMapping mapping : entry.getValue()) {
                    if (mapping.getKey() != key) {
                        mapping.setKey(key);
                        needsReset = true;
                    }
                }
            } else {
                for (KeyMapping mapping : entry.getValue()) {
                    if (mapping.getKey() != InputConstants.UNKNOWN) {
                        mapping.setKey(InputConstants.UNKNOWN);
                        needsReset = true;
                    }
                }
            }
        }

        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : suppressedConflicts.entrySet()) {
            for (KeyMapping mapping : entry.getValue()) {
                if (mapping.getKey() != InputConstants.UNKNOWN) {
                    mapping.setKey(InputConstants.UNKNOWN);
                    needsReset = true;
                }
            }
        }
        
        if (needsReset) {
            KeyMapping.resetMapping();
        }
    }
}