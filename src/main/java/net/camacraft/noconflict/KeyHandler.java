package net.camacraft.noconflict;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.camacraft.noconflict.client.ConflictManager;
import net.camacraft.noconflict.client.F3MenuManager;
import net.camacraft.noconflict.client.KeyMappingExtensions;
import net.camacraft.noconflict.client.RadialAction;
import net.camacraft.noconflict.client.gui.RadialMenuScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = NoConflict.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final InputConstants.Key F3_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F3);

    private static final Map<InputConstants.Key, Long> keyPressTimes = new HashMap<>();
    private static final Map<InputConstants.Key, Boolean> pressedInGui = new HashMap<>();

    // Keys whose hold selection is currently being actively held down
    private static final Set<InputConstants.Key> holdActiveKeys = new HashSet<>();

    // To track double taps
    private static final Map<InputConstants.Key, Long> lastReleaseTimes = new HashMap<>();

    // To optionally trigger a tap action when the radial menu closes
    private static InputConstants.Key pendingTapKey = null;
    private static RadialAction pendingTapSelection = null;

    // Synthetic taps held down until the end of the next full tick, so mods that poll
    // KeyMapping#isDown() in a tick handler (e.g. movement-ability mods) observe the tap.
    private static final Map<InputConstants.Key, PendingTapRelease> pendingTapReleases = new HashMap<>();

    private record PendingTapRelease(KeyMapping mapping, int scanCode, int modifiers) {}

    // Ensures ConflictManager applies resolutions on the very first game tick after all mods load
    private static boolean initialized = false;

    // Cached reflection method for vanilla's internal F3 action handler
    private static Method handleDebugKeysMethod = null;
    private static boolean debugKeysLookupFailed = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            releasePendingTaps();
            return;
        }

        if (!initialized) {
            initialized = true;
            ConflictManager.initialize();
        }

        // Continuously enforce resolutions in case vanilla or other mods overwrite the key map behind our back
        ConflictManager.enforceResolutions();

        Minecraft mc = Minecraft.getInstance();

        // Check if we need to trigger a pending tap action from a closed menu
        if (pendingTapKey != null && pendingTapSelection != null && !(mc.screen instanceof RadialMenuScreen)) {
            executePendingTap(mc);
            pendingTapKey = null;
            pendingTapSelection = null;
        }

        if (mc.screen instanceof RadialMenuScreen) {
            keyPressTimes.clear();
            pressedInGui.clear();
            holdActiveKeys.clear();
            return;
        }

        if (keyPressTimes.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        InputConstants.Key keyToOpen = null;

        for (Map.Entry<InputConstants.Key, Long> entry : keyPressTimes.entrySet()) {
            InputConstants.Key key = entry.getKey();
            long pressTime = entry.getValue();
            long duration = currentTime - pressTime;

            boolean isF3 = isF3RadialKey(key);
            List<KeyMapping> activeMappings = ConflictManager.getActiveMappings(key);
            boolean hasActiveConflict = !activeMappings.isEmpty();

            if (duration > Config.HOLD_TIME.get() && (hasActiveConflict || isF3)) {

                if (mc.screen != null && !isOverlayAllowed(mc.screen)) {
                    continue;
                }

                if (isF3) {
                    keyToOpen = key;
                    break;
                }

                KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
                boolean isDoubleTapHold = wasDoubleTapped(key, pressTime);

                if (holdSelection != null && !isDoubleTapHold) {
                    // Long-press activates the hold selection (whether or not it matches the tap
                    // selection). The radial menu is reached via double-tap-and-hold instead.
                    if (mc.screen == null && !holdSelection.isDown()) {
                        activateHold(key, holdSelection);
                    }
                } else {
                    keyToOpen = key;
                    break;
                }
            }
        }

        if (keyToOpen != null) {
            keyPressTimes.remove(keyToOpen);
            pressedInGui.remove(keyToOpen);
            holdActiveKeys.remove(keyToOpen);

            KeyMapping tapSelection = ConflictManager.getSelection(keyToOpen);
            if (tapSelection != null) {
                tapSelection.setKey(InputConstants.UNKNOWN);
            }

            if (isF3RadialKey(keyToOpen)) {
                mc.setScreen(new RadialMenuScreen(F3MenuManager.getActions(), keyToOpen, mc.screen, false));
            } else {
                KeyMapping holdSelection = ConflictManager.getHoldSelection(keyToOpen);
                if (holdSelection != null) {
                    holdSelection.setDown(false);
                    holdSelection.setKey(InputConstants.UNKNOWN);
                }

                List<KeyMapping> mappings = ConflictManager.getActiveMappings(keyToOpen);
                mc.setScreen(RadialMenuScreen.forKeyMappings(mappings, keyToOpen, mc.screen, true));
            }
        }
    }

    private static boolean isOverlayAllowed(Screen screen) {
        if (!Config.ALLOW_GUI_OVERLAY.get()) return false;

        // Swapping away from a server-backed container screen closes the container server-side,
        // so restoring it afterwards would leave a dead GUI. The player inventory is client-safe.
        if (screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)) {
            return false;
        }

        String simpleName = screen.getClass().getSimpleName();
        String fullName = screen.getClass().getName();
        List<? extends String> blacklist = Config.GUI_OVERLAY_BLACKLIST.get();
        return !blacklist.contains(simpleName) && !blacklist.contains(fullName);
    }

    private static void executePendingTap(Minecraft mc) {
        InputConstants.Key key = pendingTapKey;

        if (pendingTapSelection instanceof F3MenuManager.F3Action f3Action) {
            executeF3Action(mc, f3Action.getTargetKey());
            return;
        }

        KeyMapping mapping = (KeyMapping) pendingTapSelection;

        if (mc.screen != null && key.getType() == InputConstants.Type.KEYSYM) {
            // Let the restored parent screen process the key natively (e.g. closing the inventory)
            mapping.setKey(key);
            mc.tell(() -> {
                if (mc.screen != null) {
                    mc.screen.keyPressed(key.getValue(), 0, 0);
                    if (mc.screen != null) {
                        mc.screen.keyReleased(key.getValue(), 0, 0);
                    } else {
                        while (mapping.consumeClick()) {}
                    }
                }
                postSyntheticTap(key, 0, 0);
                mapping.setKey(InputConstants.UNKNOWN);
            });
        } else {
            // No screen, or a mouse trigger key (mouse buttons must not be fed to Screen#keyPressed)
            mapping.setKey(key);
            ((KeyMappingExtensions) mapping).incrementClickCounter();
            mapping.setDown(true);
            postSyntheticEvent(key, 0, InputConstants.PRESS, 0);
            pendingTapReleases.put(key, new PendingTapRelease(mapping, 0, 0));
        }
    }

    private static void executeF3Action(Minecraft mc, int targetKey) {
        if (targetKey == GLFW.GLFW_KEY_LEFT_ALT) {
            // Vanilla F3+Alt toggles the frame time graph; mirror the toggle instead of forcing it on
            boolean show = !mc.options.renderFpsChart;
            mc.options.renderDebug = show;
            mc.options.renderFpsChart = show;
            return;
        }

        mc.tell(() -> {
            try {
                if (targetKey == GLFW.GLFW_KEY_F4 && mc.gameMode != null && mc.player != null) {
                    // The vanilla gamemode switcher instantly closes without applying if the physical
                    // F3 key is not actively held down, so we toggle via command instead (requires OP).
                    mc.player.connection.sendCommand("gamemode " + (mc.player.isCreative() ? "survival" : "creative"));
                    return;
                }

                Method method = findHandleDebugKeys();
                if (method != null) {
                    method.invoke(mc.keyboardHandler, targetKey);
                } else {
                    // Last-resort fallbacks for the few actions we can replicate directly
                    if (targetKey == GLFW.GLFW_KEY_T) mc.reloadResourcePacks();
                    else if (targetKey == GLFW.GLFW_KEY_A) mc.levelRenderer.allChanged();
                    else if (targetKey == GLFW.GLFW_KEY_D) mc.gui.getChat().clearMessages(false);
                    else LOGGER.warn("NoConflict: cannot execute F3 action for key {} (handleDebugKeys unavailable)", targetKey);
                }
            } catch (Exception e) {
                LOGGER.error("NoConflict: failed to execute F3 action for key {}", targetKey, e);
            }
        });
    }

    private static Method findHandleDebugKeys() {
        if (handleDebugKeysMethod == null && !debugKeysLookupFailed) {
            try {
                handleDebugKeysMethod = ObfuscationReflectionHelper.findMethod(KeyboardHandler.class, "m_90932_", int.class);
            } catch (Exception e) {
                debugKeysLookupFailed = true;
                LOGGER.warn("NoConflict: could not resolve KeyboardHandler#handleDebugKeys; F3 radial actions will use limited fallbacks", e);
            }
        }
        return handleDebugKeysMethod;
    }

    private static void activateHold(InputConstants.Key key, KeyMapping holdSelection) {
        holdSelection.setKey(key);
        holdSelection.setDown(true);
        ((KeyMappingExtensions) holdSelection).incrementClickCounter();
        holdActiveKeys.add(key);
        postSyntheticEvent(key, 0, InputConstants.PRESS, 0);
    }

    /** Posts a synthetic PRESS followed by a RELEASE so mods listening for either see the tap. */
    private static void postSyntheticTap(InputConstants.Key key, int scanCode, int modifiers) {
        postSyntheticEvent(key, scanCode, InputConstants.PRESS, modifiers);
        postSyntheticEvent(key, scanCode, InputConstants.RELEASE, modifiers);
    }

    /**
     * Runs at tick END, after tick handlers have observed isDown() for one full tick, and releases
     * every synthetic tap armed since the previous tick. enforceResolutions() skips these keys
     * while a release is pending so the temporary binding isn't stomped mid-tap.
     */
    private static void releasePendingTaps() {
        if (pendingTapReleases.isEmpty()) return;
        for (Map.Entry<InputConstants.Key, PendingTapRelease> entry : pendingTapReleases.entrySet()) {
            PendingTapRelease pending = entry.getValue();
            pending.mapping().setDown(false);
            postSyntheticEvent(entry.getKey(), pending.scanCode(), InputConstants.RELEASE, pending.modifiers());
            pending.mapping().setKey(InputConstants.UNKNOWN);
        }
        pendingTapReleases.clear();
    }

    public static boolean isSyntheticTapActive(InputConstants.Key key) {
        return pendingTapReleases.containsKey(key);
    }

    private static void postSyntheticEvent(InputConstants.Key key, int scanCode, int action, int modifiers) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            MinecraftForge.EVENT_BUS.post(new InputEvent.MouseButton.Post(key.getValue(), action, modifiers));
        } else {
            MinecraftForge.EVENT_BUS.post(new InputEvent.Key(key.getValue(), scanCode, action, modifiers));
        }
    }

    public static void setPendingTapAction(InputConstants.Key key, RadialAction action) {
        pendingTapKey = key;
        pendingTapSelection = action;
    }

    private static boolean wasDoubleTapped(InputConstants.Key key, long pressTime) {
        Long lastRelease = lastReleaseTimes.get(key);
        if (lastRelease != null) {
            long gap = pressTime - lastRelease;
            return gap < Config.DOUBLE_TAP_TIME.get();
        }
        return false;
    }

    private static boolean isF3RadialKey(InputConstants.Key key) {
        return Config.F3_RADIAL_MENU.get()
                && key.getType() == InputConstants.Type.KEYSYM
                && key.getValue() == GLFW.GLFW_KEY_F3;
    }

    /** A keyboard key other than F3 pressed while F3 is physically held: vanilla reads it as an F3+key combo. */
    private static boolean isF3ComboKey(Minecraft mc, InputConstants.Key key) {
        return key.getType() != InputConstants.Type.MOUSE
                && !key.equals(F3_KEY)
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F3);
    }

    public static boolean isPressedInGui(InputConstants.Key key) {
        return pressedInGui.getOrDefault(key, false);
    }

    public static boolean isHoldActive(InputConstants.Key key) {
        return holdActiveKeys.contains(key);
    }

    /**
     * The single raw-input entry point, called from KeyboardHandlerMixin / MouseHandlerMixin at the
     * HEAD of vanilla's input handlers (i.e. before vanilla or any screen processes the input).
     */
    public static void onRawInput(InputConstants.Key key, int scanCode, int action, int modifiers) {
        Minecraft mc = Minecraft.getInstance();

        if (action == InputConstants.PRESS) {
            boolean isF3 = isF3RadialKey(key);

            // With F3 held, this press is the second half of a debug combo (vanilla's F3+T, Full Stop's
            // F3+V, ...), which vanilla dispatches itself: don't also tap or hold the key's routed
            // selection, and don't let the held F3 go on to open its radial menu.
            if (!isF3 && isF3ComboKey(mc, key)) {
                keyPressTimes.remove(F3_KEY);
                pressedInGui.remove(F3_KEY);
                return;
            }

            List<KeyMapping> activeMappings = ConflictManager.getActiveMappings(key);
            boolean hasActiveConflict = !activeMappings.isEmpty();

            if (!hasActiveConflict && !isF3) return;
            if (mc.screen instanceof RadialMenuScreen) return;

            long currentTime = System.currentTimeMillis();
            keyPressTimes.put(key, currentTime);
            pressedInGui.put(key, mc.screen != null);

            if (isF3) return;

            KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
            KeyMapping tapSelection = ConflictManager.getSelection(key);

            // When the hold selection IS the tap selection, the keybind is hold-styled (e.g. Create
            // toolbox menus): activate it immediately on press so it behaves like a native binding.
            if (mc.screen == null && holdSelection != null && holdSelection == tapSelection
                    && !wasDoubleTapped(key, currentTime) && !holdSelection.isDown()) {
                activateHold(key, holdSelection);
            }

            if (mc.screen != null && tapSelection != null) {
                try {
                    if (tapSelection.getKeyConflictContext().isActive()) {
                        // Temporarily rebind so the open screen can process the key natively;
                        // unbound again on release. enforceResolutions() skips this key meanwhile.
                        tapSelection.setKey(key);
                    }
                } catch (Exception e) {
                    LOGGER.debug("NoConflict: key conflict context threw while checking isActive", e);
                }
            }
            // If nothing is selected and TRIGGER_ALL is active, ConflictManager left the keys natively
            // bound; we only record the press so holding can still open the radial menu.

        } else if (action == InputConstants.RELEASE) {
            long currentTime = System.currentTimeMillis();
            lastReleaseTimes.put(key, currentTime);

            Long pressTime = keyPressTimes.remove(key);
            Boolean wasPressedInGui = pressedInGui.remove(key);
            boolean holdWasActive = holdActiveKeys.remove(key);

            if (isF3RadialKey(key)) {
                return;
            }

            KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
            if (holdSelection != null) {
                holdSelection.setDown(false);
                holdSelection.setKey(InputConstants.UNKNOWN);
                if (holdWasActive) {
                    postSyntheticEvent(key, scanCode, InputConstants.RELEASE, modifiers);
                }
            }

            if (pressTime == null) return;

            long duration = currentTime - pressTime;
            if (duration > Config.HOLD_TIME.get()) return;

            // The press-time hold activation already delivered this action; don't fire it again as a tap
            if (holdWasActive) return;

            KeyMapping selection = ConflictManager.getSelection(key);

            if (selection == null) {
                // Under TRIGGER_ALL, vanilla handled the tap natively; nothing to do or say.
                if (holdSelection == null
                        && Config.DEFAULT_TAP_BEHAVIOR.get() == Config.DefaultTapBehavior.DO_NOTHING
                        && mc.player != null && mc.screen == null) {
                    mc.player.displayClientMessage(
                            Component.translatable("noconflict.message.hold_to_assign").withStyle(ChatFormatting.RED), true);
                }
                return;
            }

            if (wasPressedInGui != null && wasPressedInGui) {
                // The screen already processed the temporarily-bound key; just unbind it afterwards
                mc.tell(() -> selection.setKey(InputConstants.UNKNOWN));
                return;
            }

            try {
                if (!selection.getKeyConflictContext().isActive()) {
                    return;
                }
            } catch (Exception e) {
                LOGGER.debug("NoConflict: key conflict context threw while checking isActive", e);
            }

            selection.setKey(key);
            ((KeyMappingExtensions) selection).incrementClickCounter();
            selection.setDown(true);
            postSyntheticEvent(key, scanCode, InputConstants.PRESS, modifiers);
            pendingTapReleases.put(key, new PendingTapRelease(selection, scanCode, modifiers));
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        NoConflictCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof KeyBindsScreen) {
            ConflictManager.suspend();
        }
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof KeyBindsScreen) {
            ConflictManager.resume();
        }
    }
}
