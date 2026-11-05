package com.adshield.dns;

import android.net.VpnService;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class CloudflareDnsForwarder {

    private static final String TAG = "DnsForwarder";
    private static final int DNS_PORT = 53;
    private static final int DEFAULT_TIMEOUT_MS = 1200;

    private static final String[] GLOBAL_FALLBACKS = {"8.8.8.8", "9.9.9.9"};

    private final VpnService vpnService;
    private String primaryServer;
    private String secondaryServer;

    public CloudflareDnsForwarder(VpnService vpnService, String primaryServer, String secondaryServer) {
        this.vpnService = vpnService;
        this.primaryServer = (primaryServer != null) ? primaryServer : "1.1.1.1";
        this.secondaryServer = (secondaryServer != null) ? secondaryServer : "1.0.0.1";
    }

    public CloudflareDnsForwarder(VpnService vpnService) {
        this(vpnService, "1.1.1.1", "1.0.0.1");
    }

    public void setUpstreamServers(String primary, String secondary) {
        this.primaryServer = primary;
        this.secondaryServer = secondary;
    }

    public byte[] forward(byte[] dnsQuery) {
        // 1. Try primary configured server
        byte[] response = queryUdp(primaryServer, dnsQuery);
        if (response != null) {
            if (response.length >= 4 && (response[2] & 0x02) != 0) {
                byte[] tcpResp = queryTcp(primaryServer, dnsQuery);
                if (tcpResp != null) return tcpResp;
            }
            return response;
        }

        // 2. Try secondary configured server
        if (secondaryServer != null && !secondaryServer.isEmpty()) {
            response = queryUdp(secondaryServer, dnsQuery);
            if (response != null) {
                if (response.length >= 4 && (response[2] & 0x02) != 0) {
                    byte[] tcpResp = queryTcp(secondaryServer, dnsQuery);
                    if (tcpResp != null) return tcpResp;
                }
                return response;
            }
        }

        // 3. Fallback to resilient global upstream servers (Google 8.8.8.8 / Quad9 9.9.9.9)
        // Ensures cellular carriers never fail to resolve popular streaming or system APIs
        for (String fallbackIp : GLOBAL_FALLBACKS) {
            if (!fallbackIp.equals(primaryServer) && !fallbackIp.equals(secondaryServer)) {
                response = queryUdp(fallbackIp, dnsQuery);
                if (response != null) {
                    Log.d(TAG, "Upstream DNS resolved via fallback: " + fallbackIp);
                    return response;
                }
            }
        }

        return null;
    }

    private byte[] queryUdp(String serverIp, byte[] query) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            if (vpnService != null) {
                boolean protectedOk = vpnService.protect(socket);
                if (!protectedOk) {
                    Log.w(TAG, "Failed to protect upstream UDP socket");
                }
            }

            socket.setSoTimeout(DEFAULT_TIMEOUT_MS);
            InetAddress address = InetAddress.getByName(serverIp);
            DatagramPacket sendPacket = new DatagramPacket(query, query.length, address, DNS_PORT);
            socket.send(sendPacket);

            byte[] buffer = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);

            return Arrays.copyOf(buffer, receivePacket.getLength());
        } catch (SocketTimeoutException e) {
            Log.w(TAG, "UDP DNS query timeout to " + serverIp);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "UDP DNS error to " + serverIp + ": " + e.getMessage());
            return null;
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private byte[] queryTcp(String serverIp, byte[] query) {
        Socket socket = null;
        try {
            socket = new Socket();
            if (vpnService != null) {
                vpnService.protect(socket);
            }
            socket.setSoTimeout(DEFAULT_TIMEOUT_MS);
            socket.connect(new java.net.InetSocketAddress(serverIp, DNS_PORT), DEFAULT_TIMEOUT_MS);

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // TCP DNS prefixes packet with 2-byte big-endian length
            byte[] lengthPrefix = new byte[]{
                    (byte) ((query.length >> 8) & 0xFF),
                    (byte) (query.length & 0xFF)
            };
            out.write(lengthPrefix);
            out.write(query);
            out.flush();

            // Read 2-byte response length
            int lenHigh = in.read();
            int lenLow = in.read();
            if (lenHigh == -1 || lenLow == -1) return null;
            int respLen = (lenHigh << 8) | lenLow;

            byte[] resp = new byte[respLen];
            int read = 0;
            while (read < respLen) {
                int r = in.read(resp, read, respLen - read);
                if (r == -1) break;
                read += r;
            }
            return (read == respLen) ? resp : null;
        } catch (Exception e) {
            Log.w(TAG, "TCP DNS query error to " + serverIp + ": " + e.getMessage());
            return null;
        } finally {
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
