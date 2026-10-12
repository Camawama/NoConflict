package net.camacraft.noconflict.api;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.client.ConflictManager;
import net.minecraft.client.KeyMapping;

import javax.annotation.Nullable;

/**
 * Stable public API for other mods (e.g. Binders) to query No Conflict's state.
 * <p>
 * No Conflict resolves keybind conflicts by routing each press of a conflicted key to one
 * {@link KeyMapping} (the tap or hold selection, or the one picked on the radial menu). It never
 * unbinds a mapping: {@code getKey()} is always the real key, and a managed mapping simply doesn't
 * answer to it ({@code matches}, {@code isActiveAndMatches}, clicks, held state) unless the press is
 * routed to it. Mods can use this class to learn how a managed mapping is actually triggered.
 * <p>
 * All methods are client-side only and safe to call every frame. Method signatures in
 * this class are a compatibility contract; do not change them without a major version bump.
 */
public final class NoConflictApi {

    private NoConflictApi() {}

    /**
     * @return true if No Conflict has taken over this mapping (presses of its key reach it
     *         only through tap/hold selections or the radial menu).
     */
    public static boolean isManaged(KeyMapping mapping) {
        return ConflictManager.isManagedMapping(mapping);
    }

    /**
     * @return the physical key this mapping is bound to (its own key: No Conflict never
     *         unbinds anything), or null if it is unbound.
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
