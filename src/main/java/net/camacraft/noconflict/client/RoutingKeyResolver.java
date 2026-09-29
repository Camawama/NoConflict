package net.camacraft.noconflict.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.camafy.client.input.KeyResolver;
import net.camacraft.camafy.client.input.KeyResolvers;
import net.camacraft.camafy.client.input.KeyRoute;
import net.camacraft.noconflict.KeyHandler;
import net.camacraft.noconflict.NoConflict;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import javax.annotation.Nullable;

/**
 * Tells Camafy which physical key really drives a mapping No Conflict has unbound, and how
 * (tap, hold or radial menu). Mods that ask Camafy (e.g. Binders' keybind overlay, via
 * {@code Keys.displayText}) then show the right key without knowing about No Conflict.
 * {@link net.camacraft.noconflict.api.NoConflictApi} stays for mods that query No Conflict directly.
 */
@Mod.EventBusSubscriber(modid = NoConflict.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RoutingKeyResolver implements KeyResolver {
    public static final ResourceLocation ID = new ResourceLocation(NoConflict.MODID, "routing");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        KeyResolvers.register(ID, new RoutingKeyResolver());
    }

    @Nullable
    @Override
    public KeyRoute resolve(KeyMapping mapping) {
        InputConstants.Key key = ConflictManager.getOriginalKey(mapping);
        // Only mappings a tap, hold or the radial menu actually reaches. Not one the user unbound
        // (dropped from management when the controls screen closes), nor one UNBIND suppressed for
        // sharing a key with an ignored keybind: nothing routes to those, so they read "Not Bound".
        if (key == null || !ConflictManager.isRoutedOn(key, mapping)) return null;
        if (mapping == ConflictManager.getSelection(key)) return KeyRoute.tap(key);
        if (mapping == ConflictManager.getHoldSelection(key)) return KeyRoute.hold(key);
        return KeyRoute.menu(key);
    }

    /** A synthetic tap stays down until the end of the next tick, after the physical key is up. */
    @Override
    public boolean holdsDown(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        return !key.equals(InputConstants.UNKNOWN)
                && KeyHandler.isSyntheticTapActive(key)
                && ConflictManager.isManagedMapping(mapping);
    }
}
