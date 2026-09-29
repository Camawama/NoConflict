package net.camacraft.noconflict.client;

import org.lwjgl.glfw.GLFW;

import java.util.List;

public class F3MenuManager {

    /**
     * A debug-screen action shown on the F3 radial menu. Deliberately NOT a KeyMapping:
     * constructing KeyMappings would register ghost entries in vanilla's static maps.
     */
    public static class F3Action implements RadialAction {
        private final String translationKey;
        private final int targetKey;

        public F3Action(String translationKey, int targetKey) {
            this.translationKey = translationKey;
            this.targetKey = targetKey;
        }

        @Override
        public String getActionName() {
            return translationKey;
        }

        public int getTargetKey() {
            return targetKey;
        }
    }

    private static final List<RadialAction> F3_ACTIONS = List.of(
            new F3Action("noconflict.f3.reload_chunks", GLFW.GLFW_KEY_A),
            new F3Action("noconflict.f3.show_hitboxes", GLFW.GLFW_KEY_B),
            new F3Action("noconflict.f3.copy_location", GLFW.GLFW_KEY_C),
            new F3Action("noconflict.f3.clear_chat", GLFW.GLFW_KEY_D),
            new F3Action("noconflict.f3.chunk_borders", GLFW.GLFW_KEY_G),
            new F3Action("noconflict.f3.advanced_tooltips", GLFW.GLFW_KEY_H),
            new F3Action("noconflict.f3.copy_entity_data", GLFW.GLFW_KEY_I),
            new F3Action("noconflict.f3.spectator", GLFW.GLFW_KEY_N),
            new F3Action("noconflict.f3.pause_on_lost_focus", GLFW.GLFW_KEY_P),
            new F3Action("noconflict.f3.f3_help", GLFW.GLFW_KEY_Q),
            new F3Action("noconflict.f3.reload_resource_packs", GLFW.GLFW_KEY_T),
            new F3Action("noconflict.f3.toggle_creative", GLFW.GLFW_KEY_F4),
            new F3Action("noconflict.f3.frame_time_graph", GLFW.GLFW_KEY_LEFT_ALT)
    );

    public static List<RadialAction> getActions() {
        return F3_ACTIONS;
    }
}
