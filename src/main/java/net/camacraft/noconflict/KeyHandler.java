package net.camacraft.noconflict;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.noconflict.client.ConflictManager;
import net.camacraft.noconflict.client.F3MenuManager;
import net.camacraft.noconflict.client.KeyMappingExtensions;
import net.camacraft.noconflict.client.gui.RadialMenuScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = NoConflict.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class KeyHandler {

    private static final Map<InputConstants.Key, Long> keyPressTimes = new HashMap<>();
    private static final Map<InputConstants.Key, Boolean> heldForMenu = new HashMap<>();
    
    // To track double taps
    private static final Map<InputConstants.Key, Long> lastReleaseTimes = new HashMap<>();
    
    // To optionally trigger a tap action when the radial menu closes
    private static InputConstants.Key pendingTapKey = null;
    private static KeyMapping pendingTapSelection = null;

    // Prevents the mod from intercepting its own simulated F3 key presses
    private static boolean isSimulatingF3 = false;
    
    // Cached reflection method for vanilla's internal F3 action handler
    private static Method handleDebugKeysMethod = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getInstance();

            // Check if we need to trigger a pending tap action from a closed menu
            if (pendingTapKey != null && pendingTapSelection != null) {
                if (mc.screen == null || !(mc.screen instanceof RadialMenuScreen)) {
                    
                    if (pendingTapSelection instanceof F3MenuManager.F3ActionMapping f3Mapping) {
                        int targetKey = f3Mapping.getTargetKey();
                        
                        if (targetKey == GLFW.GLFW_KEY_LEFT_ALT) {
                            mc.options.renderDebug = true;
                            mc.options.renderFpsChart = true;
                        } else {
                            // Modern Minecraft checks the physical state of the F3 key via GLFW directly
                            // when another key is pressed. Since we can't spoof native GLFW hardware states, 
                            // simulating a key press won't work because the game knows F3 is physically released.
                            // Instead, we use reflection to find and directly invoke vanilla's internal handleDebugKeys method.
                            mc.tell(() -> {
                                try {
                                    if (handleDebugKeysMethod == null) {
                                        for (Method m : mc.keyboardHandler.getClass().getDeclaredMethods()) {
                                            String name = m.getName();
                                            // Find the exact handleDebugKeys method.
                                            // m_90933_ is the SRG name for handleDebugKeys in production environments.
                                            if ((name.equals("handleDebugKeys") || name.equals("m_90933_")) 
                                                && m.getReturnType() == boolean.class 
                                                && m.getParameterCount() == 1 
                                                && m.getParameterTypes()[0] == int.class) {
                                                
                                                m.setAccessible(true);
                                                handleDebugKeysMethod = m;
                                                break;
                                            }
                                        }
                                    }
                                    
                                    if (handleDebugKeysMethod != null) {
                                        handleDebugKeysMethod.invoke(mc.keyboardHandler, targetKey);
                                    } else {
                                        // Absolute fallback for major actions if reflection completely fails across versions
                                        if (targetKey == GLFW.GLFW_KEY_T) mc.reloadResourcePacks();
                                        if (targetKey == GLFW.GLFW_KEY_A) mc.levelRenderer.allChanged();
                                        if (targetKey == GLFW.GLFW_KEY_D) mc.gui.getChat().clearMessages(false);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    } else {
                        pendingTapSelection.setKey(pendingTapKey);
                        
                        Screen activeScreen = mc.screen;
                        if (activeScreen != null) {
                            activeScreen.keyPressed(pendingTapKey.getValue(), 0, 0);
                            if (mc.screen == activeScreen) {
                                activeScreen.keyReleased(pendingTapKey.getValue(), 0, 0);
                            } else {
                                while(pendingTapSelection.consumeClick()) {}
                            }
                        } else {
                            ((KeyMappingExtensions) pendingTapSelection).incrementClickCounter();
                            pendingTapSelection.setDown(true);
                        }
                        
                        pendingTapSelection.setDown(false);
                        pendingTapSelection.setKey(InputConstants.UNKNOWN);
                        
                        MinecraftForge.EVENT_BUS.post(new InputEvent.Key(InputConstants.UNKNOWN.getValue(), 0, 0, 0));
                    }
                    
                    pendingTapKey = null;
                    pendingTapSelection = null;
                }
            }

            if (mc.screen instanceof RadialMenuScreen) {
                keyPressTimes.clear();
                heldForMenu.clear();
                return;
            }

            if (keyPressTimes.isEmpty()) return;

            long currentTime = System.currentTimeMillis();
            InputConstants.Key keyToOpen = null;

            for (Map.Entry<InputConstants.Key, Long> entry : keyPressTimes.entrySet()) {
                InputConstants.Key key = entry.getKey();
                long pressTime = entry.getValue();
                long duration = currentTime - pressTime;

                boolean isF3 = Config.F3_RADIAL_MENU.get() && key.getValue() == GLFW.GLFW_KEY_F3;

                if (duration > Config.HOLD_TIME.get() && (ConflictManager.hasConflict(key) || isF3)) {
                    
                    if (mc.screen != null && !Config.ALLOW_GUI_OVERLAY.get()) {
                        continue; 
                    }
                    
                    if (mc.screen != null && Config.ALLOW_GUI_OVERLAY.get()) {
                        String simpleName = mc.screen.getClass().getSimpleName();
                        String fullName = mc.screen.getClass().getName();
                        List<? extends String> blacklist = Config.GUI_OVERLAY_BLACKLIST.get();
                        
                        if (blacklist.contains(simpleName) || blacklist.contains(fullName)) {
                            continue; 
                        }
                    }
                    
                    if (isF3) {
                        keyToOpen = key;
                        break;
                    }
                    
                    KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
                    KeyMapping tapSelection = ConflictManager.getSelection(key);
                    
                    boolean isDoubleTapHold = wasDoubleTapped(key, pressTime);
                    
                    if (holdSelection != null && holdSelection == tapSelection && !isDoubleTapHold) {
                        if (!holdSelection.isDown()) {
                            holdSelection.setKey(key); 
                            holdSelection.setDown(true);
                            KeyMapping.click(key);
                            ((KeyMappingExtensions) holdSelection).incrementClickCounter();
                            
                            MinecraftForge.EVENT_BUS.post(new InputEvent.Key(InputConstants.UNKNOWN.getValue(), 0, 0, 0));
                        }
                    } else {
                        keyToOpen = key;
                        break;
                    }
                }
            }

            if (keyToOpen != null) {
                keyPressTimes.remove(keyToOpen); 
                heldForMenu.put(keyToOpen, true);
                
                if (Config.F3_RADIAL_MENU.get() && keyToOpen.getValue() == GLFW.GLFW_KEY_F3) {
                    mc.setScreen(new RadialMenuScreen(F3MenuManager.getMappings(), keyToOpen, mc.screen, false));
                } else {
                    KeyMapping holdSelection = ConflictManager.getHoldSelection(keyToOpen);
                    if (holdSelection != null) {
                        holdSelection.setDown(false);
                        holdSelection.setKey(InputConstants.UNKNOWN);
                    }
                    
                    List<KeyMapping> mappings = ConflictManager.getConflictingMappings(keyToOpen);
                    mc.setScreen(new RadialMenuScreen(mappings, keyToOpen, mc.screen, true));
                }
            }
        }
    }
    
    public static void setPendingTapAction(InputConstants.Key key, KeyMapping mapping) {
        pendingTapKey = key;
        pendingTapSelection = mapping;
    }
    
    private static boolean wasDoubleTapped(InputConstants.Key key, long pressTime) {
        Long lastRelease = lastReleaseTimes.get(key);
        if (lastRelease != null) {
            long gap = pressTime - lastRelease;
            return gap < Config.DOUBLE_TAP_TIME.get(); 
        }
        return false;
    }

    public static boolean onRawInput(InputConstants.Key key, int scanCode, int action) {
        Minecraft mc = Minecraft.getInstance();

        if (action == InputConstants.PRESS) {
            boolean isF3 = Config.F3_RADIAL_MENU.get() && key.getValue() == GLFW.GLFW_KEY_F3;
            
            if (ConflictManager.hasConflict(key) || isF3) {
                
                if (mc.screen instanceof RadialMenuScreen) return false;

                long currentTime = System.currentTimeMillis();
                keyPressTimes.put(key, currentTime);
                heldForMenu.put(key, false);

                if (isF3) {
                    if (mc.screen != null && !(mc.screen instanceof RadialMenuScreen)) {
                        return false; 
                    }
                    return true;
                }

                KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
                KeyMapping tapSelection = ConflictManager.getSelection(key);
                
                if (holdSelection != null && holdSelection == tapSelection && !wasDoubleTapped(key, currentTime)) {
                    holdSelection.setKey(key);
                    holdSelection.setDown(true);
                    KeyMapping.click(key);
                    ((KeyMappingExtensions) holdSelection).incrementClickCounter();
                    // Do NOT set the key back to UNKNOWN here! Keep it bound while held.
                }

                // In GUIs, we must NOT globally intercept the initial press if we want vanilla components 
                // to function identically to native behavior. 
                // Instead of completely cancelling the event, we let the press go through to the GUI. 
                // Because ConflictManager unbinds it, the GUI won't trigger the action YET.
                // We will manually trigger the action on release.
                if (mc.screen != null && !(mc.screen instanceof RadialMenuScreen)) {
                    return false; 
                }

                return true;
            }
        } else if (action == InputConstants.RELEASE) {
            long currentTime = System.currentTimeMillis();
            lastReleaseTimes.put(key, currentTime);
            
            Long pressTime = keyPressTimes.remove(key);
            Boolean wasHeldForMenu = heldForMenu.remove(key);
            
            if (Config.F3_RADIAL_MENU.get() && key.getValue() == GLFW.GLFW_KEY_F3) {
                if (wasHeldForMenu != null && wasHeldForMenu) {
                    // It was held for the menu, so we intercept the release to prevent the debug screen from toggling.
                    return true;
                } else {
                    // It was a tap, let vanilla handle it normally so the debug screen opens.
                    return false;
                }
            }
            
            KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
            if (holdSelection != null) {
                holdSelection.setDown(false);
                // Only set it back to UNKNOWN when it is actually released.
                holdSelection.setKey(InputConstants.UNKNOWN);
            }
            
            if (pressTime != null && wasHeldForMenu != null && !wasHeldForMenu) {
                long duration = currentTime - pressTime;
                
                if (duration <= Config.HOLD_TIME.get()) {
                    KeyMapping selection = ConflictManager.getSelection(key);
                    if (selection != null) {
                        
                        selection.setKey(key);

                        Screen activeScreen = mc.screen;
                        if (activeScreen != null && !(activeScreen instanceof RadialMenuScreen)) {
                            // Defer the execution of the tap into the GUI using enqueueWork 
                            // to ensure it happens completely independently of the active key poll loop
                            mc.tell(() -> {
                                if (mc.screen != null) {
                                    mc.screen.keyPressed(key.getValue(), scanCode, 0);
                                    if (mc.screen != null) {
                                        mc.screen.keyReleased(key.getValue(), scanCode, 0);
                                    } else {
                                        while (selection.consumeClick()) {}
                                    }
                                }
                                selection.setKey(InputConstants.UNKNOWN);
                            });
                            
                            // We don't unbind immediately here, we let the scheduled task unbind it
                            return false;
                            
                        } else {
                            ((KeyMappingExtensions) selection).incrementClickCounter();
                            selection.setDown(true);
                            selection.setDown(false);
                            selection.setKey(InputConstants.UNKNOWN);
                            
                            MinecraftForge.EVENT_BUS.post(new InputEvent.Key(InputConstants.UNKNOWN.getValue(), 0, 0, 0));
                        }
                        
                    } else if (mc.player != null && (mc.screen == null || mc.screen instanceof RadialMenuScreen)) {
                        mc.player.displayClientMessage(Component.literal("Press and hold to assign an action!").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
            
            if (mc.screen instanceof RadialMenuScreen) return false;
            
            if (ConflictManager.hasConflict(key)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        if (isSimulatingF3) return;

        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
        
        boolean cancelF3Release = false;
        if (Config.F3_RADIAL_MENU.get() && key.getValue() == GLFW.GLFW_KEY_F3 && event.getAction() == GLFW.GLFW_RELEASE) {
            if (heldForMenu.getOrDefault(key, false)) {
                cancelF3Release = true;
            }
        }
        
        if (onRawInput(key, event.getScanCode(), event.getAction())) {
            if (cancelF3Release) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonInput(InputEvent.MouseButton.Pre event) {
        if (isSimulatingF3) return;

        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        if (onRawInput(key, -1, event.getAction())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        Screen screen = event.getScreen();
        if (screen instanceof KeyBindsScreen) {
            ConflictManager.suspend();
        } else if (screen == null) {
            ConflictManager.resume();
        }
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        Screen screen = event.getScreen();
        if (screen instanceof KeyBindsScreen) {
            ConflictManager.resume();
        }
    }
}