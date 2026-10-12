package net.camacraft.noconflict.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which keybind a physical key reaches right now. No Conflict never unbinds anything: every
 * {@link KeyMapping} keeps its real key, so mods that show their key, compare it with
 * {@code matches}, or read it, see the real one. Instead the three places a mapping is asked
 * "is this press yours" ({@code matches}, {@code matchesMouse} and Forge's
 * {@code isActiveAndMatches}, which also decides which mappings vanilla's {@code KeyMapping.click}
 * and {@code set} reach) answer through {@link #passes}: a mapping No Conflict manages on a key
 * answers only while that key is routed to it.
 *
 * <ul>
 *   <li>Not managed (no conflict, an ignored category or keybind, natively overlapping one): always
 *   its own answer.</li>
 *   <li>Managed: only while the key is routed to it ({@link #route}): a tap or hold selection being
 *   delivered, a press inside a screen meant for the tap selection.</li>
 *   <li>Managed on a key with no selection under {@code TRIGGER_ALL}: every one of them, as vanilla.</li>
 *   <li>Suppressed under {@code ignoredConflictBehavior = UNBIND}: never.</li>
 *   <li>While No Conflict replays a press of a key ({@link #beginReplay}): only the mapping the key is
 *   routed to, managed or not, so nothing else on that key sees the press twice.</li>
 * </ul>
 * Render thread only.
 */
public final class KeyRouting {
    private KeyRouting() {}

    /** Managed mapping -> the key it is managed on (its own key when the conflicts were last scanned). */
    private static final Map<KeyMapping, InputConstants.Key> MANAGED = new IdentityHashMap<>();
    /** Managed mappings nothing reaches (UNBIND). */
    private static final Set<KeyMapping> SILENCED = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Key -> the one mapping it reaches now. */
    private static final Map<InputConstants.Key, KeyMapping> ROUTED = new HashMap<>();
    /** Keys whose managed mappings all pass (no selection, TRIGGER_ALL). */
    private static final Set<InputConstants.Key> PASS_ALL = new HashSet<>();

    private static InputConstants.Key replaying = null;
    private static boolean bypassed = false;

    /** Whether this mapping may take a press of its key now (see the class notes). */
    public static boolean passes(KeyMapping mapping) {
        if (bypassed) return true;
        if (replaying != null) {
            InputConstants.Key own = mapping.getKey();
            if (replaying.equals(own)) return ROUTED.get(replaying) == mapping;
        }
        InputConstants.Key managedOn = MANAGED.get(mapping);
        if (managedOn == null) return true;
        if (!managedOn.equals(mapping.getKey())) return true; // rebound since the last scan: its own again
        if (SILENCED.contains(mapping)) return false;
        if (PASS_ALL.contains(managedOn)) return true;
        return ROUTED.get(managedOn) == mapping;
    }

    static void setManaged(Map<InputConstants.Key, java.util.List<KeyMapping>> conflicts,
                           Map<InputConstants.Key, java.util.List<KeyMapping>> suppressed) {
        MANAGED.clear();
        SILENCED.clear();
        conflicts.forEach((key, list) -> list.forEach(m -> MANAGED.put(m, key)));
        suppressed.forEach((key, list) -> list.forEach(m -> {
            MANAGED.put(m, key);
            SILENCED.add(m);
        }));
        ROUTED.keySet().removeIf(key -> !conflicts.containsKey(key));
    }

    static void setPassAll(Set<InputConstants.Key> keys) {
        PASS_ALL.clear();
        PASS_ALL.addAll(keys);
    }

    /** Whether No Conflict manages this mapping on its current key. */
    public static boolean isManaged(KeyMapping mapping) {
        InputConstants.Key managedOn = MANAGED.get(mapping);
        return managedOn != null && managedOn.equals(mapping.getKey());
    }

    /** Whether nothing reaches this managed mapping (UNBIND). */
    public static boolean isSilenced(KeyMapping mapping) {
        return SILENCED.contains(mapping) && isManaged(mapping);
    }

    public static void route(InputConstants.Key key, KeyMapping mapping) {
        ROUTED.put(key, mapping);
    }

    public static KeyMapping routedTo(InputConstants.Key key) {
        return ROUTED.get(key);
    }

    /** Ends the route of this key (only if it still goes to {@code mapping}, when given). */
    public static void unroute(InputConstants.Key key, KeyMapping mapping) {
        if (mapping == null || ROUTED.get(key) == mapping) ROUTED.remove(key);
    }

    public static Set<InputConstants.Key> routedKeys() {
        return new HashSet<>(ROUTED.keySet());
    }

    public static void beginReplay(InputConstants.Key key) {
        replaying = key;
    }

    public static void endReplay() {
        replaying = null;
    }

    public static boolean isReplaying() {
        return replaying != null;
    }

    /** Every mapping answers for itself (the controls screen is open: the player sees and edits the true bindings). */
    public static void setBypassed(boolean value) {
        bypassed = value;
    }

    public static boolean isBypassed() {
        return bypassed;
    }
}
