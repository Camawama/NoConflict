package net.camacraft.noconflict.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.KeyHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    // This HEAD injection is the mod's ONLY mouse entry point. Do not also subscribe to
    // InputEvent.MouseButton — that event fires from this same method and would double-process input.
    @Inject(method = "onPress", at = @At("HEAD"))
    private void onMousePress(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        if (windowPointer != Minecraft.getInstance().getWindow().getWindow()) return;
        InputConstants.Key inputKey = InputConstants.Type.MOUSE.getOrCreate(button);
        KeyHandler.onRawInput(inputKey, -1, action, modifiers);
    }
}
