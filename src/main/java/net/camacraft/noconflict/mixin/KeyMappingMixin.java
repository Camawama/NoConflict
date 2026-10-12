package net.camacraft.noconflict.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.client.KeyMappingExtensions;
import net.camacraft.noconflict.client.KeyRouting;
import net.camacraft.noconflict.client.RadialAction;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * The three questions a key press asks a mapping, answered through {@link KeyRouting}: a mapping
 * No Conflict manages on a key answers "yes" only while that key is routed to it. Its key itself
 * is never touched, so everything that shows or reads the binding sees the real one.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements KeyMappingExtensions, RadialAction {
    @Shadow
    private int clickCount;

    @Shadow
    @Final
    private static Map<String, KeyMapping> ALL;

    @Shadow
    public abstract String getName();

    @Shadow(remap = false)
    public abstract InputConstants.Key getKey();

    @Shadow(remap = false)
    public abstract IKeyConflictContext getKeyConflictContext();

    @Shadow(remap = false)
    public abstract KeyModifier getKeyModifier();

    @Override
    public void incrementClickCounter() {
        this.clickCount++;
    }

    @Override
    public String getActionName() {
        return getName();
    }

    /** Raw keyboard events (a screen's {@code keyPressed}, a mod's key listener). */
    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void noconflict$matches(int keysym, int scancode, CallbackInfoReturnable<Boolean> cir) {
        if (!KeyRouting.passes((KeyMapping) (Object) this)) cir.setReturnValue(false);
    }

    /** Raw mouse events. */
    @Inject(method = "matchesMouse", at = @At("HEAD"), cancellable = true)
    private void noconflict$matchesMouse(int button, CallbackInfoReturnable<Boolean> cir) {
        if (!KeyRouting.passes((KeyMapping) (Object) this)) cir.setReturnValue(false);
    }

    /**
     * Forge's {@code IForgeKeyMapping.isActiveAndMatches}, which has no body in {@code KeyMapping}
     * to inject into: this overrides the interface's default with the same test plus the route.
     * Where another mod already owns this method (Controllable overwrites it), Mixin skips this one with a warning;
     * vanilla's own dispatch ({@code KeyMapping.click} / {@code set}) is filtered in {@link KeyMappingLookupMixin}
     * either way, so only mods calling {@code isActiveAndMatches} themselves then see unrouted mappings.
     */
    public boolean isActiveAndMatches(InputConstants.Key keyCode) {
        return keyCode != InputConstants.UNKNOWN && keyCode.equals(getKey()) && getKeyConflictContext().isActive()
                && getKeyModifier().isActive(getKeyConflictContext())
                && KeyRouting.passes((KeyMapping) (Object) this);
    }

    /**
     * {@code setAll} (the mouse grabbed again after a screen) sets every mapping held whose key is
     * physically down, managed or not: put the managed ones the key isn't routed to back up.
     */
    @Inject(method = "setAll", at = @At("RETURN"))
    private static void noconflict$setAll(CallbackInfo ci) {
        for (KeyMapping mapping : ALL.values()) {
            if (!KeyRouting.passes(mapping)) mapping.setDown(false);
        }
    }
}
