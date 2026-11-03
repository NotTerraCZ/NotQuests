package cz.jirka.notquests.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** Multi line text editor for descriptions and subtitles. One list entry per line. */
public class TextListScreen extends Screen {
    private final Screen parent;
    private final Consumer<List<String>> callback;
    private String text;
    private MultiLineEditBox box;

    public TextListScreen(Screen parent, Component title, List<String> lines, Consumer<List<String>> callback) {
        super(title);
        this.parent = parent;
        this.callback = callback;
        this.text = String.join("\n", lines);
    }

    @Override
    protected void init() {
        int w = Math.min(420, width - 20);
        int h = height - 80;
        box = new MultiLineEditBox(font, (width - w) / 2, 30, w, h, Component.translatable("notquests.gui.text_hint"), title);
        box.setCharacterLimit(65536);
        box.setValue(text);
        box.setValueListener(s -> text = s);
        addRenderableWidget(box);
        setInitialFocus(box);
        int bw = 100;
        addRenderableWidget(new FlatButton(width / 2 - bw - 4, height - 26, bw, 18, Component.translatable("notquests.gui.save"), b -> {
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
            while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                lines.remove(lines.size() - 1);
            }
            minecraft.setScreen(parent);
            callback.accept(lines);
        }).textColor(() -> Theme.COMPLETE));
        addRenderableWidget(new FlatButton(width / 2 + 4, height - 26, bw, 18, Component.translatable("notquests.gui.cancel"), b -> minecraft.setScreen(parent)));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Theme.SCREEN);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        GuiUtil.centeredString(g, title, width / 2F, 10, Theme.TEXT, false);
        GuiUtil.centeredString(g, Component.translatable("notquests.gui.format_hint"), width / 2F, height - 40, Theme.TEXT_DARK, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
