package dev.ownhost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OwnHostSetupScreen extends Screen {
    private final Screen parent;

    public OwnHostSetupScreen(Screen parent) {
        super(Component.literal("OwnHost setup"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(
                        Component.literal(OwnHost.session().isRunning() ? "Stop hosting" : "Host this world"),
                        ignored -> {
                            OwnHostClient.toggleHosting(Minecraft.getInstance());
                            clearWidgets();
                            init();
                        })
                .bounds(center - 100, height / 2 - 10, 200, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        ignored -> Minecraft.getInstance().setScreen(parent))
                .bounds(center - 100, height / 2 + 20, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, title, center, height / 2 - 70, 0xFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.literal("One click publishes this world on port 25565."),
                center,
                height / 2 - 50,
                0xD0D0D0
        );
        graphics.drawCenteredString(
                font,
                Component.literal("Friends need the same Minecraft version, loader, and mods."),
                center,
                height / 2 - 35,
                0xD0D0D0
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
