package net.camacraft.noconflict.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.camacraft.noconflict.Config;
import net.camacraft.noconflict.KeyHandler;
import net.camacraft.noconflict.client.ConflictManager;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

public class RadialMenuScreen extends Screen {
    private final List<KeyMapping> allKeyMappings;
    private List<KeyMapping> currentPageMappings;
    private final InputConstants.Key triggerKey;
    private final Screen parentScreen;
    private final boolean saveSelection;
    private int selectedIndex = -1;
    private boolean hoveringCenter = false;
    private boolean hasClickedDuringThisOpen = false;
    private int indexClickedDuringThisOpen = -1;
    
    private int currentPage = 0;
    private int totalPages = 1;
    private final boolean paginationEnabled;
    private final int itemsPerPage;
    
    private long lastRenderTime;
    private float[] hoverProgress;

    public RadialMenuScreen(List<KeyMapping> keyMappings, InputConstants.Key triggerKey, Screen parentScreen, boolean saveSelection) {
        super(Component.literal("Radial Menu"));
        this.allKeyMappings = new ArrayList<>(keyMappings);
        this.triggerKey = triggerKey;
        this.parentScreen = parentScreen;
        this.saveSelection = saveSelection;
        
        this.paginationEnabled = Config.PAGINATION_ENABLED.get();
        this.itemsPerPage = Config.ITEMS_PER_PAGE.get();
        
        updatePageMappings();
        this.lastRenderTime = Util.getMillis();
    }
    
    private void updatePageMappings() {
        if (paginationEnabled && allKeyMappings.size() > itemsPerPage) {
            this.totalPages = (int) Math.ceil((double) allKeyMappings.size() / itemsPerPage);
            if (currentPage >= totalPages) currentPage = totalPages - 1;
            if (currentPage < 0) currentPage = 0;
            
            int startIndex = currentPage * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, allKeyMappings.size());
            this.currentPageMappings = allKeyMappings.subList(startIndex, endIndex);
        } else {
            this.currentPageMappings = allKeyMappings;
            this.totalPages = 1;
            this.currentPage = 0;
        }
        
        this.hoverProgress = new float[currentPageMappings.size()];
        this.selectedIndex = -1;
        this.indexClickedDuringThisOpen = -1;
        this.hasClickedDuringThisOpen = false;
    }

    private void playToggleSound() {
        if (Config.TOGGLE_SOUND.get()) {
            float volume = Config.TOGGLE_VOLUME.get().floatValue();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F, volume));
        }
    }

    private float easeOutCubic(float t) {
        float f = 1.0f - t;
        return 1.0f - f * f * f;
    }

    private int lerpColor(int colorStart, int colorEnd, float t) {
        int aStart = (colorStart >> 24) & 0xFF;
        int rStart = (colorStart >> 16) & 0xFF;
        int gStart = (colorStart >> 8) & 0xFF;
        int bStart = colorStart & 0xFF;

        int aEnd = (colorEnd >> 24) & 0xFF;
        int rEnd = (colorEnd >> 16) & 0xFF;
        int gEnd = (colorEnd >> 8) & 0xFF;
        int bEnd = colorEnd & 0xFF;

        int a = (int) (aStart + (aEnd - aStart) * t);
        int r = (int) (rStart + (rEnd - rStart) * t);
        int g = (int) (gStart + (gEnd - gStart) * t);
        int b = (int) (bStart + (bEnd - bStart) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // Render the parent screen beneath us if it exists
        if (this.parentScreen != null) {
            this.parentScreen.render(guiGraphics, -1, -1, partialTicks);
        }
        
        int alpha = (int) (Config.BACKGROUND_OPACITY.get() * 255.0);
        guiGraphics.fill(0, 0, this.width, this.height, alpha << 24);
        
        super.render(guiGraphics, mouseX, mouseY, partialTicks);

        if (currentPageMappings.isEmpty()) return;

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int radius = Config.RADIUS.get();

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distanceSq = dx * dx + dy * dy;

        double textScale = Config.TEXT_SCALE.get();
        KeyMapping currentSelection = saveSelection ? ConflictManager.getSelection(triggerKey) : null;
        KeyMapping holdSelection = saveSelection ? ConflictManager.getHoldSelection(triggerKey) : null;

        if (distanceSq > 30 * 30) {
            hoveringCenter = false;
            double angle = Math.atan2(dy, dx);
            if (angle < 0) angle += 2 * Math.PI;
            double segmentAngle = 2 * Math.PI / currentPageMappings.size();
            double adjustedAngle = (angle + segmentAngle / 2) % (2 * Math.PI);
            selectedIndex = (int) (adjustedAngle / segmentAngle);
            if (selectedIndex >= currentPageMappings.size()) selectedIndex = 0;
        } else {
            hoveringCenter = true;
            selectedIndex = -1;
        }

        // Animation update
        long currentTime = Util.getMillis();
        float deltaTime = (currentTime - lastRenderTime) / 1000f;
        lastRenderTime = currentTime;

        float animationSpeed = Config.ANIMATION_SPEED.get().floatValue();
        for (int i = 0; i < currentPageMappings.size(); i++) {
            if (i == selectedIndex) {
                hoverProgress[i] = Math.min(1.0f, hoverProgress[i] + deltaTime * animationSpeed);
            } else {
                hoverProgress[i] = Math.max(0.0f, hoverProgress[i] - deltaTime * animationSpeed);
            }
        }

        // Draw separators
        if (currentPageMappings.size() > 1) {
            int innerRadius = 30; // Leave space for center X
            int outerRadius = radius + 60;
            int separatorColor = Config.getIntColor(Config.SEPARATOR_COLOR);
            for (int i = 0; i < currentPageMappings.size(); i++) {
                double angle = 2 * Math.PI * i / currentPageMappings.size() - Math.PI / currentPageMappings.size();
                
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(centerX, centerY, 0);
                guiGraphics.pose().mulPose(Axis.ZP.rotation((float) angle));
                guiGraphics.fill(innerRadius, -1, outerRadius, 1, separatorColor);
                guiGraphics.pose().popPose();
            }
        }

        // Draw the center X
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0);
        float centerScale = hoveringCenter ? (float)textScale * 1.5f : (float)textScale * 1.2f;
        guiGraphics.pose().scale(centerScale, centerScale, centerScale);
        Component xText = Component.literal("X");
        int xColor = hoveringCenter ? 0xFFFF5555 : 0xFFB22222;
        guiGraphics.drawString(this.font, xText, -this.font.width(xText) / 2, -this.font.lineHeight / 2, xColor);
        guiGraphics.pose().popPose();
        
        // Draw page indicator if pagination is active
        if (totalPages > 1) {
            String pageText = (currentPage + 1) + " / " + totalPages;
            int pageTextWidth = this.font.width(pageText);
            // Position directly under the center X
            int yOffset = (int) ((this.font.lineHeight / 2.0f) * textScale * 1.5f + 5);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(centerX, centerY + yOffset, 0);
            guiGraphics.pose().scale((float)textScale * 0.8f, (float)textScale * 0.8f, 1.0f);
            guiGraphics.drawString(this.font, pageText, -pageTextWidth / 2, 0, 0xFFAAAAAA);
            guiGraphics.pose().popPose();
        }

        for (int i = 0; i < currentPageMappings.size(); i++) {
            double angle = 2 * Math.PI * i / currentPageMappings.size();
            double itemX = centerX + radius * Math.cos(angle);
            double itemY = centerY + radius * Math.sin(angle);

            float progress = easeOutCubic(hoverProgress[i]);
            float scaleFactor = 1.0F + (0.2F * progress);
            float finalScale = (float) (textScale * scaleFactor);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(itemX, itemY, 0);
            guiGraphics.pose().scale(finalScale, finalScale, finalScale);

            KeyMapping mapping = currentPageMappings.get(i);
            String translatedName = Component.translatable(mapping.getName()).getString();
            String[] words = translatedName.split(" ");
            
            int baseColor;
            int holdColor = Config.getIntColor(Config.HOLD_INDICATOR_COLOR);

            if (mapping == currentSelection) {
                baseColor = lerpColor(Config.getIntColor(Config.SELECTED_ITEM_COLOR), Config.getIntColor(Config.SELECTED_HOVERED_ITEM_COLOR), progress);
            } else {
                baseColor = lerpColor(Config.getIntColor(Config.REGULAR_ITEM_COLOR), Config.getIntColor(Config.HOVERED_ITEM_COLOR), progress);
            }

            int totalHeight = words.length * this.font.lineHeight;
            float startY = -totalHeight / 2.0f;
            
            int maxWidth = 0;
            for(String word : words) {
                int w = this.font.width(word);
                if(w > maxWidth) maxWidth = w;
            }

            float blockXOffset = (float) ((maxWidth / 2.0f + 4) * Math.cos(angle));
            float blockYOffset = (float) ((totalHeight / 2.0f + 4) * Math.sin(angle));

            for (int w = 0; w < words.length; w++) {
                String word = words[w];
                float wordWidth = this.font.width(word);
                
                float xOffset = -wordWidth / 2.0f + blockXOffset;
                float yOffset = startY + (w * this.font.lineHeight) + blockYOffset;
                
                guiGraphics.drawString(this.font, word, (int) xOffset, (int) yOffset, baseColor);
                
                if (w == words.length - 1 && mapping == holdSelection) {
                    String holdText = " (Hold)";
                    float holdXOffset = xOffset + wordWidth;
                    guiGraphics.drawString(this.font, holdText, (int) holdXOffset, (int) yOffset, holdColor);
                }
            }

            guiGraphics.pose().popPose();
        }
    }

    private void executeSelection(boolean isClick, int button) {
        if (hoveringCenter) {
            if (isClick) {
                playToggleSound();
                closeRadialMenu();
            } else {
                closeRadialMenu();
            }
        } else if (selectedIndex != -1) {
            KeyMapping selectedMapping = currentPageMappings.get(selectedIndex);
            KeyMapping currentSelection = saveSelection ? ConflictManager.getSelection(triggerKey) : null;
            KeyMapping holdSelection = saveSelection ? ConflictManager.getHoldSelection(triggerKey) : null;
            
            if (!saveSelection) {
                if (isClick && button == 0) {
                    // Ignore left click hold toggle for transient menus
                    return;
                }
                
                if (isClick && button == 1) { // Right Click
                    if (Config.TRIGGER_ON_SELECTION.get()) {
                        KeyHandler.setPendingTapAction(triggerKey, selectedMapping);
                    }
                    playToggleSound();
                    closeRadialMenu();
                } else if (!isClick) { // Release
                    if (Config.TRIGGER_ON_SELECTION.get()) {
                        KeyHandler.setPendingTapAction(triggerKey, selectedMapping);
                    }
                    playToggleSound();
                    closeRadialMenu();
                }
                return;
            }
            
            if (isClick) {
                if (button == 0) { // Left Click
                    // Click ONLY toggles HOLD mode, it does NOT close the menu or set TAP mode
                    if (selectedMapping == holdSelection) {
                        ConflictManager.setHoldSelection(triggerKey, null);
                    } else {
                        ConflictManager.setHoldSelection(triggerKey, selectedMapping);
                    }
                    hasClickedDuringThisOpen = true;
                    indexClickedDuringThisOpen = selectedIndex;
                    playToggleSound();
                } else if (button == 1) { // Right Click
                    // Right click forces Tap selection and immediately closes and triggers it.
                    if (selectedMapping != currentSelection) {
                        ConflictManager.setSelection(triggerKey, selectedMapping);
                    }
                    if (Config.TRIGGER_ON_SELECTION.get()) {
                        KeyHandler.setPendingTapAction(triggerKey, selectedMapping);
                    }
                    playToggleSound();
                    closeRadialMenu();
                }
            } else {
                // Release logic
                if (hasClickedDuringThisOpen && selectedIndex == indexClickedDuringThisOpen) {
                    if (selectedMapping != currentSelection) {
                        ConflictManager.setSelection(triggerKey, selectedMapping);
                        if (Config.TRIGGER_ON_SELECTION.get()) KeyHandler.setPendingTapAction(triggerKey, selectedMapping);
                    } else {
                        if (Config.TRIGGER_ON_SELECTION.get()) KeyHandler.setPendingTapAction(triggerKey, selectedMapping);
                    }
                } else if (hasClickedDuringThisOpen && selectedIndex != indexClickedDuringThisOpen) {
                    if (selectedMapping == currentSelection) {
                        ConflictManager.setSelection(triggerKey, null);
                    } else {
                        ConflictManager.setSelection(triggerKey, selectedMapping);
                        if (Config.TRIGGER_ON_SELECTION.get()) KeyHandler.setPendingTapAction(triggerKey, selectedMapping);
                    }
                } else {
                    if (selectedMapping == currentSelection) {
                        ConflictManager.setSelection(triggerKey, null);
                    } else {
                        ConflictManager.setSelection(triggerKey, selectedMapping);
                        if (Config.TRIGGER_ON_SELECTION.get()) KeyHandler.setPendingTapAction(triggerKey, selectedMapping);
                    }
                }
                playToggleSound();
                closeRadialMenu();
            }
        } else {
            // If releasing without a valid selection, just close the menu.
            if (!isClick) {
                closeRadialMenu();
            }
        }
    }
    
    private void closeRadialMenu() {
        Minecraft.getInstance().setScreen(this.parentScreen);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (totalPages > 1) {
            if (delta > 0) {
                currentPage--;
                if (currentPage < 0) currentPage = totalPages - 1; // loop around
            } else if (delta < 0) {
                currentPage++;
                if (currentPage >= totalPages) currentPage = 0; // loop around
            }
            updatePageMappings();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // If the trigger key is a mouse button, its release should act like a key release
        if (triggerKey.getType() == InputConstants.Type.MOUSE && button == triggerKey.getValue()) {
            executeSelection(false, -1);
            return true;
        }

        if (button == 0 || button == 1) { // Support left and right click
            executeSelection(true, button);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (key.equals(triggerKey)) {
            executeSelection(false, -1);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}