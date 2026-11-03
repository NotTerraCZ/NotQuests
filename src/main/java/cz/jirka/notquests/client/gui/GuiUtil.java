package cz.jirka.notquests.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import cz.jirka.notquests.NotQuests;
import cz.jirka.notquests.quest.QuestShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GuiUtil {
    public static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(NotQuests.MOD_ID, "textures/gui/icons.png");
    private static final Map<QuestShape, ResourceLocation> SHAPE_FILL = new EnumMap<>(QuestShape.class);
    private static final Map<QuestShape, ResourceLocation> SHAPE_OUTLINE = new EnumMap<>(QuestShape.class);

    static {
        for (QuestShape s : QuestShape.REAL) {
            SHAPE_FILL.put(s, ResourceLocation.fromNamespaceAndPath(NotQuests.MOD_ID, "textures/gui/shapes/" + s.id() + ".png"));
            SHAPE_OUTLINE.put(s, ResourceLocation.fromNamespaceAndPath(NotQuests.MOD_ID, "textures/gui/shapes/" + s.id() + "_outline.png"));
        }
    }

    private GuiUtil() {
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    // ------------------------------------------------------------------ colors

    public static int withAlpha(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 255) * alpha);
        return (Math.max(0, Math.min(255, a)) << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        int a = (int) (((c1 >>> 24) & 255) + (((c2 >>> 24) & 255) - ((c1 >>> 24) & 255)) * t);
        int r = (int) (((c1 >> 16) & 255) + (((c2 >> 16) & 255) - ((c1 >> 16) & 255)) * t);
        int g = (int) (((c1 >> 8) & 255) + (((c2 >> 8) & 255) - ((c1 >> 8) & 255)) * t);
        int b = (int) ((c1 & 255) + ((c2 & 255) - (c1 & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 0..1..0 pulse with the given period in milliseconds. */
    public static float pulse(long period) {
        double t = (System.currentTimeMillis() % period) / (double) period;
        return (float) (0.5D - 0.5D * Math.cos(t * Math.PI * 2D));
    }

    // ------------------------------------------------------------------ primitives

    public static void rect(GuiGraphics g, float x1, float y1, float x2, float y2, int argb) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        vc.addVertex(m, x1, y1, 0F).setColor(argb);
        vc.addVertex(m, x1, y2, 0F).setColor(argb);
        vc.addVertex(m, x2, y2, 0F).setColor(argb);
        vc.addVertex(m, x2, y1, 0F).setColor(argb);
    }

    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001F) {
            return;
        }
        float nx = -dy / len * thickness / 2F;
        float ny = dx / len * thickness / 2F;
        // same winding as GuiGraphics.fill, the gui render type culls back faces
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = g.pose().last().pose();
        vc.addVertex(m, x1 - nx, y1 - ny, 0F).setColor(argb);
        vc.addVertex(m, x1 + nx, y1 + ny, 0F).setColor(argb);
        vc.addVertex(m, x2 + nx, y2 + ny, 0F).setColor(argb);
        vc.addVertex(m, x2 - nx, y2 - ny, 0F).setColor(argb);
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }

    /** Draws part of a texture with float coordinates and an ARGB tint. */
    public static void texture(GuiGraphics g, ResourceLocation tex, float x1, float y1, float x2, float y2,
                               float u1, float v1, float u2, float v2, int argb) {
        g.flush();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        float a = ((argb >>> 24) & 255) / 255F;
        float r = ((argb >> 16) & 255) / 255F;
        float gr = ((argb >> 8) & 255) / 255F;
        float b = (argb & 255) / 255F;
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.addVertex(m, x1, y1, 0F).setUv(u1, v1).setColor(r, gr, b, a);
        buf.addVertex(m, x1, y2, 0F).setUv(u1, v2).setColor(r, gr, b, a);
        buf.addVertex(m, x2, y2, 0F).setUv(u2, v2).setColor(r, gr, b, a);
        buf.addVertex(m, x2, y1, 0F).setUv(u2, v1).setColor(r, gr, b, a);
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    public static void icon(GuiGraphics g, Icon icon, float x, float y, float size, int argb) {
        texture(g, ICONS, x, y, x + size, y + size, icon.u0(), 0F, icon.u1(), 1F, argb);
    }

    public static void shape(GuiGraphics g, QuestShape shape, boolean outline, float cx, float cy, float size, int argb) {
        QuestShape s = shape == QuestShape.DEFAULT ? QuestShape.CIRCLE : shape;
        ResourceLocation tex = (outline ? SHAPE_OUTLINE : SHAPE_FILL).get(s);
        float h = size / 2F;
        texture(g, tex, cx - h, cy - h, cx + h, cy + h, 0F, 0F, 1F, 1F, argb);
    }

    /** Renders an item at any (float) position and size. */
    public static void item(GuiGraphics g, ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0F);
        g.pose().scale(size / 16F, size / 16F, 1F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    public static void itemWithCount(GuiGraphics g, ItemStack stack, int x, int y, String count) {
        g.renderItem(stack, x, y);
        if (count != null) {
            g.renderItemDecorations(font(), stack.copyWithCount(1), x, y, count);
        }
    }

    /** Lets everything drawn afterwards appear on top of items rendered before. */
    public static void clearDepth(GuiGraphics g) {
        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    // ------------------------------------------------------------------ text

    public static String trim(String s, int width) {
        Font f = font();
        if (f.width(s) <= width) {
            return s;
        }
        return f.plainSubstrByWidth(s, Math.max(0, width - f.width("..."))) + "...";
    }

    public static FormattedCharSequence trim(Component c, int width) {
        Font f = font();
        if (f.width(c) <= width) {
            return c.getVisualOrderText();
        }
        FormattedText cut = f.substrByWidth(c, Math.max(0, width - f.width("...")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("...")));
    }

    public static List<FormattedCharSequence> wrap(Component c, int width) {
        return font().split(c, Math.max(10, width));
    }

    public static void centeredString(GuiGraphics g, Component c, float cx, float y, int color, boolean shadow) {
        Font f = font();
        g.drawString(f, c, (int) (cx - f.width(c) / 2F), (int) y, color, shadow);
    }
}
