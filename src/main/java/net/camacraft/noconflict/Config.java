package net.camacraft.noconflict;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = NoConflict.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public enum DefaultHoldBehavior {
        DO_NOTHING, TRIGGER_ALL
    }

    // General
    public static final ForgeConfigSpec.BooleanValue F3_RADIAL_MENU;
    public static final ForgeConfigSpec.IntValue HOLD_TIME;
    public static final ForgeConfigSpec.IntValue DOUBLE_TAP_TIME;
    public static final ForgeConfigSpec.EnumValue<DefaultHoldBehavior> DEFAULT_HOLD_BEHAVIOR;

    // Filters & Defaults
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_CATEGORIES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_KEYBINDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEFAULT_SELECTIONS;

    // Radial Menu
    public static final ForgeConfigSpec.IntValue RADIUS;
    public static final ForgeConfigSpec.DoubleValue BACKGROUND_OPACITY;
    public static final ForgeConfigSpec.BooleanValue ALLOW_GUI_OVERLAY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> GUI_OVERLAY_BLACKLIST;

    // Radial Menu Visuals
    public static final ForgeConfigSpec.DoubleValue TEXT_SCALE;
    public static final ForgeConfigSpec.DoubleValue ANIMATION_SPEED;
    public static final ForgeConfigSpec.ConfigValue<String> SEPARATOR_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> REGULAR_ITEM_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> HOVERED_ITEM_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> SELECTED_ITEM_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> SELECTED_HOVERED_ITEM_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> HOLD_INDICATOR_COLOR;


    // Radial Menu Audio
    public static final ForgeConfigSpec.BooleanValue TOGGLE_SOUND;
    public static final ForgeConfigSpec.DoubleValue TOGGLE_VOLUME;


    static {
        BUILDER.push("General");

        F3_RADIAL_MENU = BUILDER
                .comment("Whether holding the F3 key should open a radial menu with debug actions.")
                .define("f3RadialMenu", true);

        HOLD_TIME = BUILDER
                .comment("The time in milliseconds a key must be held down to open the radial menu when in tap mode. If a key is pressed quickly (less than this time), it triggers the action.")
                .defineInRange("holdTime", 200, 0, 2000);
                
        DOUBLE_TAP_TIME = BUILDER
                .comment("The maximum time in milliseconds between two taps to register as a double tap. In hold mode, the user must double tap the key and hold on the second tap to access the radial menu.")
                .defineInRange("doubleTapTime", 300, 50, 1000);

        DEFAULT_HOLD_BEHAVIOR = BUILDER
                .comment("What should happen when a conflicted key is held down, but no master keybind is set for it. DO_NOTHING is recommended.")
                .defineEnum("defaultHoldBehavior", DefaultHoldBehavior.DO_NOTHING);

        BUILDER.pop();

        BUILDER.push("Filters & Defaults");

        IGNORED_CATEGORIES = BUILDER
                .comment("A list of keybind categories to ignore conflicts for. If ANY conflicting keybind belongs to one of these categories, the radial menu will NOT open and the game will handle the keys normally.")
                .defineList("ignoredCategories", List.of("key.categories.movement"), obj -> obj instanceof String);

        IGNORED_KEYBINDS = BUILDER
                .comment("A list of specific keybind names to ignore conflicts for. For example, 'key.attack' for left click, or 'key.use' for right click.")
                .defineList("ignoredKeybinds", List.of("key.attack", "key.use"), obj -> obj instanceof String);

        DEFAULT_SELECTIONS = BUILDER
                .comment("A list of default selections for conflicting keybinds when the game loads. Format: 'trigger_key:mode:keybind_name'. Mode is either 'tap' or 'hold'. E.g., 'key.keyboard.1:tap:key.hotbar.1'")
                .defineList("defaultSelections", List.of(), obj -> obj instanceof String);

        BUILDER.pop();

        BUILDER.push("Radial Menu");

        RADIUS = BUILDER
                .comment("The base radius (distance from the center) of the options in the radial menu.")
                .defineInRange("radius", 80, 20, 300);

        BACKGROUND_OPACITY = BUILDER
                .comment("The opacity/darkness of the background when the radial menu is open (0.0 = transparent, 1.0 = fully black).")
                .defineInRange("backgroundOpacity", 0.5, 0.0, 1.0);

        ALLOW_GUI_OVERLAY = BUILDER
                .comment("Whether the radial menu should be allowed to open and overlay on top of other GUIs (like the Inventory).")
                .define("allowGuiOverlay", false);

        GUI_OVERLAY_BLACKLIST = BUILDER
                .comment("A list of screen class names where the radial menu is NOT allowed to open as an overlay. You can use simple names (e.g., 'TitleScreen') or fully qualified names.")
                .defineList("guiOverlayBlacklist", List.of("TitleScreen", "OptionsScreen", "KeyBindsScreen", "PauseScreen"), obj -> obj instanceof String);

        BUILDER.pop();

        BUILDER.push("Radial Menu Visuals");

        TEXT_SCALE = BUILDER
                .comment("The scale of the text for the options in the radial menu.")
                .defineInRange("textScale", 1.0, 0.5, 3.0);

        ANIMATION_SPEED = BUILDER
                .comment("Controls the speed of hover animations in the radial menu.")
                .defineInRange("animationSpeed", 7.0, 1.0, 20.0);

        SEPARATOR_COLOR = BUILDER
                .comment("The color of the separator lines in the radial menu (ARGB hex format).")
                .define("separatorColor", "44FFFFFF");

        REGULAR_ITEM_COLOR = BUILDER
                .comment("The color for a regular (non-selected) item.")
                .define("regularItemColor", "FFFFFFFF"); // White

        HOVERED_ITEM_COLOR = BUILDER
                .comment("The color for a regular item when hovered.")
                .define("hoveredItemColor", "FFFFFF00"); // Yellow

        SELECTED_ITEM_COLOR = BUILDER
                .comment("The color for a selected item.")
                .define("selectedItemColor", "FF00AAAA"); // Dark Cyan

        SELECTED_HOVERED_ITEM_COLOR = BUILDER
                .comment("The color for a selected item when hovered.")
                .define("selectedHoveredItemColor", "FF55FFFF"); // Cyan
        
        HOLD_INDICATOR_COLOR = BUILDER
                .comment("The color of the '(Hold)' text indicator.")
                .define("holdIndicatorColor", "FF55FF55"); // Green

        BUILDER.pop();

        BUILDER.push("Radial Menu Audio");

        TOGGLE_SOUND = BUILDER
                .comment("Whether a sound should play when an item is selected or toggled.")
                .define("toggleSound", true);

        TOGGLE_VOLUME = BUILDER
                .comment("The volume of the toggle sound (0.0 = silent, 1.0 = full volume).")
                .defineInRange("toggleVolume", 0.5, 0.0, 1.0);

        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int getIntColor(ForgeConfigSpec.ConfigValue<String> colorValue) {
        try {
            return (int) Long.parseLong(colorValue.get(), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF; // Default to white on error
        }
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
    }
}
