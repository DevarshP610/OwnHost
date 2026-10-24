package dev.ownhost;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = OwnHost.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class OwnHostClient {
    private static final int HOST_PORT = 25565;
    private static final KeyMapping HOST_WORLD = new KeyMapping(
            "key.ownhost.host_world",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.ownhost"
    );

    private OwnHostClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(HOST_WORLD);
    }

    @EventBusSubscriber(modid = OwnHost.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen != null || !HOST_WORLD.consumeClick()) {
                return;
            }

            openSetup(minecraft);
        }

        @SubscribeEvent
        public static void addPauseMenuButton(ScreenEvent.Init.Post event) {
            if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) {
                return;
            }

            event.addListener(Button.builder(
                            Component.literal("Host with OwnHost"),
                            ignored -> openSetup(Minecraft.getInstance())
                    ).bounds(event.getScreen().width / 2 - 100, event.getScreen().height / 4 + 72, 200, 20)
                    .build());
        }
    }

    private static void openSetup(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null || minecraft.player == null) {
            return;
        }
        minecraft.setScreen(new OwnHostSetupScreen(minecraft.screen));
    }

    static void toggleHosting(Minecraft minecraft) {
        if (OwnHost.session().isRunning()) {
            OwnHost.session().stop();
            minecraft.player.displayClientMessage(Component.literal("OwnHost stopped."), false);
            return;
        }
        if (!minecraft.getSingleplayerServer().publishServer(GameType.SURVIVAL, false, HOST_PORT)) {
            minecraft.player.displayClientMessage(
                    Component.literal("OwnHost could not publish this world on port " + HOST_PORT + "."),
                    false
            );
            return;
        }
        OwnHost.session().start();
        minecraft.player.displayClientMessage(
                Component.literal("OwnHost is live on port " + HOST_PORT + ". Configure your router/firewall, then share your public address."),
                false
        );
    }
}
