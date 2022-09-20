package net.fabricmc.example.config.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.example.M;
import net.fabricmc.example.config.ChromaColour;
import net.fabricmc.example.config.elements.GuiElementColour;
import net.fabricmc.example.config.struct.ConfigProcessor;
import net.fabricmc.example.util.render.RenderUtils;
import org.lwjgl.input.Mouse;

import static net.fabricmc.example.config.GuiTextures.button_white;

public class GuiOptionEditorColour extends GuiOptionEditor {

    private String chromaColour;
    private GuiElementColour colourElement = null;

    public GuiOptionEditorColour(ConfigProcessor.ProcessedOption option) {
        super(option);
        this.chromaColour = (String) option.get();
    }

    @Override
    public void render(int x, int y, int width) {
        super.render(x, y, width);
        int height = getHeight();

        int argb = ChromaColour.specialToChromaRGB(chromaColour);
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        GlStateManager.color4f(r / 255f, g / 255f, b / 255f, 1);
        M.C.getTextureManager().bindTexture(button_white);
        RenderUtils.drawTexturedRect(x + width / 6 - 24, y + height - 7 - 14, 48, 16);
    }

    @Override
    public void renderOverlay(int x, int y, int width) {
        if (colourElement != null) {
            colourElement.render();
        }
    }

    @Override
    public boolean mouseInputOverlay(int x, int y, int width, int mouseX, int mouseY) {
        if (colourElement != null && colourElement.mouseInput(mouseX, mouseY)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseInput(int x, int y, int width, int mouseX, int mouseY) {
        int height = getHeight();

        if (Mouse.getEventButtonState() && Mouse.getEventButton() == 0 && mouseX > x + width / 6 - 24 && mouseX < x + width / 6 + 24 && mouseY > y + height - 7 - 14 && mouseY < y + height - 7 + 2) {
            colourElement =
                new GuiElementColour(
                    mouseX,
                    mouseY,
                    (String) option.get(),
                    val -> {
                        option.set(val);
                        chromaColour = val;
                    },
                    () -> colourElement = null
                );
        }

        return false;
    }

    @Override
    public boolean keyboardInput() {
        if (colourElement != null && colourElement.keyboardInput()) {
            return true;
        }

        return false;
    }
}
