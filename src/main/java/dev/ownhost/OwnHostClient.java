package dev.ownhost;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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
            if (minecraft.screen instanceof Screen || !HOST_WORLD.consumeClick()) {
                return;
            }

            if (minecraft.getSingleplayerServer() == null || minecraft.player == null) {
                return;
            }

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
                    Component.literal(
                            "OwnHost is live on port " + HOST_PORT
                                    + ". Configure your router/firewall, then share your public address."
                    ),
                    false
            );
        }
    }
}
