package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.Build
import android.util.Log

data class CarPlayUsbConfiguration(
    val id: Int,
    val interfaces: List<UsbInterface>,
    internal val platformConfiguration: Any? = null,
)

internal data class RawUsbInterfaceDescriptor(
    val id: Int,
    val alternateSetting: Int,
    val endpointCount: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
)

internal data class RawUsbConfigurationDescriptor(
    val id: Int,
    val interfaces: List<RawUsbInterfaceDescriptor>,
)

/** USB configuration descriptors retain the grouping that API 17's UsbDevice omits. */
internal fun parseRawUsbConfigurations(raw: ByteArray): List<RawUsbConfigurationDescriptor> {
    val configurations = mutableListOf<RawUsbConfigurationDescriptor>()
    var configurationId: Int? = null
    var interfaces = mutableListOf<RawUsbInterfaceDescriptor>()
    var offset = 0
    while (offset + 2 <= raw.size) {
        val length = raw[offset].toInt() and 0xff
        if (length < 2 || offset + length > raw.size) return emptyList()
        when (raw[offset + 1].toInt() and 0xff) {
            2 -> if (length >= 9) {
                configurationId?.let { configurations += RawUsbConfigurationDescriptor(it, interfaces) }
                configurationId = raw[offset + 5].toInt() and 0xff
                interfaces = mutableListOf()
            }
            4 -> if (length >= 9 && configurationId != null) {
                interfaces += RawUsbInterfaceDescriptor(
                    id = raw[offset + 2].toInt() and 0xff,
                    alternateSetting = raw[offset + 3].toInt() and 0xff,
                    endpointCount = raw[offset + 4].toInt() and 0xff,
                    interfaceClass = raw[offset + 5].toInt() and 0xff,
                    interfaceSubclass = raw[offset + 6].toInt() and 0xff,
                    interfaceProtocol = raw[offset + 7].toInt() and 0xff,
                )
            }
        }
        offset += length
    }
    if (offset != raw.size) return emptyList()
    configurationId?.let { configurations += RawUsbConfigurationDescriptor(it, interfaces) }
    return configurations
}

/**
 * Descriptor-based discovery of the iPhone's CarPlay configuration.
 *
 * Configuration ids differ between iPhone models, so the configuration is identified by its
 * interfaces: Apple USB Multiplexor (USBMUX) plus the NCM/Ethernet function CarPlay uses.
 */
object IphoneCarPlayConfiguration {
    const val TAG = "xcertplay-usb"

    private const val USBMUX_CLASS = 0xff
    private const val USBMUX_SUBCLASS = 0xfe
    private const val USBMUX_PROTOCOL = 0x02
    private const val APPLE_ETHERNET_CLASS = 0xff
    private const val APPLE_ETHERNET_SUBCLASS = 0xfd
    private const val APPLE_ETHERNET_PROTOCOL = 0x01
    private const val NCM_CONTROL_CLASS = 0x02
    private const val NCM_CONTROL_SUBCLASS = 0x0d
    private const val PREFERRED_USBMUX_OUT = 0x04
    private const val PREFERRED_USBMUX_IN = 0x85

    fun find(device: UsbDevice, rawDescriptors: ByteArray? = null): CarPlayUsbConfiguration? {
        val configurations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            platformConfigurations(device)
        } else if (rawDescriptors != null) {
            legacyConfigurations(device, rawDescriptors)
        } else {
            // Descriptor-only discovery before USB permission. Never use this synthetic id to
            // issue SET_CONFIGURATION; reopen with raw descriptors to resolve the actual id.
            listOf(CarPlayUsbConfiguration(1, (0 until device.interfaceCount).map(device::getInterface)))
        }
        val chosen = configurations.firstOrNull { usbMuxInterface(it) != null && hasCdcNcm(it) && hasAppleEthernet(it) }
            ?: configurations.firstOrNull { usbMuxInterface(it) != null && hasCdcNcm(it) }
        Log.i(
            TAG,
            "carplay config chosen=${chosen?.id} " +
                "available=${configurations.map { it.id }} detail=${chosen?.let(::describe)}",
        )
        return chosen
    }

    fun describe(configuration: CarPlayUsbConfiguration): String =
        configuration.interfaces.joinToString(",") { usbInterface ->
            "${usbInterface.id}/${alternateSetting(usbInterface)}" +
                ":${usbInterface.interfaceClass.toString(16)}" +
                ".${usbInterface.interfaceSubclass.toString(16)}" +
                ".${usbInterface.interfaceProtocol.toString(16)}" +
                "x${usbInterface.endpointCount}"
        }

    fun usbMuxInterface(configuration: CarPlayUsbConfiguration): UsbInterface? =
        configuration.interfaces.firstOrNull {
            it.interfaceClass == USBMUX_CLASS &&
                it.interfaceSubclass == USBMUX_SUBCLASS &&
                it.interfaceProtocol == USBMUX_PROTOCOL
        }

    fun usbMuxEndpoints(usbInterface: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
        val endpoints = (0 until usbInterface.endpointCount).map(usbInterface::getEndpoint)
        val out = endpoints.firstOrNull {
            it.address == PREFERRED_USBMUX_OUT &&
                it.direction == UsbConstants.USB_DIR_OUT &&
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        } ?: endpoints.singleOrNull {
            it.direction == UsbConstants.USB_DIR_OUT &&
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        }
        val input = endpoints.firstOrNull {
            it.address == PREFERRED_USBMUX_IN &&
                it.direction == UsbConstants.USB_DIR_IN &&
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        } ?: endpoints.singleOrNull {
            it.direction == UsbConstants.USB_DIR_IN &&
                it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
        }
        return if (out != null && input != null) out to input else null
    }

    private fun hasCdcNcm(configuration: CarPlayUsbConfiguration): Boolean =
        configuration.interfaces.any {
            it.interfaceClass == NCM_CONTROL_CLASS && it.interfaceSubclass == NCM_CONTROL_SUBCLASS
        }

    private fun hasAppleEthernet(configuration: CarPlayUsbConfiguration): Boolean =
        configuration.interfaces.any {
            it.interfaceClass == APPLE_ETHERNET_CLASS &&
                it.interfaceSubclass == APPLE_ETHERNET_SUBCLASS &&
                it.interfaceProtocol == APPLE_ETHERNET_PROTOCOL
        }

    private fun legacyConfigurations(device: UsbDevice, raw: ByteArray): List<CarPlayUsbConfiguration> {
        val descriptors = parseRawUsbConfigurations(raw)
        val expectedCount = descriptors.sumOf { it.interfaces.size }
        if (descriptors.isEmpty() || expectedCount != device.interfaceCount) {
            Log.w(TAG, "API 17 USB descriptor mismatch: configs=${descriptors.map { it.id }} " +
                "rawInterfaces=$expectedCount androidInterfaces=${device.interfaceCount}")
            return emptyList()
        }
        var index = 0
        return descriptors.map { descriptor ->
            val interfaces = descriptor.interfaces.map { rawInterface ->
                val usbInterface = device.getInterface(index++)
                if (usbInterface.id != rawInterface.id ||
                    usbInterface.interfaceClass != rawInterface.interfaceClass ||
                    usbInterface.interfaceSubclass != rawInterface.interfaceSubclass ||
                    usbInterface.interfaceProtocol != rawInterface.interfaceProtocol ||
                    usbInterface.endpointCount != rawInterface.endpointCount
                ) {
                    Log.w(TAG, "API 17 USB interface mismatch at index=${index - 1} config=${descriptor.id}")
                    return emptyList()
                }
                usbInterface
            }
            CarPlayUsbConfiguration(descriptor.id, interfaces)
        }
    }

    fun alternateSetting(usbInterface: UsbInterface): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) usbInterface.alternateSetting
        else if (usbInterface.interfaceClass == 0x0a && usbInterface.endpointCount > 0) 1 else 0

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun platformConfigurations(device: UsbDevice): List<CarPlayUsbConfiguration> =
        (0 until device.configurationCount).map { index ->
            val configuration: UsbConfiguration = device.getConfiguration(index)
            CarPlayUsbConfiguration(
                id = configuration.id,
                interfaces = (0 until configuration.interfaceCount).map(configuration::getInterface),
                platformConfiguration = configuration,
            )
        }

}
