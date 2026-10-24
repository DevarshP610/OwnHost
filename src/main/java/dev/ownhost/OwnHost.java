package dev.ownhost;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(OwnHost.MOD_ID)
public final class OwnHost {
    public static final String MOD_ID = "ownhost";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final HostSession SESSION = new HostSession();
    private static final UpnpPortMapping PORT_MAPPING = new UpnpPortMapping(25565);

    public OwnHost(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    public static HostSession session() {
        return SESSION;
    }

    public static UpnpPortMapping.Result openPort() {
        return PORT_MAPPING.open();
    }

    public static void closePort() {
        PORT_MAPPING.close();
    }

    private void onServerStopping(ServerStoppingEvent event) {
        SESSION.stop();
        PORT_MAPPING.close();
    }
}
