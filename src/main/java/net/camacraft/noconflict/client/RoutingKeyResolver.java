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
 * Tells Camafy how a mapping No Conflict manages is reached on its key (tap, hold or the radial
 * menu). Mods that ask Camafy (e.g. Binders' keybind overlay, via {@code Keys.displayText}) then
 * show "Hold X" or "X (menu)" without knowing about No Conflict. No Conflict never unbinds a
 * mapping, so its own key is always the physical one.
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
        // Only mappings a tap, hold or the radial menu actually reaches; not one silenced under UNBIND for
        // sharing a key with an ignored keybind (nothing reaches it), nor while the controls screen is open.
        if (key == null || !ConflictManager.isRoutedOn(key, mapping)) return null;
        if (mapping == ConflictManager.getSelection(key)) return KeyRoute.tap(key);
        if (mapping == ConflictManager.getHoldSelection(key)) return KeyRoute.hold(key);
        return KeyRoute.menu(key);
    }

    /** A delivered tap stays down until the end of the tick, after the physical key is up. */
    @Override
    public boolean holdsDown(KeyMapping mapping) {
        return KeyHandler.isSyntheticTapOf(mapping);
    }
}
