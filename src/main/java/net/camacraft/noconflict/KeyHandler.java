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
    private static final Map<InputConstants.Key, Boolean> pressedInGui = new HashMap<>();
    
    // To track double taps
    private static final Map<InputConstants.Key, Long> lastReleaseTimes = new HashMap<>();
    
    // To optionally trigger a tap action when the radial menu closes
    private static InputConstants.Key pendingTapKey = null;
    private static KeyMapping pendingTapSelection = null;

    // Prevents the mod from intercepting its own simulated F3 key presses
    private static boolean isSimulatingF3 = false;
    
    // Ensures ConflictManager applies resolutions on the very first game tick after all mods load
    private static boolean initialized = false;
    
    // Cached reflection method for vanilla's internal F3 action handler
    private static Method handleDebugKeysMethod = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (!initialized) {
                initialized = true;
                ConflictManager.initialize();
            }
            
            // Continuously enforce resolutions in case vanilla or other mods overwrite the key map behind our back
            ConflictManager.enforceResolutions();

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
                            mc.tell(() -> {
                                try {
                                    if (handleDebugKeysMethod == null) {
                                        for (Method m : mc.keyboardHandler.getClass().getDeclaredMethods()) {
                                            String name = m.getName();
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
                                    
                                    if (targetKey == GLFW.GLFW_KEY_F4 && mc.gameMode != null && mc.player != null) {
                                        // Bypass reflection for F4 because the vanilla screen instantly closes without applying 
                                        // if the physical F3 key is not actively held down. We manually toggle it instead.
                                        mc.player.connection.sendCommand("gamemode " + (mc.player.isCreative() ? "survival" : "creative"));
                                    } else if (handleDebugKeysMethod != null) {
                                        handleDebugKeysMethod.invoke(mc.keyboardHandler, targetKey);
                                    } else {
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
                            mc.tell(() -> {
                                if (mc.screen != null) {
                                    mc.screen.keyPressed(pendingTapKey.getValue(), 0, 0);
                                    if (mc.screen != null) {
                                        mc.screen.keyReleased(pendingTapKey.getValue(), 0, 0);
                                    } else {
                                        while (pendingTapSelection.consumeClick()) {}
                                    }
                                }
                                MinecraftForge.EVENT_BUS.post(new InputEvent.Key(pendingTapKey.getValue(), 0, 0, 0));
                                pendingTapSelection.setKey(InputConstants.UNKNOWN);
                            });
                        } else {
                            ((KeyMappingExtensions) pendingTapSelection).incrementClickCounter();
                            pendingTapSelection.setDown(true);
                            MinecraftForge.EVENT_BUS.post(new InputEvent.Key(pendingTapKey.getValue(), 0, 0, 0));
                            pendingTapSelection.setDown(false);
                            pendingTapSelection.setKey(InputConstants.UNKNOWN);
                        }
                    }
                    
                    pendingTapKey = null;
                    pendingTapSelection = null;
                }
            }

            if (mc.screen instanceof RadialMenuScreen) {
                keyPressTimes.clear();
                heldForMenu.clear();
                pressedInGui.clear();
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
                
                List<KeyMapping> activeMappings = ConflictManager.getActiveMappings(key);
                
                // If there are no selections and the default behavior is TRIGGER_ALL, 
                // the conflict manager already unbound the conflict, allowing vanilla to natively handle it.
                // We should NOT open the radial menu or intercept holds in this state,
                // UNLESS the user is trying to hold it to open the menu to assign a new default.
                // Wait, if TRIGGER_ALL is active, vanilla handles the tap natively. 
                // But holding the key should STILL open the radial menu so they can make a selection!
                boolean hasActiveConflict = !activeMappings.isEmpty();

                if (duration > Config.HOLD_TIME.get() && (hasActiveConflict || isF3)) {
                    
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
                        if (mc.screen == null && !holdSelection.isDown()) {
                            holdSelection.setKey(key); 
                            holdSelection.setDown(true);
                            KeyMapping.click(key);
                            ((KeyMappingExtensions) holdSelection).incrementClickCounter();
                            
                            MinecraftForge.EVENT_BUS.post(new InputEvent.Key(key.getValue(), 0, 0, 0));
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
                pressedInGui.remove(keyToOpen);
                
                KeyMapping tapSelection = ConflictManager.getSelection(keyToOpen);
                if (tapSelection != null) {
                    tapSelection.setKey(InputConstants.UNKNOWN);
                }
                
                if (Config.F3_RADIAL_MENU.get() && keyToOpen.getValue() == GLFW.GLFW_KEY_F3) {
                    mc.setScreen(new RadialMenuScreen(F3MenuManager.getMappings(), keyToOpen, mc.screen, false));
                } else {
                    KeyMapping holdSelection = ConflictManager.getHoldSelection(keyToOpen);
                    if (holdSelection != null) {
                        holdSelection.setDown(false);
                        holdSelection.setKey(InputConstants.UNKNOWN);
                    }
                    
                    List<KeyMapping> mappings = ConflictManager.getActiveMappings(keyToOpen);
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

    public static boolean isPressedInGui(InputConstants.Key key) {
        return pressedInGui.getOrDefault(key, false);
    }

    public static void onRawInput(InputConstants.Key key, int scanCode, int action) {
        Minecraft mc = Minecraft.getInstance();

        if (action == InputConstants.PRESS) {
            boolean isF3 = Config.F3_RADIAL_MENU.get() && key.getValue() == GLFW.GLFW_KEY_F3;
            List<KeyMapping> activeMappings = ConflictManager.getActiveMappings(key);
            boolean hasActiveConflict = !activeMappings.isEmpty();
            
            if (hasActiveConflict || isF3) {
                
                if (mc.screen instanceof RadialMenuScreen) return;

                long currentTime = System.currentTimeMillis();
                keyPressTimes.put(key, currentTime);
                heldForMenu.put(key, false);
                pressedInGui.put(key, mc.screen != null);

                if (isF3) {
                    return;
                }

                KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
                KeyMapping tapSelection = ConflictManager.getSelection(key);
                
                if (mc.screen == null && holdSelection != null && holdSelection == tapSelection && !wasDoubleTapped(key, currentTime)) {
                    holdSelection.setKey(key);
                    holdSelection.setDown(true);
                    KeyMapping.click(key);
                    ((KeyMappingExtensions) holdSelection).incrementClickCounter();
                }

                if (mc.screen != null) {
                    if (tapSelection != null) {
                        try {
                            if (tapSelection.getKeyConflictContext().isActive()) {
                                tapSelection.setKey(key); 
                            }
                        } catch (Exception e) {}
                    }
                } else if (tapSelection == null && holdSelection == null && Config.DEFAULT_TAP_BEHAVIOR.get() == Config.DefaultHoldBehavior.TRIGGER_ALL) {
                    // If TRIGGER_ALL is enabled and nothing is selected, the keys were rebound natively by ConflictManager.
                    // We still intercept to populate keyPressTimes (so the user can hold to open the radial menu),
                    // but we do NOT want to interrupt native vanilla processing of the tap.
                    // Vanilla will process the keypress natively since its key is legitimately bound!
                }
            }
        } else if (action == InputConstants.RELEASE) {
            long currentTime = System.currentTimeMillis();
            lastReleaseTimes.put(key, currentTime);
            
            Long pressTime = keyPressTimes.remove(key);
            Boolean wasHeldForMenu = heldForMenu.remove(key);
            Boolean wasPressedInGui = pressedInGui.remove(key);
            
            if (Config.F3_RADIAL_MENU.get() && key.getValue() == GLFW.GLFW_KEY_F3) {
                return;
            }
            
            KeyMapping holdSelection = ConflictManager.getHoldSelection(key);
            if (holdSelection != null) {
                holdSelection.setDown(false);
                holdSelection.setKey(InputConstants.UNKNOWN);
            }
            
            if (pressTime != null && wasHeldForMenu != null && !wasHeldForMenu) {
                long duration = currentTime - pressTime;
                
                if (duration <= Config.HOLD_TIME.get()) {
                    KeyMapping selection = ConflictManager.getSelection(key);
                    
                    // Do nothing on release if TRIGGER_ALL is active and no selection is made
                    if (selection == null && holdSelection == null && Config.DEFAULT_TAP_BEHAVIOR.get() == Config.DefaultHoldBehavior.TRIGGER_ALL) {
                        return;
                    }
                    
                    if (selection != null) {
                        
                        if (wasPressedInGui != null && wasPressedInGui) {
                            mc.tell(() -> selection.setKey(InputConstants.UNKNOWN));
                            return;
                        }

                        try {
                            if (!selection.getKeyConflictContext().isActive()) {
                                return; 
                            }
                        } catch (Exception e) {}
                        
                        selection.setKey(key);
                        ((KeyMappingExtensions) selection).incrementClickCounter();
                        selection.setDown(true);
                        
                        MinecraftForge.EVENT_BUS.post(new InputEvent.Key(key.getValue(), scanCode, 0, 0));
                        
                        selection.setDown(false);
                        selection.setKey(InputConstants.UNKNOWN);
                        
                    } else if (mc.player != null && mc.screen == null) {
                        // We only show the warning message if DO_NOTHING is active.
                        if (Config.DEFAULT_TAP_BEHAVIOR.get() == Config.DefaultHoldBehavior.DO_NOTHING) {
                            mc.player.displayClientMessage(Component.literal("Press and hold to assign an action!").withStyle(ChatFormatting.RED), true);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyInput(InputEvent.Key event) {
        if (isSimulatingF3) return;

        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
        onRawInput(key, event.getScanCode(), event.getAction());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButtonInput(InputEvent.MouseButton.Pre event) {
        if (isSimulatingF3) return;

        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        onRawInput(key, -1, event.getAction());
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