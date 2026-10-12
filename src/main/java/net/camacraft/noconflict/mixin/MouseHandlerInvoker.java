package net.camacraft.noconflict.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets No Conflict replay a mouse button press through vanilla's own handler. */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onPress")
    void noconflict$invokeOnPress(long windowPointer, int button, int action, int modifiers);
}
