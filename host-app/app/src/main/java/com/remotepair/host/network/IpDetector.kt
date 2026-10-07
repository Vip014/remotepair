package com.remotepair.host.network

import java.net.Inet4Address
import java.net.NetworkInterface

object IpDetector {
    /**
     * Returns the device's current local IPv4 address, or null if offline.
     * Prefers Wi-Fi/Ethernet over mobile.
     */
    fun localIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            // Prefer wlan/eth over mobile
            val ordered = interfaces.sortedBy { iface ->
                when {
                    iface.name.startsWith("wlan") -> 0
                    iface.name.startsWith("eth") -> 1
                    iface.name.startsWith("rmnet") -> 2
                    else -> 3
                }
            }
            for (iface in ordered) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
