# Building DiPlay

Requirements for the API 17 build: JDK 21 (verified), Android SDK 37, and the included Gradle wrapper. Normal builds with JNI also require NDK 23.2.8568313, whose native minimum includes API 17.

The standard `mobile`, `common`, and `shared` modules now have `minSdk 17`. The separate Android Automotive application retains its API 28 minimum. A normal build packages ARM64, ARMv7, x86_64 and x86 JNI libraries; the wired-only build below omits the wireless-radio and I2C JNI libraries.

## Android 4.2 / API 17 wired test build

Set `DIPLAY_WIRED_ONLY=true` when building the mobile app for USB CarPlay on Android 4.2. This skips the wireless-radio and Linux I2C JNI libraries. Linux I2C MFi authentication is unavailable in this mode; a USB CH341 backend remains in Java but has not been device-tested. The normal build still uses NDK 23.2.8568313 and targets native API 17. On Windows, if the checkout path contains non-ASCII characters, Gradle needs the included `android.overridePathCheck=true` setting; NDK builds may still fail on that path, so use the wired-only switch.

```powershell
$env:DIPLAY_WIRED_ONLY = 'true'
$env:ANDROID_HOME = 'C:\path\to\android-sdk'
$env:JAVA_HOME = 'C:\path\to\jdk-21'
.\gradlew.bat :mobile:assembleDebug :mobile:lintDebug
```

The output is `mobile/build/outputs/apk/debug/mobile-debug.apk` with package ID `com.shihab.diplay.hudtest`. It declares API 17 as its minimum, but installation has not yet been checked on a G6S. This source-only APK has no bundled offline MFi identity, so its CarPlay authentication is unverified and depends on separately available MFi hardware or service. For a usable standalone car test, provide the external authentication assets described below and run `:mobile:assembleStandaloneDebug` with the same `DIPLAY_WIRED_ONLY=true` setting. The APK must then be checked for both `assets/offline-mfi/identity.pk8` and `assets/offline-mfi/certificate.p7b` before use.

### 2026-10-04 migration record

- Lowered the mobile, common, and shared minimum from API 19 to API 17, including the native platform target.
- Switched AndroidX Core from 1.13.1 to 1.12.0 after the former blocked the API 17 manifest merge.
- Selected NDK r23c because r25c cannot target native API 17, and added the wired-only build switch for checkouts with non-ASCII paths.
- Verified `:mobile:assembleDebug` and `:mobile:lintDebug` with the wired-only switch. Device installation, USB handoff, codec playback, and authentication still require testing on the G6S.

### 2026-10-05 G6S wired USB follow-up

- The G6S API 17 log now confirms Apple USB device `05ac:12a8`, granted USB permission, and a one-byte response to the CarPlay configuration request. It then stops while waiting for updated USB descriptors; this is before iAP2 or MFi exchange with the phone.
- After the request, the app now polls for the CarPlay USB configuration every two seconds because the head unit did not deliver another attach event. Descriptor changes are recorded in the diagnostic report, and a 30-second timeout reports when the configuration never appears.
- The API 17 emulator has no attached iPhone, so it verifies installation and ordinary USB discovery only. The new re-enumeration path needs another G6S test.
- The next G6S log reached USBMUX/NCM discovery, but `SET_INTERFACE` for NCM data alternate setting failed repeatedly. API 17 exposes a flattened interface list without configuration ids, and the app had treated that list as configuration `1`. The USB connection now reads raw descriptors, groups interfaces by their real configuration ids, validates their order against Android's list, and selects the matching CarPlay configuration. This device-specific fix remains unverified until another G6S run.
- The following G6S log confirms `ncm config=6` and then has no later stage for over a minute. A later diagnostic build records progress before and after the NCM MAC read, interface claims, and alternate-setting selection; if an operation remains blocked for five seconds, it records the worker thread's stack in the report.
- Those NCM progress points now update the CarPlay connection screen in Chinese or English as well as the diagnostic report, including a visible five-second waiting message.
- The next G6S report shows every NCM step succeeded, including alternate setting `1` on configuration `6`, but no USBMUX completion after roughly one minute. On API 17, `UsbDeviceConnection.requestWait()` has no timeout; the USBMUX receive path now uses one-second bulk reads on pre-26 Android so the handshake deadline can be enforced. USBMUX send/wait/reply stages and a five-second worker stack are also reported and shown on the connection screen. This still needs a physical iPhone test.
- The 2026-10-05 13:05 G6S report confirms the version request was sent but no USBMUX reply arrived before the 60-second deadline. The request's 20-byte wire format matches usbmuxd's version-2 handshake. The pre-26 synchronous USB read buffer has now been reduced from 65,536 to 16,384 bytes to stay within usbmuxd's USB receive chunk, and the app diagnostic records the selected USBMUX interface/endpoints, claim result, and first bulk-read result. The next physical iPhone log is needed to distinguish an oversized-read failure from a wrong endpoint or an unresponsive phone.
- The 2026-10-05 13:17 G6S report shows the expected USBMUX interface 1 and endpoints 0x04/0x85. The first 16 KiB bulk read returned -1 after 999 ms, which is a normal read timeout rather than an immediate invalid-size error. A second attempt likewise received no version reply. The next build reads the device's active USB configuration through standard GET_CONFIGURATION, skips a redundant SET_CONFIGURATION when already on CarPlay configuration 6, and reports the selection result and active value. This separates a configuration reset from a true USBMUX response failure.
- The 2026-10-05 13:36 G6S report confirms active configuration 6. After one 60-second USBMUX timeout, subsequent attempts received the 20-byte version reply immediately and reached Lockdown pairing and `com.apple.carkit.service`. The current blocker is `KeyManagerFactory PKIX implementation not found` on the Android 4.2 vendor build. Lockdown TLS now requests the platform's configured default key-manager algorithm rather than hardcoding `PKIX`; physical validation is pending.
- The 2026-10-05 13:46 G6S report confirms the default key manager works, USBMUX succeeds immediately, and Lockdown pairing reaches StartSession. The next failure is that API 17's SSLEngine exposes neither TLS 1.2 nor 1.3. Android documents SSLEngine TLS 1.2 support only from API 20. The app now permits the platform's TLS 1.0 engine only for the paired USB Lockdown stream on pre-20 Android. This is a compatibility probe; a modern iPhone may reject that protocol, in which case an app-bundled TLS 1.2 provider is required.
- The 2026-10-05 13:57 G6S report confirms TLS succeeded, `com.apple.carkit.service` opened, and iAP2 CSM started. The next failure is `VpnService.establish returned null` when attaching NCM. The service manifest lacked the required `android.net.VpnService` intent filter and was `exported=false`, preventing the system VPN manager from binding it. Its `onBind` also returned the app's local binder for the system action. The manifest and binder routing are corrected, and a null establish result now reports whether authorization was revoked.
- The 2026-10-05 14:19 G6S report shows `VpnService.establish` still returns null even though `VpnService.prepare` reports no consent needed. AOSP's old VPN manager can retain a prepared package while its app UID no longer matches after reinstall. A dedicated `DIPLAY_FRESH_VPN_PACKAGE=true` debug build uses `com.shihab.diplay.hudvpn` to force a fresh authorization without changing the ordinary build identity. The Activity now logs whether the VPN consent intent appeared and its result. If the fresh package still cannot establish, the head unit's VPN manager needs further inspection; do not infer USB or MFi failure from this state.
- The 2026-10-05 14:46 G6S report shows the fresh `com.shihab.diplay.hudvpn` package requested VPN consent and received `RESULT_OK`, but the IPv6 TUN establish still returned null. This rules out a stale authorization for the old package. A failed attach now probes a minimal IPv4 VPN interface once and closes it immediately if created; diagnostics also report whether the package remains prepared, whether the VPN service resolves, and the process/package UIDs. A successful IPv4 probe points at the requested IPv6 configuration; a null probe points at the head unit's VPN manager or permission implementation.
- The 2026-10-05 15:12 G6S report confirms every attempt reaches the iPhone's CarKit stream, but both the IPv6 tunnel and minimal IPv4 VPN probe return null. `prepare` reports authorized, the VPN service resolves, and process UID equals application UID (`10048`). Android 4.2 AOSP returns null from `Vpn.establish` only when the prepared package lookup fails or its UID differs from the Binder caller; a malformed address or manifest instead throws an exception. This discrepancy suggests the Flyaudio VPN implementation or its prepared-package state, but needs the head unit's system log or VPN service state to determine the precise cause. No further APK was built from this report because it did not identify an app-side fix.
- The next compatibility build explicitly starts `CarPlayVpnService` before binding it for the NCM attachment. The earlier implementation only bound the service; the Android VPN documentation describes starting the service after consent, and this may also matter to the Flyaudio vendor implementation. The app now reports start/bind failures separately. `:mobile:assembleStandaloneDebug` succeeded on 2026-10-05; whether this changes the G6S `establish()` result requires a new physical test.
- The 2026-10-05 15:49 G6S report confirms `CarPlayVpnService` was explicitly started and bound, but both the production IPv6 tunnel and minimal IPv4 probe still returned null with `prepared=true` and matching UID `10048`. Service start order is now ruled out. A head-unit system log or inspection of its VPN service state is required before another app-side workaround can be selected; no additional APK was built from this report.
- On 2026-10-06, a Windows host recognized the user's iPhone as Apple USB `05ac:12a8`, while a separately launched API 17 x86 emulator with QEMU `usb-host` passthrough did not enumerate the phone, including after a physical unplug/replug. The guest listed only its two root hubs, `dumpsys usb` listed no host devices, and `pm list features` lacked `android.hardware.usb.host`. DiPlay installed and reached the visible “waiting for USB iPhone” stage, but this stock emulator cannot currently validate the physical wired transport. The G6S remains the relevant USB test device; emulator tests can still cover API 17 startup and UI.
- On 2026-10-07, a Samsung SM-C5000 on Android 8.0/API 26 enumerated iPhone `05ac:12a8`, granted DiPlay USB permission, exchanged USBMUX traffic, opened the wired CarPlay stream, and received `CarPlayStartSession`. The NCM reader then failed because `UsbRequest.queue()` rejected its 32 KiB direct buffer (`remaining bytes ... too high`, platform maximum 16 KiB). `NcmUsbBridge` now queues 16 KiB reads; its existing buffer/parser reassembles split NTB16 blocks. Rebuild and repeat the physical SM-C5000/iPhone test.
- On 2026-10-07, the 16 KiB read fix allowed iAP2/MFi authentication, CarPlay availability, and `CarPlayStartSession`; the remaining observed stall is before AirPlay accepts a TCP client on port 7000. A diagnostic build logs bounded inbound/outbound IPv6 TCP flags and sequence numbers plus the bound listener address to isolate the NCM/TCP handshake.

## Source and CI builds

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintDebug :mobile:assembleDebug
```

The resulting source-only APK contains no accessory identity. Standalone CarPlay requires runtime authentication provisioning. Tests generate synthetic identities at runtime; no test private-key files are tracked.

## Local release packaging

Provide an external asset directory using `DIPLAY_AUTH_ASSETS_DIR`. The directory must contain exactly the intended runtime files under `offline-mfi/identity.pk8` and `offline-mfi/certificate.p7b`. Neither file belongs in Git. The build permits those two files only when this explicit input is set and rejects unexpected credential containers elsewhere in APK assets.

Set `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` locally for your Android signing key. Never commit these values or the keystore. Different signing keys cannot update an existing project-signed installation.

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest :mobile:lintRelease :mobile:assembleRelease
```

Output: `mobile/build/outputs/apk/release/mobile-release.apk`. The release APK deliberately contains the experimental identity described in the notices; it is extractable by recipients. The separate Android signing key is not included. The retired build-beta.py helper is not used; this Gradle workflow uses explicit environment inputs.

The public release source archive corresponds to the tagged source and excludes runtime identities, signing keys, local configuration and build output.

## Standalone car-test APK

Use `:mobile:assembleStandaloneDebug` for a test APK that must connect to an iPhone:

```sh
DIPLAY_AUTH_ASSETS_DIR=/absolute/path/to/runtime-assets ./gradlew :mobile:assembleStandaloneDebug
```

This task refuses missing or empty runtime inputs. `assembleDebug` remains an identity-free
source/CI build when the explicit asset input is absent; do not install that output as a
standalone car-test package. Before delivery, verify both `assets/offline-mfi/identity.pk8`
and `assets/offline-mfi/certificate.p7b` in the APK against the selected local inputs.
Update the existing test app without uninstalling it to preserve its settings.

- The 2026-10-07 SM-C5000/API 26 test exposed a missing CDC-NCM host initialization sequence. The bridge now reads `GET_NTB_PARAMETERS`, selects NTB16 with `SET_NTB_FORMAT`, and enables directed, multicast, and broadcast traffic with `SET_ETHERNET_PACKET_FILTER` when supported. The phone accepted all three requests; its reported NTB sizes are 32764 bytes.
- With that fix installed, the iPhone completed AirPlay TCP/RTSP setup over IPv6 and rendered the CarPlay dashboard and Amap on the SM-C5000. Video telemetry reached about 27 fps. One session ended with peer EOF after roughly 24 seconds; the iPhone retried, completed pair-setup/pair-verify and RTSP setup again, and the dashboard was visible in the latest live screenshot. Continue monitoring for stable reconnects and user input/audio before calling the path fully validated.
- The current test APK was copied to the task outputs folder as `DiPlay-API17-Apple-USB-auto-connect-debug.apk`, replacing the previous APK there. The configured `Z:\迅雷下载\outfile` destination was unavailable in this Windows session because drive Z: is not mapped.
- The USB attach filter now includes all Apple VID `05AC` devices (while retaining the CH341 match), so Android can resolve DiPlay as the default handler when an iPhone is attached. This default-handler association can launch/select the app; it does not create a durable USB access grant. `UsbManager.requestPermission()` grants access only until disconnect, so an app cannot silently preserve or bypass that grant as an ordinary third-party app. iPhone re-enumeration can trigger a new authorization request.

### 2026-10-07 USB foreground and vehicle branding follow-up

- The USB attach filter now targets `CarPlayHostActivity` instead of the DiPlay home activity. iPhone USB re-enumeration during CarPlay therefore returns to the existing `singleTask` projection activity instead of foregrounding the home page while the background session remains active. The home activity still scans for an already connected Apple USB device during startup.
- When CarPlay asks to show the vehicle UI, DiPlay records a one-shot return marker before opening Android HOME. Reopening DiPlay brings the existing projection activity forward if its process-local session survived; if Android killed the session process, startup skips USB auto-connect once and asks the user to reconnect manually, avoiding a stale iPhone USB session being handshaken repeatedly.
- The app's Settings page now edits the AirPlay vehicle name and custom icon. With no custom value, the name and blue-oval icon default to Ford; the previous built-in BYD label is migrated once, and user-defined labels are retained. Changes apply on the next CarPlay connection. DiPlay's own app name is unchanged.

- 2026-10-07 USB throughput regression: removed a diagnostic iAP2 stream wrapper that split each TLS write into 256-byte USB transactions. Device log showed MFi auth and CarPlayStartSession succeeded, but writes then took roughly 1–2 seconds for a few hundred bytes and USBMUX eventually failed. Restored direct writes to the Carkit stream. Built `DiPlay-API17-USB-throughput-fix-debug.apk` (`minSdk 17`, `com.shihab.diplay.hudvpn`) and installed it on SM-C5000; projection still needs a fresh in-car test.

- 2026-10-07 head-unit HOME regression: the manifest USB attach filter can relaunch/foreground CarPlayHostActivity during iPhone re-enumeration after the CarPlay vehicle tile returned to system HOME. While the persisted one-shot car-UI return marker and existing background session are both present, Host now redirects USB-attach launches back to HOME and finishes the duplicate UI, leaving the current session available for an intentional DiPlay reopen. Built and installed DiPlay-API17-USB-home-return-fix-debug.apk; repeated vehicle-tile / USB re-enumeration behavior still needs user-side confirmation.
