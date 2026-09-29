package net.camacraft.noconflict.api;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.client.ConflictManager;
import net.minecraft.client.KeyMapping;

import javax.annotation.Nullable;

/**
 * Stable public API for other mods (e.g. Binders) to query No Conflict's state.
 * <p>
 * No Conflict resolves keybind conflicts by unbinding conflicted {@link KeyMapping}s
 * (setting their key to {@link InputConstants#UNKNOWN}) and routing input itself.
 * Mods that display or match keybinds can use this class to recover the real physical
 * key and to learn how a managed mapping is actually triggered.
 * <p>
 * All methods are client-side only and safe to call every frame. Method signatures in
 * this class are a compatibility contract; do not change them without a major version bump.
 */
public final class NoConflictApi {

    private NoConflictApi() {}

    /**
     * @return true if No Conflict has taken over this mapping (its key is forced UNKNOWN
     *         and presses are routed through tap/hold selections or the radial menu).
     */
    public static boolean isManaged(KeyMapping mapping) {
        return ConflictManager.isManagedMapping(mapping);
    }

    /**
     * @return the physical key the user actually bound this mapping to, or null if
     *         No Conflict has never seen it bound. Also covers mappings suppressed
     *         because they conflict with an ignored keybind.
     */
    @Nullable
    public static InputConstants.Key getPhysicalKey(KeyMapping mapping) {
        return ConflictManager.getOriginalKey(mapping);
    }

    /** @return the mapping the user selected to trigger on a short tap of this physical key, or null. */
    @Nullable
    public static KeyMapping getTapSelection(InputConstants.Key key) {
        return ConflictManager.getSelection(key);
    }

    /** @return the mapping the user selected to trigger on a long hold of this physical key, or null. */
    @Nullable
    public static KeyMapping getHoldSelection(InputConstants.Key key) {
        return ConflictManager.getHoldSelection(key);
    }
}
