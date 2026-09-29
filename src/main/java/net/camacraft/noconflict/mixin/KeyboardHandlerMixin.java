package net.camacraft.noconflict.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.KeyHandler;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    // This HEAD injection is the mod's ONLY keyboard entry point. Do not also subscribe to
    // InputEvent.Key — that event fires from this same method and would double-process input.
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKeyPress(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (windowPointer != Minecraft.getInstance().getWindow().getWindow()) return;
        InputConstants.Key inputKey = InputConstants.getKey(key, scanCode);
        KeyHandler.onRawInput(inputKey, scanCode, action, modifiers);
    }
}
