package net.camacraft.noconflict.mixin;

import net.camacraft.noconflict.client.ConflictManager;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class OptionsMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void onSaveHead(CallbackInfo ci) {
        ConflictManager.onSaveStart();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void onSaveReturn(CallbackInfo ci) {
        ConflictManager.onSaveEnd();
    }
}
