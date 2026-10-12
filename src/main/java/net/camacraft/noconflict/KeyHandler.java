package net.camacraft.noconflict;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.camacraft.noconflict.client.ConflictManager;
import net.camacraft.noconflict.client.F3MenuManager;
import net.camacraft.noconflict.client.KeyMappingExtensions;
import net.camacraft.noconflict.client.KeyRouting;
import net.camacraft.noconflict.client.RadialAction;
import net.camacraft.noconflict.client.gui.RadialMenuScreen;
import net.camacraft.noconflict.mixin.MouseHandlerInvoker;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a press of a key with conflicting keybinds does: a short tap delivers the tap selection, a long press
 * the hold selection or the radial menu.
 *
 * <p>Nothing is ever unbound (see {@link KeyRouting}): a managed mapping simply doesn't answer to its key
 * unless the key is routed to it. The physical press and release go through vanilla as always (so mods that
 * read raw input in real time keep seeing it), and reach only what they are routed to. A selection that is
 * decided after the physical press (a tap, delivered on release; a hold, at the hold time) is delivered by
 * replaying the press through vanilla's own keyboard or mouse handler with the key routed to it, so every
 * way a mod listens (its {@code KeyMapping}'s clicks and held state, {@code matches} in a raw key listener or
 * a mixin at the head of {@code KeyboardHandler.keyPress}, Forge's input events) sees one press of its key,
 * and nothing else on that key sees it at all.
 */
@Mod.EventBusSubscriber(modid = NoConflict.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final InputConstants.Key F3_KEY = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F3);

    /**
     * Keys vanilla's keyboard handler acts on by their code, not through a KeyMapping (Esc pauses, F1 hides the
     * HUD, F3 toggles the debug screen, F4 the post effect): replaying a press of one would do that a second
     * time, so a selection on one of these is delivered to its mapping directly (clicks, held state, Forge's
     * input event) instead.
     */
    private static final Set<Integer> HARD_WIRED = Set.of(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_F1, GLFW.GLFW_KEY_F3, GLFW.GLFW_KEY_F4);

    /** A physical input event, kept to replay it: the key code (or mouse button), scancode and modifiers. */
    private record PressEvent(int code, int scanCode, int modifiers) {}

    private static final Map<InputConstants.Key, Long> keyPressTimes = new HashMap<>();
    private static final Map<InputConstants.Key, PressEvent> pressEvents = new HashMap<>();
    private static final Map<InputConstants.Key, Boolean> pressedInGui = new HashMap<>();

    // Keys whose hold selection is routed and held right now
    private static final Set<InputConstants.Key> holdActiveKeys = new HashSet<>();

    // To track double taps
    private static final Map<InputConstants.Key, Long> lastReleaseTimes = new HashMap<>();

    // To optionally trigger a tap action when the radial menu closes
    private static InputConstants.Key pendingTapKey = null;
    private static RadialAction pendingTapSelection = null;

    // Taps delivered (pressed) and held until the end of the tick, so mods that poll KeyMapping#isDown()
    // in a tick handler observe them; released (replayed) at tick end.
    private static final Map<InputConstants.Key, PendingTapRelease> pendingTapReleases = new HashMap<>();

    /** A delivered tap; {@code ticked} once a client tick has started since (it is released at that tick's end). */
    private static final class PendingTapRelease {
        final KeyMapping mapping;
        final PressEvent event;
        boolean ticked;

        PendingTapRelease(KeyMapping mapping, PressEvent event) {
            this.mapping = mapping;
            this.event = event;
        }

        KeyMapping mapping() {
            return mapping;
        }

        PressEvent event() {
            return event;
        }
    }

    // What waits until vanilla has dealt with the physical event that caused it (the RETURN of its handler)
    private static final Set<InputConstants.Key> unrouteAfterInput = new LinkedHashSet<>();
    private static final List<Runnable> afterInput = new ArrayList<>();

    // Ensures ConflictManager scans on the very first game tick after all mods load
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

        // every tap delivered so far is down for this whole tick, and released at its end
        for (PendingTapRelease pending : pendingTapReleases.values()) pending.ticked = true;

        // A binding changed behind our back (another controls screen, a mod rebinding at runtime): rescan
        ConflictManager.checkForRebinds();

        // Whatever a RETURN hook didn't get to (another mod cancelled the input event first)
        afterRawInput();
        sweepStaleRoutes();

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

        for (Map.Entry<InputConstants.Key, Long> entry : new ArrayList<>(keyPressTimes.entrySet())) {
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

                if (holdActiveKeys.contains(key)) continue; // already held as its hold selection

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
            pressEvents.remove(keyToOpen);
            pressedInGui.remove(keyToOpen);
            holdActiveKeys.remove(keyToOpen);

            // Whatever the key was routed to (a press inside a screen, a hold) lets go before the menu opens
            KeyMapping routed = KeyRouting.routedTo(keyToOpen);
            if (routed != null) {
                routed.setDown(false);
                KeyRouting.unroute(keyToOpen, routed);
            }

            if (isF3RadialKey(keyToOpen)) {
                mc.setScreen(new RadialMenuScreen(F3MenuManager.getActions(), keyToOpen, mc.screen, false));
            } else {
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
        PressEvent press = syntheticPress(key);
        finishPendingTap(key);

        if (mc.screen != null && key.getType() != InputConstants.Type.MOUSE) {
            // The restored parent screen processes the key for the selection (e.g. closing the inventory)
            KeyRouting.route(key, mapping);
            replay(key, mapping, press, InputConstants.PRESS);
            replay(key, mapping, press, InputConstants.RELEASE);
            KeyRouting.unroute(key, mapping);
        } else {
            // No screen, or a mouse trigger key (mouse buttons are not fed to the restored screen)
            deliverTap(key, mapping, press);
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

    /** The hold selection takes the key now: routed to it, and the press it missed replayed to it. */
    private static void activateHold(InputConstants.Key key, KeyMapping holdSelection) {
        PressEvent press = pressEvents.getOrDefault(key, syntheticPress(key));
        KeyRouting.route(key, holdSelection);
        holdActiveKeys.add(key);
        replay(key, holdSelection, press, InputConstants.PRESS);
    }

    /** A tap for {@code mapping}: its press now, its release at the end of the tick. */
    private static void deliverTap(InputConstants.Key key, KeyMapping mapping, PressEvent press) {
        finishPendingTap(key);
        KeyRouting.route(key, mapping);
        replay(key, mapping, press, InputConstants.PRESS);
        pendingTapReleases.put(key, new PendingTapRelease(mapping, press));
    }

    /**
     * Runs at tick END and releases every tap that has been down for a whole tick (delivered before this tick
     * started), so tick handlers polling isDown() see it once. A tap delivered during this tick (from another mod's
     * tick handler, or a test's) waits for the next one.
     */
    private static void releasePendingTaps() {
        if (pendingTapReleases.isEmpty()) return;
        for (Map.Entry<InputConstants.Key, PendingTapRelease> entry : new ArrayList<>(pendingTapReleases.entrySet())) {
            if (entry.getValue().ticked) finishPendingTap(entry.getKey());
        }
    }

    /** Releases a delivered tap of this key, if one is still held, and ends its route. */
    private static void finishPendingTap(InputConstants.Key key) {
        PendingTapRelease pending = pendingTapReleases.remove(key);
        if (pending == null) return;
        replay(key, pending.mapping(), pending.event(), InputConstants.RELEASE);
        pending.mapping().setDown(false);
        KeyRouting.unroute(key, pending.mapping());
    }

    /**
     * One input event replayed through vanilla's own handler, for the mapping the key is routed to. While it
     * runs only that mapping answers to the key (KeyRouting), and No Conflict's own hooks let it through.
     */
    private static void replay(InputConstants.Key key, KeyMapping mapping, PressEvent press, int action) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        boolean mouse = key.getType() == InputConstants.Type.MOUSE;
        KeyRouting.beginReplay(key);
        try {
            if (mouse && mc.screen == null) {
                ((MouseHandlerInvoker) mc.mouseHandler).noconflict$invokeOnPress(window, key.getValue(), action, press.modifiers());
            } else if (!mouse && !HARD_WIRED.contains(press.code())) {
                mc.keyboardHandler.keyPress(window, press.code(), press.scanCode(), action, press.modifiers());
            } else {
                deliverDirectly(key, mapping, press, action);
            }
        } catch (RuntimeException e) {
            LOGGER.error("NoConflict: replaying {} of {} for {} failed", action == InputConstants.PRESS ? "a press" : "a release",
                    key.getName(), mapping.getName(), e);
        } finally {
            KeyRouting.endReplay();
        }
    }

    /** For a key vanilla acts on by code, or a mouse button inside a screen: the mapping's own state and Forge's event. */
    private static void deliverDirectly(InputConstants.Key key, KeyMapping mapping, PressEvent press, int action) {
        if (action == InputConstants.RELEASE) {
            mapping.setDown(false);
        } else {
            ((KeyMappingExtensions) mapping).incrementClickCounter();
            mapping.setDown(true);
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            MinecraftForge.EVENT_BUS.post(new InputEvent.MouseButton.Post(key.getValue(), action, press.modifiers()));
        } else {
            MinecraftForge.EVENT_BUS.post(new InputEvent.Key(press.code(), press.scanCode(), action, press.modifiers()));
        }
    }

    /** A press of this key as GLFW would report it, for a tap that has no physical press of its own (the radial menu). */
    private static PressEvent syntheticPress(InputConstants.Key key) {
        return switch (key.getType()) {
            case KEYSYM -> new PressEvent(key.getValue(), GLFW.glfwGetKeyScancode(key.getValue()), 0);
            case SCANCODE -> new PressEvent(GLFW.GLFW_KEY_UNKNOWN, key.getValue(), 0);
            case MOUSE -> new PressEvent(key.getValue(), -1, 0);
        };
    }

    /**
     * Routes that nothing holds any more (their release never came through our hook): a key routed is either
     * held (tracked press, active hold) or has a tap out.
     */
    private static void sweepStaleRoutes() {
        for (InputConstants.Key key : KeyRouting.routedKeys()) {
            if (holdActiveKeys.contains(key) || pendingTapReleases.containsKey(key) || keyPressTimes.containsKey(key)) continue;
            KeyMapping routed = KeyRouting.routedTo(key);
            if (routed != null && !isPhysicallyDown(key)) {
                routed.setDown(false);
                KeyRouting.unroute(key, routed);
            }
        }
    }

    private static boolean isPhysicallyDown(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            case KEYSYM -> key.getValue() > GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(window, key.getValue());
            case SCANCODE -> false;
        };
    }

    public static boolean isSyntheticTapActive(InputConstants.Key key) {
        return pendingTapReleases.containsKey(key);
    }

    /** Whether a delivered tap holds this mapping down right now (its key may already be up). */
    public static boolean isSyntheticTapOf(KeyMapping mapping) {
        for (PendingTapRelease pending : pendingTapReleases.values()) {
            if (pending.mapping() == mapping) return true;
        }
        return false;
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

    private static boolean safeIsActive(KeyMapping mapping) {
        try {
            return mapping.getKeyConflictContext().isActive();
        } catch (Exception e) {
            LOGGER.debug("NoConflict: key conflict context threw while checking isActive", e);
            return false;
        }
    }

    /**
     * A physical input event, from KeyboardHandlerMixin / MouseHandlerMixin at the HEAD of vanilla's handlers
     * (before vanilla or any screen processes it; No Conflict's own replays never come here). Sets the route
     * the event takes through vanilla; what has to wait for vanilla to finish with it goes to {@link #afterRawInput}.
     */
    public static void onRawInput(InputConstants.Key key, int code, int scanCode, int action, int modifiers) {
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

            // A new press of a key whose last tap is still held: that tap lets go first
            finishPendingTap(key);

            List<KeyMapping> activeMappings = ConflictManager.getActiveMappings(key);
            boolean hasActiveConflict = !activeMappings.isEmpty();

            if (!hasActiveConflict && !isF3) return;
            if (mc.screen instanceof RadialMenuScreen) return;

            long currentTime = System.currentTimeMillis();
            keyPressTimes.put(key, currentTime);
            pressEvents.put(key, new PressEvent(code, scanCode, modifiers));
            pressedInGui.put(key, mc.screen != null);

            if (isF3) return;

            KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
            KeyMapping tapSelection = ConflictManager.getSelection(key);

            // When the hold selection IS the tap selection, the keybind is hold-styled (e.g. Create
            // toolbox menus): this physical press is its own, exactly like a native binding.
            if (mc.screen == null && holdSelection != null && holdSelection == tapSelection
                    && !wasDoubleTapped(key, currentTime) && !holdSelection.isDown()) {
                KeyRouting.route(key, holdSelection);
                holdActiveKeys.add(key);
                return;
            }

            // Inside a screen the press can't wait for the release: the screen gets it (and later its release)
            // for the tap selection, as if that were the only binding on the key.
            if (mc.screen != null && tapSelection != null && safeIsActive(tapSelection)) {
                KeyRouting.route(key, tapSelection);
            }
            // With nothing selected and TRIGGER_ALL every mapping on the key takes it natively (KeyRouting);
            // we only record the press so holding can still open the radial menu.

        } else if (action == InputConstants.RELEASE) {
            long currentTime = System.currentTimeMillis();
            lastReleaseTimes.put(key, currentTime);

            Long pressTime = keyPressTimes.remove(key);
            Boolean wasPressedInGui = pressedInGui.remove(key);
            PressEvent press = pressEvents.remove(key);
            boolean holdWasActive = holdActiveKeys.remove(key);

            if (isF3RadialKey(key)) {
                return;
            }

            if (holdWasActive || (wasPressedInGui != null && wasPressedInGui)) {
                // The route carries this release to the mapping that had the press; it ends once vanilla is done
                // with the release. A hold delivered its action already: no tap on top of it.
                unrouteAfterInput.add(key);
                return;
            }

            if (pressTime == null) return;

            long duration = currentTime - pressTime;
            if (duration > Config.HOLD_TIME.get()) return;

            KeyMapping selection = ConflictManager.getSelection(key);
            KeyMapping holdSelection = ConflictManager.getHoldSelection(key);

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

            if (!safeIsActive(selection)) return;

            // The tap: delivered once vanilla has finished with this release (the hook at its RETURN)
            PressEvent event = press != null ? press : new PressEvent(code, scanCode, modifiers);
            afterInput.add(() -> deliverTap(key, selection, event));
        }
    }

    /**
     * After vanilla has dealt with a physical input event (the RETURN of its handler; and at every tick start for
     * one whose RETURN never came, another mod having cancelled the event): routes that carried a release end,
     * and taps decided on a release are delivered.
     */
    public static void afterRawInput() {
        if (!unrouteAfterInput.isEmpty()) {
            for (InputConstants.Key key : unrouteAfterInput) {
                KeyMapping routed = KeyRouting.routedTo(key);
                if (routed != null && !pendingTapReleases.containsKey(key)) KeyRouting.unroute(key, routed);
            }
            unrouteAfterInput.clear();
        }
        if (!afterInput.isEmpty()) {
            List<Runnable> tasks = new ArrayList<>(afterInput);
            afterInput.clear();
            for (Runnable task : tasks) task.run();
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
