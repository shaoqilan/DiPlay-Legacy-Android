# Existing Wi-Fi / same-LAN mode on API 17

This Legacy backport adds an Existing Wi-Fi option for wireless CarPlay. It keeps the existing Bluetooth RFCOMM/iAP2 startup sequence and sends the saved Wi-Fi credentials to the iPhone over Bluetooth. AirPlay discovery, control, audio, and video then use the car's already-connected Wi-Fi station interface.

DiPlay does not start a hotspot, join a Wi-Fi network, change the Wi-Fi state, or change the default route in this mode. Join both the car and iPhone to the same router or portable Wi-Fi network first, then configure that network's exact SSID and password in Connection setup and start wireless CarPlay. Leave the password empty for an open network. WPA2-Personal is the supported secured network mode.

The API 17 implementation uses `WifiManager.getConnectionInfo()` and `NetworkInterface` to find the active Wi-Fi interface. It obtains the AP channel from cached scan results when available, otherwise iAP2 receives channel 0 (automatic). When a scoped link-local IPv6 address is available, it is listed first in the Bluetooth StartSession request, followed by IPv4. Bonjour and AirPlay listen on both addresses using the same receiver identity and port. IPv4 is used alone when scoped IPv6 is unavailable.

The router must allow client-to-client traffic and Bonjour/mDNS multicast between the car and iPhone. Guest networks, AP/client isolation, WPA3-only, enterprise authentication, and captive portals are unsupported. If Android masks the live SSID, the entered SSID is used as the user's confirmation; otherwise a readable mismatch stops the connection.

Wireless startup must advertise the receiver's real Bluetooth MAC, not the iPhone MAC or a generated AirPlay identifier. When firmware hides the local Bluetooth address, Connection setup provides an optional receiver Bluetooth MAC override. The SM-C5000 test device uses this setting; API 17 devices normally obtain the address from BluetoothAdapter. The application now stops with a setup message when neither the system nor the override supplies a real address.

The startup screen reports authentication completion and the wait for an AirPlay session after StartSession. If the session is still absent after 45 seconds, it shows a waiting/check-pairing message while keeping the current attempt available.

## SM-C5000 test, 2026-10-07

- Corrected the receiver Bluetooth MAC override; RFCOMM and MFi authentication succeed.
- After removing the old Bluetooth pairing and pairing again, the user confirmed DiPlay appears in the iPhone CarPlay vehicle list.
- The receiver binds IPv6 and IPv4 port 7000. StartSession now includes both addresses instead of only IPv6.
- Standalone APK build and installation succeeded; APK metadata still reports minSdk 17.
- All six Iap2WirelessControlClientTest tests passed, including the two-address wire encoding test.
- No incoming AirPlay session was observed after the latest install. A Windows same-LAN mDNS probe received no response from the receiver; this alone does not identify a router or application fault.
- The mapped Z: output directory was unavailable during this build. The existing local Same-LAN APK was overwritten.

After the iPhone rejoined Wi-Fi, the user confirmed CarPlay opened. The home Connect Phone button was then corrected to use the saved wireless/USB selection instead of always starting USB. Its label now identifies the selected transport. On-device verification of this button started Existing Wi-Fi mode, passed Bluetooth authentication, accepted an incoming IPv6 AirPlay connection, and completed authentication over the iAP tunnel.
