# OwnHost

OwnHost is a standalone Minecraft hosting mod. It is separate from YardScout
and is being built with a shared hosting core plus loader-specific adapters.
The first adapter targets NeoForge for Minecraft 1.21.1.

## Hosting model

OwnHost publishes the host player's single-player world directly from their
computer. It does not use LAN discovery and does not require a dedicated
server. The host can use UPnP to request a port mapping, or configure the
same port manually in their router. Friends connect using the public address
and port.

The first release is intentionally direct-hosting only:

- It does not depend on a relay server.
- The host can stop the published session when leaving the world.
- NeoForge's normal login handshake checks that joining clients have the
  required server-side mods and compatible versions.
- OwnHost requests a temporary UPnP TCP mapping for port 25565 when hosting
  starts and removes it when hosting stops.

Direct hosting requires a reachable public IPv4 address. Carrier-grade NAT,
strict firewalls, and some school or cellular networks can prevent incoming
connections; a future relay adapter can support those networks. The current
Minecraft login protocol does not provide a safe place for a custom token
before a connection is accepted, so OwnHost does not pretend its local
session token is an access-control boundary. Use Minecraft's whitelist and
router firewall while direct hosting is enabled.

If the router does not support UPnP, OwnHost still starts the world and tells
the host to forward TCP port 25565 manually. UPnP should remain enabled only
on a trusted home network; the router's firewall is still the boundary for
internet traffic.

OwnHost does not download or execute mods from a host. Joining players must
install the same Minecraft version, loader, and modpack through a trusted
launcher. Fabric and Forge support require separate loader adapters; no single
NeoForge JAR can load on those platforms.

## Development

Requirements:

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.235

Run the checks with:

```powershell
.\gradlew.bat build
```

Fabric and Forge support will use the same hosting contract with separate
loader adapters. The loader-specific implementation is intentionally kept out
of the shared lifecycle logic. A future relay/control protocol will add
short-lived invite tokens and admission rate limits before the relay forwards
Minecraft traffic.
