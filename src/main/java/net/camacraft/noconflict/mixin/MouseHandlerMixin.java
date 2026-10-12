package net.camacraft.noconflict.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.KeyHandler;
import net.camacraft.noconflict.client.KeyRouting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mouse side of {@link KeyboardHandlerMixin}. */
@Mixin(value = MouseHandler.class, priority = 500)
public class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void noconflict$onPressHead(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        if (windowPointer != Minecraft.getInstance().getWindow().getWindow() || KeyRouting.isReplaying()) return;
        InputConstants.Key inputKey = InputConstants.Type.MOUSE.getOrCreate(button);
        KeyHandler.onRawInput(inputKey, button, -1, action, modifiers);
    }

    @Inject(method = "onPress", at = @At("RETURN"))
    private void noconflict$onPressReturn(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        if (windowPointer != Minecraft.getInstance().getWindow().getWindow() || KeyRouting.isReplaying()) return;
        KeyHandler.afterRawInput();
    }
}
