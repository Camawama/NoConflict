package net.camacraft.noconflict.mixin;

import net.camacraft.noconflict.client.KeyMappingExtensions;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(KeyMapping.class)
public class KeyMappingMixin implements KeyMappingExtensions {
    @Shadow
    private int clickCount;

    @Override
    public void incrementClickCounter() {
        this.clickCount++;
    }
}