package net.camacraft.noconflict.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.KeyHandler;
import net.camacraft.noconflict.client.KeyRouting;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mod's only keyboard entry point (no {@code InputEvent.Key} listener: that event fires from
 * this same method). HEAD sees the physical event before vanilla or any screen and sets the route
 * it should take; RETURN runs what has to wait until vanilla has dealt with it (a tap delivered
 * after its release, a route ended after the release it carried). No Conflict's own replays come
 * through here too and are left alone. Early priority: the route must be set before another
 * mod's HEAD listener (VMod's) asks a mapping whether the key is its.
 */
@Mixin(value = KeyboardHandler.class, priority = 500)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void noconflict$keyPressHead(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (windowPointer != Minecraft.getInstance().getWindow().getWindow() || KeyRouting.isReplaying()) return;
        InputConstants.Key inputKey = InputConstants.getKey(key, scanCode);
        KeyHandler.onRawInput(inputKey, key, scanCode, action, modifiers);
    }

    @Inject(method = "keyPress", at = @At("RETURN"))
    private void noconflict$keyPressReturn(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (windowPointer != Minecraft.getInstance().getWindow().getWindow() || KeyRouting.isReplaying()) return;
        KeyHandler.afterRawInput();
    }
}
