package net.fabricmc.example.config;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.example.M;
import net.fabricmc.example.config.elements.GuiElement;
import net.fabricmc.example.config.elements.GuiElementTextField;
import net.fabricmc.example.config.gui.GuiOptionEditor;
import net.fabricmc.example.config.gui.GuiOptionEditorAccordion;
import net.fabricmc.example.config.struct.ConfigProcessor;
import net.fabricmc.example.util.GlScissorStack;
import net.fabricmc.example.util.lerp.LerpUtils;
import net.fabricmc.example.util.lerp.LerpingInteger;
import net.fabricmc.example.util.render.RenderUtils;
import net.fabricmc.example.util.render.TextRenderUtils;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.Window;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.net.URI;
import java.util.*;
import java.util.List;

public class MyModConfigEditor extends GuiElement {

    //apparently if you want your link to show up first you gotta put it at the very end of the array

    private static final Identifier[] socialsIco = new Identifier[] { GuiTextures.GITHUB };
    private static final String[] socialsLink = new String[] { "https://github.com/u9g" };

    // search related
    private final LerpingInteger minimumSearchSize = new LerpingInteger(0, 150);
    private final GuiElementTextField searchField = new GuiElementTextField("", 0, 20, 0);
    private Set<ConfigProcessor.ProcessedCategory> searchedCategories = null;
    private Map<ConfigProcessor.ProcessedCategory, Set<Integer>> searchedAccordions = null;
    private Set<ConfigProcessor.ProcessedOption> searchedOptions = null;
    // not search related

    private final long openedMillis;

    private String selectedCategory = null;
    private final String headerText;

    private final LerpingInteger optionsScroll = new LerpingInteger(0, 150);
    private final LerpingInteger categoryScroll = new LerpingInteger(0, 150);

    private LinkedHashMap<String, ConfigProcessor.ProcessedCategory> processedConfig;
    private TreeMap<String, Set<ConfigProcessor.ProcessedOption>> searchOptionMap = new TreeMap<>();
    private HashMap<ConfigProcessor.ProcessedOption, ConfigProcessor.ProcessedCategory> categoryForOption = new HashMap<>();

    public MyModConfigEditor(Config config) {
        this(config, null);
    }

    public MyModConfigEditor(Config config, String categoryOpen) {
        this.openedMillis = System.currentTimeMillis();
        this.processedConfig = ConfigProcessor.create(config);
        this.headerText = config.getHeaderText();

        for (ConfigProcessor.ProcessedCategory category : processedConfig.values()) {
            for (ConfigProcessor.ProcessedOption option : category.options.values()) {
                categoryForOption.put(option, category);

                String combined = category.name + " " + category.desc + " " + option.name + " " + option.desc;
                combined = combined.replaceAll("[^a-zA-Z_ ]", "").toLowerCase();
                for (String word : combined.split("[ _]")) {
                    searchOptionMap.computeIfAbsent(word, k -> new HashSet<>()).add(option);
                }
            }
        }

        if (categoryOpen != null) {
            for (Map.Entry<String, ConfigProcessor.ProcessedCategory> category : processedConfig.entrySet()) {
                if (category.getValue().name.equalsIgnoreCase(categoryOpen)) {
                    selectedCategory = category.getKey();
                    break;
                }
            }
            if (selectedCategory == null) {
                for (Map.Entry<String, ConfigProcessor.ProcessedCategory> category : processedConfig.entrySet()) {
                    if (category.getValue().name.toLowerCase().startsWith(categoryOpen.toLowerCase())) {
                        selectedCategory = category.getKey();
                        break;
                    }
                }
            }
            if (selectedCategory == null) {
                for (Map.Entry<String, ConfigProcessor.ProcessedCategory> category : processedConfig.entrySet()) {
                    if (category.getValue().name.toLowerCase().contains(categoryOpen.toLowerCase())) {
                        selectedCategory = category.getKey();
                        break;
                    }
                }
            }
        }
    }

    private LinkedHashMap<String, ConfigProcessor.ProcessedCategory> getCurrentConfigEditing() {
        return new LinkedHashMap<>(processedConfig);
    }

    private LinkedHashMap<String, ConfigProcessor.ProcessedOption> getOptionsInCategory(ConfigProcessor.ProcessedCategory cat) {
        return new LinkedHashMap<>(cat.options);
    }

    public String getSelectedCategory() {
        return selectedCategory;
    }

    public String getSelectedCategoryName() {
        return processedConfig.get(selectedCategory).name;
    }

    private void setSelectedCategory(String category) {
        selectedCategory = category;
        optionsScroll.setValue(0);
    }

    public void search() {
        String search = searchField.getText().trim().replaceAll("[^a-zA-Z_ ]", "").toLowerCase();
        searchedCategories = null;
        searchedOptions = null;
        searchedAccordions = null;

        if (!search.isEmpty()) {
            searchedCategories = new HashSet<>();
            searchedAccordions = new HashMap<>();

            for (String word : search.split(" ")) {
                if (word.trim().isEmpty()) continue;

                Set<ConfigProcessor.ProcessedOption> options = new HashSet<>();

                Map<String, Set<ConfigProcessor.ProcessedOption>> map = NotEnoughUpdates.INSTANCE.manager
                        .subMapWithKeysThatAreSuffixes(word, searchOptionMap);

                map.values().forEach(options::addAll);

                if (!options.isEmpty()) {
                    if (searchedOptions == null) {
                        searchedOptions = new HashSet<>(options);
                    } else {
                        searchedOptions.retainAll(options);
                    }
                }
            }

            if (searchedOptions == null) {
                searchedOptions = new HashSet<>();
            } else {
                for (ConfigProcessor.ProcessedOption option : searchedOptions) {
                    ConfigProcessor.ProcessedCategory cat = categoryForOption.get(option);
                    if (cat == null) continue;

                    searchedCategories.add(cat);
                    searchedAccordions.computeIfAbsent(cat, k -> new HashSet<>()).add(option.accordionId);
                }
            }
        }
    }

    public void render() {
        optionsScroll.tick();
        categoryScroll.tick();

        List<String> tooltipToDisplay = null;

        long currentTime = System.currentTimeMillis();
        long delta = currentTime - openedMillis;

        Window scaledResolution = new Window(M.C);
        int width = scaledResolution.getWidth();
        int height = scaledResolution.getHeight();
        int mouseX = Mouse.getX() * width / M.C.width;
        int mouseY = height - Mouse.getY() * height / M.C.height - 1;

        float opacityFactor = LerpUtils.sigmoidZeroOne(delta / 500f);
        RenderUtils.drawGradientRect(0, 0, 0, width, height, (int) (0x80 * opacityFactor) << 24 | 0x101010, (int) (0x90 * opacityFactor) << 24 | 0x101010);

        int xSize = Math.min(scaledResolution.getWidth() - 100 / scaledResolution.getScaleFactor(), 500);
        int ySize = Math.min(scaledResolution.getHeight() - 100 / scaledResolution.getScaleFactor(), 400);

        int x = (scaledResolution.getWidth() - xSize) / 2;
        int y = (scaledResolution.getHeight() - ySize) / 2;

        int adjScaleFactor = Math.max(2, scaledResolution.getScaleFactor());

        int openingXSize = xSize;
        int openingYSize = ySize;
        if (delta < 150) {
            openingXSize = (int) (delta * xSize / 150);
            openingYSize = 5;
        } else if (delta < 300) {
            openingYSize = 5 + (int) (delta - 150) * (ySize - 5) / 150;
        }
        RenderUtils.drawFloatingRectDark((scaledResolution.getWidth() - openingXSize) / 2, (scaledResolution.getHeight() - openingYSize) / 2, openingXSize, openingYSize);
        GlScissorStack.clear();
        GlScissorStack.push((scaledResolution.getWidth() - openingXSize) / 2, (scaledResolution.getHeight() - openingYSize) / 2, (scaledResolution.getWidth() + openingXSize) / 2, (scaledResolution.getHeight() + openingYSize) / 2, scaledResolution);

        RenderUtils.drawFloatingRectDark(x + 5, y + 5, xSize - 10, 20, false);

        TextRenderer fr = M.C.textRenderer;
        TextRenderUtils.drawStringCenteredScaledMaxWidth(headerText, fr, x + xSize / 2f, y + 15, false, 200, 0xa0a0a0);

        RenderUtils.drawFloatingRectDark(x + 4, y + 49 - 20, 140, ySize - 54 + 20, false);

        int innerPadding = 20 / adjScaleFactor;
        int innerLeft = x + 4 + innerPadding;
        int innerRight = x + 144 - innerPadding;
        int innerTop = y + 49 + innerPadding;
        int innerBottom = y + ySize - 5 - innerPadding;
        DrawableHelper.fill(innerLeft, innerTop, innerLeft + 1, innerBottom, 0xff08080E); //Left
        DrawableHelper.fill(innerLeft + 1, innerTop, innerRight, innerTop + 1, 0xff08080E); //Top
        DrawableHelper.fill(innerRight - 1, innerTop + 1, innerRight, innerBottom, 0xff28282E); //Right
        DrawableHelper.fill(innerLeft + 1, innerBottom - 1, innerRight - 1, innerBottom, 0xff28282E); //Bottom
        DrawableHelper.fill(innerLeft + 1, innerTop + 1, innerRight - 1, innerBottom - 1, 0x6008080E); //Middle

        GlScissorStack.push(0, innerTop + 1, scaledResolution.getWidth(), innerBottom - 1, scaledResolution);

        float catBarSize = 1;
        int catY = -categoryScroll.getValue();

        LinkedHashMap<String, ConfigProcessor.ProcessedCategory> currentConfigEditing = getCurrentConfigEditing();
        for (Map.Entry<String, ConfigProcessor.ProcessedCategory> entry : currentConfigEditing.entrySet()) {
            String selectedCategory = getSelectedCategory();
            if (selectedCategory == null || !currentConfigEditing.containsKey(selectedCategory)) {
                setSelectedCategory(entry.getKey());
            }
            String catName = entry.getValue().name;
            if (entry.getKey().equals(getSelectedCategory())) {
                catName = Formatting.DARK_AQUA.toString() + Formatting.UNDERLINE + catName;
            } else {
                catName = Formatting.GRAY + catName;
            }
            TextRenderUtils.drawStringCenteredScaledMaxWidth(catName, fr, x + 75, y + 70 + catY, false, 100, -1);
            catY += 15;
            if (catY > 0) {
                catBarSize = LerpUtils.clampZeroOne((float) (innerBottom - innerTop - 2) / (catY + 5 + categoryScroll.getValue()));
            }
        }

        float catBarStart = categoryScroll.getValue() / (float) (catY + categoryScroll.getValue());
        float catBarEnd = catBarStart + catBarSize;
        if (catBarEnd > 1) {
            catBarEnd = 1;
            if (categoryScroll.getTarget() / (float) (catY + categoryScroll.getValue()) + catBarSize < 1) {
                int target = optionsScroll.getTarget();
                categoryScroll.setValue((int) Math.ceil((catY + 5 + categoryScroll.getValue()) - catBarSize * (catY + 5 + categoryScroll.getValue())));
                categoryScroll.setTarget(target);
            } else {
                categoryScroll.setValue((int) Math.ceil((catY + 5 + categoryScroll.getValue()) - catBarSize * (catY + 5 + categoryScroll.getValue())));
            }
        }
        int catDist = innerBottom - innerTop - 12;
        DrawableHelper.fill(innerLeft + 2, innerTop + 5, innerLeft + 7, innerBottom - 5, 0xff101010);
        DrawableHelper.fill(innerLeft + 3, innerTop + 6 + (int) (catDist * catBarStart), innerLeft + 6, innerTop + 6 + (int) (catDist * catBarEnd), 0xff303030);

        GlScissorStack.pop(scaledResolution);

        TextRenderUtils.drawStringCenteredScaledMaxWidth("Categories", fr, x + 75, y + 44, false, 120, 0xa368ef);

        RenderUtils.drawFloatingRectDark(x + 149, y + 29, xSize - 154, ySize - 34, false);

        innerLeft = x + 149 + innerPadding;
        innerRight = x + xSize - 5 - innerPadding;
        innerBottom = y + ySize - 5 - innerPadding;

        GlStateManager.color4f(1, 1, 1, 1);
        int rightStuffLen = 20;

        if (getSelectedCategory() != null && currentConfigEditing.containsKey(getSelectedCategory())) {
            ConfigProcessor.ProcessedCategory cat = currentConfigEditing.get(getSelectedCategory());

            TextRenderUtils.drawStringScaledMaxWidth(cat.desc, fr, innerLeft + 5, y + 40, true, innerRight - innerLeft - rightStuffLen - 10, 0xb0b0b0);
        }

        DrawableHelper.fill(innerLeft, innerTop, innerLeft + 1, innerBottom, 0xff08080E); //Left
        DrawableHelper.fill(innerLeft + 1, innerTop, innerRight, innerTop + 1, 0xff08080E); //Top
        DrawableHelper.fill(innerRight - 1, innerTop + 1, innerRight, innerBottom, 0xff303036); //Right
        DrawableHelper.fill(innerLeft + 1, innerBottom - 1, innerRight - 1, innerBottom, 0xff303036); //Bottom
        DrawableHelper.fill(innerLeft + 1, innerTop + 1, innerRight - 1, innerBottom - 1, 0x6008080E); //Middle

        GlScissorStack.push(innerLeft + 1, innerTop + 1, innerRight - 1, innerBottom - 1, scaledResolution);
        float barSize = 1;
        int optionY = -optionsScroll.getValue();
        if (getSelectedCategory() != null && currentConfigEditing.containsKey(getSelectedCategory())) {
            ConfigProcessor.ProcessedCategory cat = currentConfigEditing.get(getSelectedCategory());
            int optionWidthDefault = innerRight - innerLeft - 20;
            GlStateManager.enableDepthTest();
            HashMap<Integer, Integer> activeAccordions = new HashMap<>();
            for (ConfigProcessor.ProcessedOption option : getOptionsInCategory(cat).values()) {
                int optionWidth = optionWidthDefault;
                if (option.accordionId >= 0) {
                    if (!activeAccordions.containsKey(option.accordionId)) {
                        continue;
                    }
                    int accordionDepth = activeAccordions.get(option.accordionId);
                    optionWidth = optionWidthDefault - (2 * innerPadding) * (accordionDepth + 1);
                }

                GuiOptionEditor editor = option.editor;
                if (editor == null) {
                    continue;
                }
                if (editor instanceof GuiOptionEditorAccordion) {
                    GuiOptionEditorAccordion accordion = (GuiOptionEditorAccordion) editor;
                    if (accordion.getToggled()) {
                        int accordionDepth = 0;
                        if (option.accordionId >= 0) {
                            accordionDepth = activeAccordions.get(option.accordionId) + 1;
                        }
                        activeAccordions.put(accordion.getAccordionId(), accordionDepth);
                    }
                }
                int optionHeight = editor.getHeight();
                if (innerTop + 5 + optionY + optionHeight > innerTop + 1 && innerTop + 5 + optionY < innerBottom - 1) {
                    editor.render((innerLeft + innerRight - optionWidth) / 2 - 5, innerTop + 5 + optionY, optionWidth);
                }
                optionY += optionHeight + 5;
            }
            GlStateManager.disableDepthTest();
            if (optionY > 0) {
                barSize = LerpUtils.clampZeroOne((float) (innerBottom - innerTop - 2) / (optionY + 5 + optionsScroll.getValue()));
            }
        }

        GlScissorStack.pop(scaledResolution);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (getSelectedCategory() != null && currentConfigEditing.containsKey(getSelectedCategory())) {
            int optionYOverlay = -optionsScroll.getValue();
            ConfigProcessor.ProcessedCategory cat = currentConfigEditing.get(getSelectedCategory());
            int optionWidthDefault = innerRight - innerLeft - 20;

            GlStateManager.translated(0, 0, 10);
            GlStateManager.enableDepthTest();
            HashMap<Integer, Integer> activeAccordions = new HashMap<>();
            for (ConfigProcessor.ProcessedOption option : getOptionsInCategory(cat).values()) {
                int optionWidth = optionWidthDefault;
                if (option.accordionId >= 0) {
                    if (!activeAccordions.containsKey(option.accordionId)) {
                        continue;
                    }
                    int accordionDepth = activeAccordions.get(option.accordionId);
                    optionWidth = optionWidthDefault - (2 * innerPadding) * (accordionDepth + 1);
                }

                GuiOptionEditor editor = option.editor;
                if (editor == null) {
                    continue;
                }
                if (editor instanceof GuiOptionEditorAccordion) {
                    GuiOptionEditorAccordion accordion = (GuiOptionEditorAccordion) editor;
                    if (accordion.getToggled()) {
                        int accordionDepth = 0;
                        if (option.accordionId >= 0) {
                            accordionDepth = activeAccordions.get(option.accordionId) + 1;
                        }
                        activeAccordions.put(accordion.getAccordionId(), accordionDepth);
                    }
                }
                int optionHeight = editor.getHeight();
                if (innerTop + 5 + optionYOverlay + optionHeight > innerTop + 1 && innerTop + 5 + optionYOverlay < innerBottom - 1) {
                    editor.renderOverlay((innerLeft + innerRight - optionWidth) / 2 - 5, innerTop + 5 + optionYOverlay, optionWidth);
                }
                optionYOverlay += optionHeight + 5;
            }
            GlStateManager.disableDepthTest();
            GlStateManager.translated(0, 0, -10);
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        float barStart = optionsScroll.getValue() / (float) (optionY + optionsScroll.getValue());
        float barEnd = barStart + barSize;
        if (barEnd > 1) {
            barEnd = 1;
            if (optionsScroll.getTarget() / (float) (optionY + optionsScroll.getValue()) + barSize < 1) {
                int target = optionsScroll.getTarget();
                optionsScroll.setValue((int) Math.ceil((optionY + 5 + optionsScroll.getValue()) - barSize * (optionY + 5 + optionsScroll.getValue())));
                optionsScroll.setTarget(target);
            } else {
                optionsScroll.setValue((int) Math.ceil((optionY + 5 + optionsScroll.getValue()) - barSize * (optionY + 5 + optionsScroll.getValue())));
            }
        }
        int dist = innerBottom - innerTop - 12;
        DrawableHelper.fill(innerRight - 10, innerTop + 5, innerRight - 5, innerBottom - 5, 0xff101010);
        DrawableHelper.fill(innerRight - 9, innerTop + 6 + (int) (dist * barStart), innerRight - 6, innerTop + 6 + (int) (dist * barEnd), 0xff303030);

        for (int socialIndex = 0; socialIndex < socialsIco.length; socialIndex++) {
            M.C.getTextureManager().bindTexture(socialsIco[socialIndex]);
            GlStateManager.color4f(1, 1, 1, 1);
            int socialLeft = x + xSize - 23 - 18 * socialIndex;
            RenderUtils.drawTexturedRect(socialLeft, y + 7, 16, 16, GL11.GL_LINEAR);

            if (mouseX >= socialLeft && mouseX <= socialLeft + 16 && mouseY >= y + 6 && mouseY <= y + 23) {
                tooltipToDisplay = Lists.newArrayList(Formatting.YELLOW + "Go to: " + Formatting.RESET + socialsLink[socialIndex]);
            }
        }

        GlScissorStack.clear();

        if (tooltipToDisplay != null) {
            TextRenderUtils.drawHoveringText(tooltipToDisplay, mouseX, mouseY, width, height, -1, fr);
        }

        GlStateManager.translated(0, 0, -2);
    }

    public boolean mouseInput(int mouseX, int mouseY) {
        Window scaledResolution = new Window(M.C);
        int width = scaledResolution.getWidth();
        int height = scaledResolution.getHeight();

        int xSize = Math.min(width - 100 / scaledResolution.getScaleFactor(), 500);
        int ySize = Math.min(height - 100 / scaledResolution.getScaleFactor(), 400);

        int x = (scaledResolution.getWidth() - xSize) / 2;
        int y = (scaledResolution.getHeight() - ySize) / 2;

        int adjScaleFactor = Math.max(2, scaledResolution.getScaleFactor());

        int innerPadding = 20 / adjScaleFactor;
        int innerTop = y + 49 + innerPadding;
        int innerBottom = y + ySize - 5 - innerPadding;
        int innerLeft = x + 149 + innerPadding;
        int innerRight = x + xSize - 5 - innerPadding;

        int dWheel = Mouse.getEventDWheel();
        if (mouseY > innerTop && mouseY < innerBottom && dWheel != 0) {
            if (dWheel < 0) {
                dWheel = -1;
            }
            if (dWheel > 0) {
                dWheel = 1;
            }
            if (mouseX < innerLeft) {
                int newTarget = categoryScroll.getTarget() - dWheel * 30;
                if (newTarget < 0) {
                    newTarget = 0;
                }

                float catBarSize = 1;
                int catY = -newTarget;
                for (Map.Entry<String, ConfigProcessor.ProcessedCategory> entry : getCurrentConfigEditing().entrySet()) {
                    if (getSelectedCategory() == null) {
                        setSelectedCategory(entry.getKey());
                    }

                    catY += 15;
                    if (catY > 0) {
                        catBarSize = LerpUtils.clampZeroOne((float) (innerBottom - innerTop - 2) / (catY + 5 + newTarget));
                    }
                }

                int barMax = (int) Math.floor((catY + 5 + newTarget) - catBarSize * (catY + 5 + newTarget));
                if (newTarget > barMax) {
                    newTarget = barMax;
                }
                categoryScroll.resetTimer();
                categoryScroll.setTarget(newTarget);
            } else {
                int newTarget = optionsScroll.getTarget() - dWheel * 30;
                if (newTarget < 0) {
                    newTarget = 0;
                }

                float barSize = 1;
                int optionY = -newTarget;
                if (getSelectedCategory() != null && getCurrentConfigEditing() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
                    ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
                    HashMap<Integer, Integer> activeAccordions = new HashMap<>();
                    for (ConfigProcessor.ProcessedOption option : getOptionsInCategory(cat).values()) {
                        if (option.accordionId >= 0) {
                            if (!activeAccordions.containsKey(option.accordionId)) {
                                continue;
                            }
                        }

                        GuiOptionEditor editor = option.editor;
                        if (editor == null) {
                            continue;
                        }
                        if (editor instanceof GuiOptionEditorAccordion) {
                            GuiOptionEditorAccordion accordion = (GuiOptionEditorAccordion) editor;
                            if (accordion.getToggled()) {
                                int accordionDepth = 0;
                                if (option.accordionId >= 0) {
                                    accordionDepth = activeAccordions.get(option.accordionId) + 1;
                                }
                                activeAccordions.put(accordion.getAccordionId(), accordionDepth);
                            }
                        }
                        optionY += editor.getHeight() + 5;

                        if (optionY > 0) {
                            barSize = LerpUtils.clampZeroOne((float) (innerBottom - innerTop - 2) / (optionY + 5 + newTarget));
                        }
                    }
                }

                int barMax = (int) Math.floor((optionY + 5 + newTarget) - barSize * (optionY + 5 + newTarget));
                if (newTarget > barMax) {
                    newTarget = barMax;
                }
                optionsScroll.setTimeToReachTarget(Math.min(150, Math.max(10, 5 * Math.abs(newTarget - optionsScroll.getValue()))));
                optionsScroll.resetTimer();
                optionsScroll.setTarget(newTarget);
            }
        } else if (Mouse.getEventButtonState() && Mouse.getEventButton() == 0) {
            if (getCurrentConfigEditing() != null) {
                int catY = -categoryScroll.getValue();
                for (Map.Entry<String, ConfigProcessor.ProcessedCategory> entry : getCurrentConfigEditing().entrySet()) {
                    if (getSelectedCategory() == null) {
                        setSelectedCategory(entry.getKey());
                    }
                    if (mouseX >= x + 5 && mouseX <= x + 145 && mouseY >= y + 70 + catY - 7 && mouseY <= y + 70 + catY + 7) {
                        setSelectedCategory(entry.getKey());
                        return true;
                    }
                    catY += 15;
                }
            }

            for (int socialIndex = 0; socialIndex < socialsLink.length; socialIndex++) {
                int socialLeft = x + xSize - 23 - 18 * socialIndex;

                if (mouseX >= socialLeft && mouseX <= socialLeft + 16 && mouseY >= y + 6 && mouseY <= y + 23) {
                    try {
                        Desktop.getDesktop().browse(new URI(socialsLink[socialIndex]));
                    } catch (Exception ignored) {}
                    return true;
                }
            }
        }

        int optionY = -optionsScroll.getValue();
        if (getSelectedCategory() != null && getCurrentConfigEditing() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
            int optionWidthDefault = innerRight - innerLeft - 20;
            ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
            HashMap<Integer, Integer> activeAccordions = new HashMap<>();
            for (ConfigProcessor.ProcessedOption option : getOptionsInCategory(cat).values()) {
                int optionWidth = optionWidthDefault;
                if (option.accordionId >= 0) {
                    if (!activeAccordions.containsKey(option.accordionId)) {
                        continue;
                    }
                    int accordionDepth = activeAccordions.get(option.accordionId);
                    optionWidth = optionWidthDefault - (2 * innerPadding) * (accordionDepth + 1);
                }

                GuiOptionEditor editor = option.editor;
                if (editor == null) {
                    continue;
                }
                if (editor instanceof GuiOptionEditorAccordion) {
                    GuiOptionEditorAccordion accordion = (GuiOptionEditorAccordion) editor;
                    if (accordion.getToggled()) {
                        int accordionDepth = 0;
                        if (option.accordionId >= 0) {
                            accordionDepth = activeAccordions.get(option.accordionId) + 1;
                        }
                        activeAccordions.put(accordion.getAccordionId(), accordionDepth);
                    }
                }
                if (editor.mouseInputOverlay((innerLeft + innerRight - optionWidth) / 2 - 5, innerTop + 5 + optionY, optionWidth, mouseX, mouseY)) {
                    return true;
                }
                optionY += editor.getHeight() + 5;
            }
        }

        if (mouseX > innerLeft && mouseX < innerRight && mouseY > innerTop && mouseY < innerBottom) {
            optionY = -optionsScroll.getValue();
            if (getSelectedCategory() != null && getCurrentConfigEditing() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
                int optionWidthDefault = innerRight - innerLeft - 20;
                ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
                HashMap<Integer, Integer> activeAccordions = new HashMap<>();
                for (ConfigProcessor.ProcessedOption option : getOptionsInCategory(cat).values()) {
                    int optionWidth = optionWidthDefault;
                    if (option.accordionId >= 0) {
                        if (!activeAccordions.containsKey(option.accordionId)) {
                            continue;
                        }
                        int accordionDepth = activeAccordions.get(option.accordionId);
                        optionWidth = optionWidthDefault - (2 * innerPadding) * (accordionDepth + 1);
                    }

                    GuiOptionEditor editor = option.editor;
                    if (editor == null) {
                        continue;
                    }
                    if (editor instanceof GuiOptionEditorAccordion) {
                        GuiOptionEditorAccordion accordion = (GuiOptionEditorAccordion) editor;
                        if (accordion.getToggled()) {
                            int accordionDepth = 0;
                            if (option.accordionId >= 0) {
                                accordionDepth = activeAccordions.get(option.accordionId) + 1;
                            }
                            activeAccordions.put(accordion.getAccordionId(), accordionDepth);
                        }
                    }
                    if (editor.mouseInput((innerLeft + innerRight - optionWidth) / 2 - 5, innerTop + 5 + optionY, optionWidth, mouseX, mouseY)) {
                        return true;
                    }
                    optionY += editor.getHeight() + 5;
                }
            }
        }

        return true;
    }

    public boolean keyboardInput() {
        Window scaledResolution = new Window(M.C);
        int width = scaledResolution.getWidth();

        int xSize = Math.min(width - 100 / scaledResolution.getScaleFactor(), 500);

        int adjScaleFactor = Math.max(2, scaledResolution.getScaleFactor());

        int innerPadding = 20 / adjScaleFactor;
        int innerWidth = xSize - 154 - innerPadding * 2;

        if (getSelectedCategory() != null && getCurrentConfigEditing() != null && getCurrentConfigEditing().containsKey(getSelectedCategory())) {
            ConfigProcessor.ProcessedCategory cat = getCurrentConfigEditing().get(getSelectedCategory());
            HashMap<Integer, Integer> activeAccordions = new HashMap<>();
            for (ConfigProcessor.ProcessedOption option : getOptionsInCategory(cat).values()) {
                if (option.accordionId >= 0) {
                    if (!activeAccordions.containsKey(option.accordionId)) {
                        continue;
                    }
                }

                GuiOptionEditor editor = option.editor;
                if (editor == null) {
                    continue;
                }
                if (editor instanceof GuiOptionEditorAccordion) {
                    GuiOptionEditorAccordion accordion = (GuiOptionEditorAccordion) editor;
                    if (accordion.getToggled()) {
                        int accordionDepth = 0;
                        if (option.accordionId >= 0) {
                            accordionDepth = activeAccordions.get(option.accordionId) + 1;
                        }
                        activeAccordions.put(accordion.getAccordionId(), accordionDepth);
                    }
                }
                if (editor.keyboardInput()) {
                    return true;
                }
            }
        }

        return true;
    }
}
