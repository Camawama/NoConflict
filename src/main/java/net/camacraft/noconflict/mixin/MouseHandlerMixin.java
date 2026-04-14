package net.camacraft.noconflict.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.KeyHandler;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void onMousePress(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        InputConstants.Key inputKey = InputConstants.Type.MOUSE.getOrCreate(button);
        KeyHandler.onRawInput(inputKey, -1, action);
    }
}
