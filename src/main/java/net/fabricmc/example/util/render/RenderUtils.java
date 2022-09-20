package net.fabricmc.example.util.render;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import net.fabricmc.example.M;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public class RenderUtils {

    public static void drawFloatingRectDark(int x, int y, int width, int height) {
        drawFloatingRectDark(x, y, width, height, true);
    }

    public static void drawFloatingRectDark(int x, int y, int width, int height, boolean shadow) {
        int alpha = 0xf0000000;

        if (GLX.supportsFbo()) {
            Window scaledResolution = new Window(M.C);
            BackgroundBlur.renderBlurredBackground(15, scaledResolution.getWidth(), scaledResolution.getHeight(), x, y, width, height, true);
        } else {
            alpha = 0xff000000;
        }

        int main = alpha | 0x202026;
        int light = 0xff303036;
        int dark = 0xff101016;
        DrawableHelper.fill(x, y, x + 1, y + height, light); //Left
        DrawableHelper.fill(x + 1, y, x + width, y + 1, light); //Top
        DrawableHelper.fill(x + width - 1, y + 1, x + width, y + height, dark); //Right
        DrawableHelper.fill(x + 1, y + height - 1, x + width - 1, y + height, dark); //Bottom
        DrawableHelper.fill(x + 1, y + 1, x + width - 1, y + height - 1, main); //Middle
        if (shadow) {
            DrawableHelper.fill(x + width, y + 2, x + width + 2, y + height + 2, 0x70000000); //Right shadow
            DrawableHelper.fill(x + 2, y + height, x + width, y + height + 2, 0x70000000); //Bottom shadow
        }
    }

    public static void drawFloatingRect(int x, int y, int width, int height) {
        drawFloatingRectWithAlpha(x, y, width, height, 0xFF, true);
    }

    public static void drawFloatingRectWithAlpha(int x, int y, int width, int height, int alpha, boolean shadow) {
        int main = (alpha << 24) | 0xc0c0c0;
        int light = (alpha << 24) | 0xf0f0f0;
        int dark = (alpha << 24) | 0x909090;
        DrawableHelper.fill(x, y, x + 1, y + height, light); //Left
        DrawableHelper.fill(x + 1, y, x + width, y + 1, light); //Top
        DrawableHelper.fill(x + width - 1, y + 1, x + width, y + height, dark); //Right
        DrawableHelper.fill(x + 1, y + height - 1, x + width - 1, y + height, dark); //Bottom
        DrawableHelper.fill(x + 1, y + 1, x + width - 1, y + height - 1, main); //Middle
        if (shadow) {
            DrawableHelper.fill(x + width, y + 2, x + width + 2, y + height + 2, (alpha * 3 / 5) << 24); //Right shadow
            DrawableHelper.fill(x + 2, y + height, x + width, y + height + 2, (alpha * 3 / 5) << 24); //Bottom shadow
        }
    }

    public static void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height) {
        double f = 0.00390625;
        double f1 = 0.00390625;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder worldrenderer = tessellator.getBuffer();
        worldrenderer.begin(7, VertexFormats.POSITION_TEXTURE);
        worldrenderer.vertex(x + 0.0, y + height, 0.0)  .texture((textureX + 0.0) * f, (textureY + height) * f1).next();
        worldrenderer.vertex(x + width, y + height, 0.0).texture((textureX + width) * f, (textureY + height) * f1).next();
        worldrenderer.vertex(x + width, y + 0.0, 0.0)   .texture((textureX + width) * f, (textureY + 0.0) * f1).next();
        worldrenderer.vertex(x + 0.0, y + 0.0, 0.0)     .texture((textureX + 0.0) * f, (textureY + 0.0) * f1).next();
        tessellator.draw();
    }

    public static void drawTexturedRect(float x, float y, float width, float height) {
        drawTexturedRect(x, y, width, height, 0, 1, 0, 1);
    }

    public static void drawTexturedRect(float x, float y, float width, float height, int filter) {
        drawTexturedRect(x, y, width, height, 0, 1, 0, 1, filter);
    }

    public static void drawTexturedRect(float x, float y, float width, float height, float uMin, float uMax, float vMin, float vMax) {
        drawTexturedRect(x, y, width, height, uMin, uMax, vMin, vMax, GL11.GL_NEAREST);
    }

    public static void drawTexturedRect(float x, float y, float width, float height, float uMin, float uMax, float vMin, float vMax, int filter) {
        GlStateManager.enableBlend();
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawTexturedRectNoBlend(x, y, width, height, uMin, uMax, vMin, vMax, filter);

        GlStateManager.disableBlend();
    }

    public static void drawTexturedRectNoBlend(float x, float y, float width, float height, float uMin, float uMax, float vMin, float vMax, int filter) {
        GlStateManager.enableTexture();

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder worldrenderer = tessellator.getBuffer();
        worldrenderer.begin(7, VertexFormats.POSITION_TEXTURE);
        worldrenderer.vertex(x, y + height, 0.0D)            .texture(uMin, vMax).next();
        worldrenderer.vertex(x + width, y + height, 0.0D)  .texture(uMax, vMax).next();
        worldrenderer.vertex(x + width, y, 0.0D)             .texture(uMax, vMin).next();
        worldrenderer.vertex(x, y, 0.0D)                       .texture(uMin, vMin).next();
        tessellator.draw();

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }

    public static void drawGradientRect(int zLevel, int left, int top, int right, int bottom, int startColor, int endColor) {
        float startAlpha = (float) (startColor >> 24 & 255) / 255.0F;
        float startRed = (float) (startColor >> 16 & 255) / 255.0F;
        float startGreen = (float) (startColor >> 8 & 255) / 255.0F;
        float startBlue = (float) (startColor & 255) / 255.0F;
        float endAlpha = (float) (endColor >> 24 & 255) / 255.0F;
        float endRed = (float) (endColor >> 16 & 255) / 255.0F;
        float endGreen = (float) (endColor >> 8 & 255) / 255.0F;
        float endBlue = (float) (endColor & 255) / 255.0F;

        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder worldrenderer = tessellator.getBuffer();
        worldrenderer.begin(7, VertexFormats.POSITION_COLOR);
        worldrenderer.vertex(right, top, zLevel).color(startRed, startGreen, startBlue, startAlpha).next();
        worldrenderer.vertex(left, top, zLevel).color(startRed, startGreen, startBlue, startAlpha).next();
        worldrenderer.vertex(left, bottom, zLevel).color(endRed, endGreen, endBlue, endAlpha).next();
        worldrenderer.vertex(right, bottom, zLevel).color(endRed, endGreen, endBlue, endAlpha).next();
        tessellator.draw();

        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableTexture();
    }

    public static void drawInnerBox(int left, int top, int width, int height) {
        DrawableHelper.fill(left, top, left + width, top + height, 0x6008080E); //Middle
        DrawableHelper.fill(left, top, left + 1, top + height, 0xff08080E); //Left
        DrawableHelper.fill(left, top, left + width, top + 1, 0xff08080E); //Top
        DrawableHelper.fill(left + width - 1, top, left + width, top + height, 0xff28282E); //Right
        DrawableHelper.fill(left, top + height - 1, left + width, top + height, 0xff28282E); //Bottom
    }
}
