package com.shilapi.xcertplay.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Looper
import com.shilapi.xcertplay.transport.Iap2WirelessSecurity
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/** Uses the already-connected Wi-Fi station interface. This class never changes Wi-Fi state. */
class ExistingWifiManager(
    context: Context,
    private val expectedSsid: String,
    private val passphrase: String,
    security: Iap2WirelessSecurity,
    private val onDiagnostic: (String) -> Unit = {},
) : WirelessHotspotManager {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val security = security
    @Volatile private var closed = false

    override fun start(timeoutMillis: Long): WirelessHotspotInfo {
        check(Looper.myLooper() != Looper.getMainLooper()) { "ExistingWifiManager.start must not run on the main thread" }
        if (!wifi.isWifiEnabled) throw IOException("Connect the car to the same Wi-Fi network as the iPhone first")
        val info = try { wifi.connectionInfo } catch (e: Exception) {
            throw IOException("Could not read the active Wi-Fi connection", e)
        } ?: throw IOException("No active Wi-Fi connection; join the car and iPhone to the same LAN")
        val liveSsid = unquote(info.ssid)
        if (liveSsid.isNotEmpty() && liveSsid != "<unknown ssid>" && liveSsid != expectedSsid) {
            throw IOException("Connected Wi-Fi is '$liveSsid', but saved network is '$expectedSsid'")
        }
        val address = ipv4FromLittleEndian(info.ipAddress)
            ?: throw IOException("The active Wi-Fi interface has no IPv4 address")
        val networkInterface = Collections.list(NetworkInterface.getNetworkInterfaces() ?: return noInterface())
            .firstOrNull { network ->
                runCatching { network.isUp && !network.isLoopback && Collections.list(network.inetAddresses).any { it == address } }.getOrDefault(false)
            } ?: throw IOException("Could not locate the active Wi-Fi interface for $address")
        if (closed) throw IOException("Existing Wi-Fi session was cancelled")
        val ipv6 = Collections.list(networkInterface.inetAddresses).filterIsInstance<Inet6Address>()
            .firstOrNull { it.isLinkLocalAddress && it.scopeId > 0 }
        val addresses = listOfNotNull(ipv6, address)
        onDiagnostic("Existing Wi-Fi: ssidMatch=${liveSsid == expectedSsid} iface=${networkInterface.name} ipv4=$address; hotspot creation disabled")
        val bssid = info.bssid?.takeUnless { it == "02:00:00:00:00:00" || it == "00:00:00:00:00:00" }
        val frequency = runCatching { wifi.scanResults.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }?.frequency }
            .getOrNull()?.takeIf { it > 0 }
        return WirelessHotspotInfo(
            ssid = expectedSsid,
            passphrase = passphrase,
            security = security,
            channel = frequency?.let(::wifiFrequencyMhzToChannel) ?: 0,
            frequencyMHz = frequency,
            bssid = bssid,
            interfaceName = networkInterface.name,
            hostAddress = addresses.first(),
            bandLabel = "Existing Wi-Fi",
            backend = WirelessHotspotBackend.EXISTING_WIFI,
            hostAddresses = addresses,
        )
    }

    override fun close() { closed = true }

    private fun ipv4FromLittleEndian(value: Int): Inet4Address? {
        if (value == 0) return null
        val bytes = byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte())
        return InetAddress.getByAddress(bytes) as? Inet4Address
    }

    private fun noInterface(): Nothing = throw IOException("No active network interfaces found")
    private fun unquote(ssid: String?): String = ssid.orEmpty().removeSurrounding("\"")
}
