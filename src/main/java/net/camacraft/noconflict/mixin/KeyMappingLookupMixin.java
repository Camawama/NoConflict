package net.camacraft.noconflict.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.client.KeyRouting;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyMappingLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Forge's {@code KeyMappingLookup.getAll}: the mappings a key press reaches, for vanilla's {@code KeyMapping.click}
 * and {@code set}. Mappings the key isn't routed to are taken out ({@link KeyRouting}). {@code KeyMappingMixin}'s
 * {@code isActiveAndMatches} does the same, but another mod may already own that method (Controllable overwrites it,
 * and Mixin then skips ours: in the owner's pack a tap meant for VMod's toolgun menu also held the player list down),
 * so vanilla's own dispatch is filtered here, where nothing else competes.
 */
@Mixin(value = KeyMappingLookup.class, remap = false)
public abstract class KeyMappingLookupMixin {
    @Inject(method = "getAll(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Ljava/util/List;", at = @At("RETURN"), remap = false)
    private void noconflict$routedOnly(InputConstants.Key key, CallbackInfoReturnable<List<KeyMapping>> cir) {
        List<KeyMapping> found = cir.getReturnValue();
        if (found != null && !found.isEmpty()) found.removeIf(mapping -> !KeyRouting.passes(mapping));
    }
}
