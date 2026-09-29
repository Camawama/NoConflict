package net.camacraft.noconflict;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NoConflict.MODID)
public class NoConflict
{
    public static final String MODID = "noconflict";

    public NoConflict(FMLJavaModLoadingContext context)
    {
        // All behavior is client-side: KeyHandler (Forge bus, Dist.CLIENT) drives initialization
        // on the first client tick, and commands are registered via RegisterClientCommandsEvent.
        context.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }
}
