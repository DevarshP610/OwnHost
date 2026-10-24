package dev.ownhost;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal UPnP IGD client for the host's local router. It only maps the
 * dedicated OwnHost port and never exposes or stores router credentials.
 */
public final class UpnpPortMapping {
    private static final String SEARCH_TARGET = "urn:schemas-upnp-org:device:InternetGatewayDevice:1";
    private static final int SSDP_PORT = 1900;
    private static final int DISCOVERY_TIMEOUT_MILLIS = 1500;
    private static final Pattern LOCATION = Pattern.compile(
            "(?im)^location\\s*:\\s*(\\S+)\\s*$"
    );
    private static final Pattern CONTROL_URL = Pattern.compile(
            "(?is)<controlURL>\\s*(.*?)\\s*</controlURL>"
    );
    private static final Pattern SERVICE_TYPE = Pattern.compile(
            "(?is)<serviceType>\\s*(.*?)\\s*</serviceType>"
    );
    private static final Pattern EXTERNAL_IP = Pattern.compile(
            "(?is)<NewExternalIPAddress>\\s*(.*?)\\s*</NewExternalIPAddress>"
    );
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final int port;
    private URI controlUrl;
    private String serviceType;
    private boolean mapped;

    public UpnpPortMapping(int port) {
        this.port = port;
    }

    public synchronized Result open() {
        if (mapped) {
            return Result.success("UPnP mapping is already active.");
        }
        try {
            URI deviceDescription = discoverDevice();
            if (deviceDescription == null) {
                return Result.failure("No UPnP router was found.");
            }
            String description = get(deviceDescription);
            controlUrl = findControlUrl(deviceDescription, description);
            if (controlUrl == null) {
                return Result.failure("The UPnP device has no WAN port-mapping service.");
            }
            String localAddress = localAddress();
            soap("AddPortMapping", """
                    <NewRemoteHost></NewRemoteHost>
                    <NewExternalPort>%d</NewExternalPort>
                    <NewProtocol>TCP</NewProtocol>
                    <NewInternalPort>%d</NewInternalPort>
                    <NewInternalClient>%s</NewInternalClient>
                    <NewEnabled>1</NewEnabled>
                    <NewPortMappingDescription>OwnHost temporary hosting</NewPortMappingDescription>
                    <NewLeaseDuration>28800</NewLeaseDuration>
                    """.formatted(port, port, localAddress));
            mapped = true;
            String externalIp = externalIp();
            return Result.success(
                    externalIp == null
                            ? "UPnP opened port " + port + "."
                            : "UPnP opened " + externalIp + ":" + port + "."
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            controlUrl = null;
            return Result.failure("UPnP could not open port " + port + ".");
        } catch (IOException | RuntimeException exception) {
            controlUrl = null;
            return Result.failure("UPnP could not open port " + port + ".");
        }
    }

    public synchronized void close() {
        if (!mapped || controlUrl == null) {
            return;
        }
        try {
            soap("DeletePortMapping", """
                    <NewRemoteHost></NewRemoteHost>
                    <NewExternalPort>%d</NewExternalPort>
                    <NewProtocol>TCP</NewProtocol>
                    """.formatted(port));
        } catch (IOException | InterruptedException | RuntimeException exception) {
            OwnHost.LOGGER.warn("Could not remove the OwnHost UPnP mapping.", exception);
        } finally {
            mapped = false;
            controlUrl = null;
            serviceType = null;
        }
    }

    private URI discoverDevice() throws IOException {
        byte[] request = (
                "M-SEARCH * HTTP/1.1\r\n"
                        + "HOST: 239.255.255.250:1900\r\n"
                        + "MAN: \"ssdp:discover\"\r\n"
                        + "MX: 1\r\n"
                        + "ST: " + SEARCH_TARGET + "\r\n\r\n"
        ).getBytes(StandardCharsets.US_ASCII);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(DISCOVERY_TIMEOUT_MILLIS);
            socket.send(new DatagramPacket(
                    request,
                    request.length,
                    InetAddress.getByName("239.255.255.250"),
                    SSDP_PORT
            ));
            byte[] response = new byte[8192];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            try {
                socket.receive(packet);
            } catch (java.net.SocketTimeoutException timeout) {
                return null;
            }
            Matcher matcher = LOCATION.matcher(
                    new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.US_ASCII)
            );
            return matcher.find() ? URI.create(matcher.group(1).trim()) : null;
        }
    }

    private String get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("UPnP device description returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private URI findControlUrl(URI descriptionUrl, String description) {
        Matcher types = SERVICE_TYPE.matcher(description);
        Matcher urls = CONTROL_URL.matcher(description);
        while (types.find() && urls.find()) {
            String type = types.group(1).trim().toLowerCase(Locale.ROOT);
            if (type.contains("wanipconnection") || type.contains("wanpppconnection")) {
                serviceType = types.group(1).trim();
                return descriptionUrl.resolve(urls.group(1).trim());
            }
        }
        return null;
    }

    private String externalIp() throws IOException, InterruptedException {
        String response = soap("GetExternalIPAddress", "");
        Matcher matcher = EXTERNAL_IP.matcher(response);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String soap(String action, String arguments) throws IOException, InterruptedException {
        String body = """
                <?xml version="1.0"?>
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                  <s:Body>
                    <u:%s xmlns:u="%s">
                      %s
                    </u:%s>
                  </s:Body>
                </s:Envelope>
                """.formatted(action, serviceType, arguments, action);
        HttpRequest request = HttpRequest.newBuilder(controlUrl)
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("SOAPAction", "\"" + serviceType + "#" + action + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("UPnP action returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String localAddress() throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            return socket.getLocalAddress().getHostAddress();
        }
    }

    public record Result(boolean success, String message) {
        private static Result success(String message) {
            return new Result(true, message);
        }

        private static Result failure(String message) {
            return new Result(false, message);
        }
    }
}
