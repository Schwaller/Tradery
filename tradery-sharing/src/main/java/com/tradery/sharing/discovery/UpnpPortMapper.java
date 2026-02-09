package com.tradery.sharing.discovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UPnP IGD port mapper using raw SSDP + SOAP (JDK only, no external library).
 * Attempts to create a TCP port mapping on the home router so remote peers can connect.
 *
 * Tries multiple strategies to maximize compatibility across routers:
 * 1. Same external port, 1-hour lease
 * 2. Same external port, permanent lease (some routers reject timed leases)
 * 3. Wildcard external port (let router pick), 1-hour lease
 * 4. Wildcard external port, permanent lease
 */
public class UpnpPortMapper {

    private static final Logger log = LoggerFactory.getLogger(UpnpPortMapper.class);

    private static final String SSDP_ADDR = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final int SSDP_TIMEOUT_MS = 3000;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("<errorCode>(\\d+)</errorCode>");
    private static final Pattern ERROR_DESC_PATTERN = Pattern.compile("<errorDescription>(.+?)</errorDescription>");

    private String controlUrl;
    private String serviceType;
    private int mappedExternalPort;

    public record Mapping(String externalIp, int externalPort) {}

    /**
     * Try to map localPort via UPnP. Returns Mapping on success, null if UPnP unavailable.
     */
    public Mapping mapPort(int localPort) {
        try {
            // 1. SSDP discovery
            String location = ssdpDiscover();
            if (location == null) {
                log.info("UPnP: no gateway found");
                return null;
            }
            log.debug("UPnP: found gateway at {}", location);

            // 2. Fetch device XML and find control URL
            String deviceXml = httpGet(location);
            parseControlUrl(deviceXml, location);
            if (controlUrl == null) {
                log.info("UPnP: no WANIPConnection/WANPPPConnection service found");
                return null;
            }
            log.debug("UPnP: control URL = {}, service = {}", controlUrl, serviceType);

            // 3. Get local IP
            String localIp = getLocalIp();

            // 4. Try port mapping with multiple strategies
            int extPort = tryAddPortMapping(localIp, localPort);
            if (extPort <= 0) {
                log.info("UPnP: all port mapping strategies failed");
                return null;
            }
            mappedExternalPort = extPort;

            // 5. Get external IP
            String externalIp = getExternalIp();

            log.info("UPnP: mapped port {} → {}:{}", localPort, externalIp, extPort);
            return new Mapping(externalIp, extPort);

        } catch (Exception e) {
            log.info("UPnP: port mapping failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Remove the port mapping (call on shutdown).
     */
    public void unmap() {
        if (controlUrl == null || mappedExternalPort == 0) return;
        try {
            String body = soapEnvelope("DeletePortMapping",
                    "<NewRemoteHost></NewRemoteHost>"
                    + "<NewExternalPort>" + mappedExternalPort + "</NewExternalPort>"
                    + "<NewProtocol>TCP</NewProtocol>");

            soapPost("DeletePortMapping", body);
            log.info("UPnP: unmapped port {}", mappedExternalPort);
        } catch (Exception e) {
            log.debug("UPnP: unmap failed: {}", e.getMessage());
        }
    }

    /**
     * Try multiple mapping strategies. Returns mapped external port, or -1 on failure.
     */
    private int tryAddPortMapping(String localIp, int localPort) {
        // Strategy combinations: (externalPort, leaseDuration)
        int[][] strategies = {
                {localPort, 3600},   // same port, 1h lease
                {localPort, 0},      // same port, permanent (some routers only allow this)
                {0, 3600},           // wildcard port, 1h lease
                {0, 0},              // wildcard port, permanent
        };

        for (int[] s : strategies) {
            int extPort = s[0];
            int lease = s[1];
            try {
                String responseXml = sendAddPortMapping(localIp, localPort, extPort, lease);
                // Success — parse the actual mapped port from response if we requested wildcard
                if (extPort == 0) {
                    Matcher m = Pattern.compile("<NewReservedPort>(\\d+)</NewReservedPort>").matcher(responseXml);
                    if (m.find()) return Integer.parseInt(m.group(1));
                    // Some routers don't return it in response; re-read our mapping
                    // Fall back to using the local port as a guess (router often mirrors it)
                    return localPort;
                }
                return extPort;
            } catch (SoapFaultException e) {
                log.debug("UPnP: AddPortMapping(ext={}, lease={}) → error {}: {}",
                        extPort, lease, e.errorCode, e.errorDescription);
            } catch (Exception e) {
                log.debug("UPnP: AddPortMapping(ext={}, lease={}) → {}", extPort, lease, e.getMessage());
            }
        }
        return -1;
    }

    private String sendAddPortMapping(String localIp, int internalPort, int externalPort, int leaseDuration)
            throws Exception {
        String body = soapEnvelope("AddPortMapping",
                "<NewRemoteHost></NewRemoteHost>"
                + "<NewExternalPort>" + externalPort + "</NewExternalPort>"
                + "<NewProtocol>TCP</NewProtocol>"
                + "<NewInternalPort>" + internalPort + "</NewInternalPort>"
                + "<NewInternalClient>" + localIp + "</NewInternalClient>"
                + "<NewEnabled>1</NewEnabled>"
                + "<NewPortMappingDescription>Plaiiin</NewPortMappingDescription>"
                + "<NewLeaseDuration>" + leaseDuration + "</NewLeaseDuration>");

        return soapPost("AddPortMapping", body);
    }

    private String soapEnvelope(String action, String innerXml) {
        return "<?xml version=\"1.0\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:" + action + " xmlns:u=\"" + serviceType + "\">"
                + innerXml
                + "</u:" + action + "></s:Body></s:Envelope>";
    }

    private String getExternalIp() throws Exception {
        String response = soapPost("GetExternalIPAddress",
                soapEnvelope("GetExternalIPAddress", ""));
        Matcher m = Pattern.compile("<NewExternalIPAddress>(.+?)</NewExternalIPAddress>").matcher(response);
        return m.find() ? m.group(1).trim() : "unknown";
    }

    private String ssdpDiscover() throws IOException {
        String search = "M-SEARCH * HTTP/1.1\r\n"
                + "HOST: " + SSDP_ADDR + ":" + SSDP_PORT + "\r\n"
                + "MAN: \"ssdp:discover\"\r\n"
                + "MX: 3\r\n"
                + "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n"
                + "\r\n";

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(SSDP_TIMEOUT_MS);
            byte[] data = search.getBytes(StandardCharsets.UTF_8);
            InetAddress addr = InetAddress.getByName(SSDP_ADDR);
            socket.send(new DatagramPacket(data, data.length, addr, SSDP_PORT));

            byte[] buf = new byte[2048];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);

            String resp = new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("(?i)LOCATION:\\s*(.+?)\\r?\\n").matcher(resp);
            return m.find() ? m.group(1).trim() : null;
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    private void parseControlUrl(String xml, String locationUrl) {
        String[] serviceTypes = {
                "urn:schemas-upnp-org:service:WANIPConnection:1",
                "urn:schemas-upnp-org:service:WANPPPConnection:1"
        };

        for (String st : serviceTypes) {
            int idx = xml.indexOf(st);
            if (idx < 0) continue;

            String sub = xml.substring(idx);
            Matcher m = Pattern.compile("<controlURL>(.+?)</controlURL>").matcher(sub);
            if (m.find()) {
                String path = m.group(1).trim();
                this.serviceType = st;
                if (path.startsWith("http")) {
                    this.controlUrl = path;
                } else {
                    try {
                        URI base = URI.create(locationUrl);
                        String baseStr = base.getScheme() + "://" + base.getAuthority();
                        this.controlUrl = path.startsWith("/") ? baseStr + path : baseStr + "/" + path;
                    } catch (Exception e) {
                        this.controlUrl = path;
                    }
                }
                return;
            }
        }
    }

    private String soapPost(String action, String body) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(controlUrl))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("SOAPAction", "\"" + serviceType + "#" + action + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            // Parse SOAP fault for UPnP error code
            String responseBody = response.body();
            int errorCode = 0;
            String errorDesc = "HTTP " + response.statusCode();
            if (responseBody != null) {
                Matcher cm = ERROR_CODE_PATTERN.matcher(responseBody);
                if (cm.find()) errorCode = Integer.parseInt(cm.group(1));
                Matcher dm = ERROR_DESC_PATTERN.matcher(responseBody);
                if (dm.find()) errorDesc = dm.group(1).trim();
            }
            throw new SoapFaultException(action, errorCode, errorDesc);
        }
        return response.body();
    }

    private String httpGet(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String getLocalIp() throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 80);
            return socket.getLocalAddress().getHostAddress();
        }
    }

    private static class SoapFaultException extends IOException {
        final int errorCode;
        final String errorDescription;

        SoapFaultException(String action, int errorCode, String errorDescription) {
            super("SOAP " + action + " failed: " + errorCode + " " + errorDescription);
            this.errorCode = errorCode;
            this.errorDescription = errorDescription;
        }
    }
}
