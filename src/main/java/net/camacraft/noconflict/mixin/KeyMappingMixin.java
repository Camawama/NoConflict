package net.camacraft.noconflict.mixin;

import net.camacraft.noconflict.client.KeyMappingExtensions;
import net.camacraft.noconflict.client.RadialAction;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin implements KeyMappingExtensions, RadialAction {
    @Shadow
    private int clickCount;

    @Shadow
    public abstract String getName();

    @Override
    public void incrementClickCounter() {
        this.clickCount++;
    }

    @Override
    public String getActionName() {
        return getName();
    }
}
