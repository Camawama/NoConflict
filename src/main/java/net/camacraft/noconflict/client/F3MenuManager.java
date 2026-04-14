package net.camacraft.noconflict.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class F3MenuManager {

    public static class F3ActionMapping extends KeyMapping {
        private final int targetKey;

        public F3ActionMapping(String name, int targetKey) {
            super(name, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "F3 Actions");
            this.targetKey = targetKey;
        }

        public int getTargetKey() {
            return targetKey;
        }
    }

    private static final List<KeyMapping> F3_MAPPINGS = new ArrayList<>();

    static {
        F3_MAPPINGS.add(new F3ActionMapping("Reload Chunks (A)", GLFW.GLFW_KEY_A));
        F3_MAPPINGS.add(new F3ActionMapping("Show Hitboxes (B)", GLFW.GLFW_KEY_B));
        F3_MAPPINGS.add(new F3ActionMapping("Copy Location (C)", GLFW.GLFW_KEY_C));
        F3_MAPPINGS.add(new F3ActionMapping("Clear Chat (D)", GLFW.GLFW_KEY_D));
        F3_MAPPINGS.add(new F3ActionMapping("Chunk Borders (G)", GLFW.GLFW_KEY_G));
        F3_MAPPINGS.add(new F3ActionMapping("Advanced Tooltips (H)", GLFW.GLFW_KEY_H));
        F3_MAPPINGS.add(new F3ActionMapping("Copy Entity Data (I)", GLFW.GLFW_KEY_I));
        F3_MAPPINGS.add(new F3ActionMapping("Spectator Mode (N)", GLFW.GLFW_KEY_N));
        F3_MAPPINGS.add(new F3ActionMapping("Pause on Lost Focus (P)", GLFW.GLFW_KEY_P));
        F3_MAPPINGS.add(new F3ActionMapping("Show Debug Profiler (Q)", GLFW.GLFW_KEY_Q));
        F3_MAPPINGS.add(new F3ActionMapping("Reload Resource Packs (T)", GLFW.GLFW_KEY_T));
        F3_MAPPINGS.add(new F3ActionMapping("Gamemode Switcher (F4)", GLFW.GLFW_KEY_F4));
        F3_MAPPINGS.add(new F3ActionMapping("Frame Time Graph (Alt)", GLFW.GLFW_KEY_LEFT_ALT));
    }

    public static List<KeyMapping> getMappings() {
        return F3_MAPPINGS;
    }
}
