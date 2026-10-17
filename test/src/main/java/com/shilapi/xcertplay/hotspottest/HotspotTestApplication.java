package com.shilapi.xcertplay.hotspottest;

import android.util.Log;
import androidx.multidex.MultiDexApplication;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

/** Local diagnostic endpoint in the test APK only; keep the app open while using it. */
public final class HotspotTestApplication extends MultiDexApplication {
    private static final String TAG = "HotspotHttpTest";

    @Override public void onCreate() {
        super.onCreate();
        Thread worker = new Thread(this::serve, "hotspot-http-test");
        worker.setDaemon(true);
        worker.start();
    }

    private void serve() {
        // Bind only to the Wi-Fi address, so this does not expose a diagnostic on other interfaces.
        try {
            NetworkInterface wifi = NetworkInterface.getByName("wlan0");
            InetAddress address = null;
            if (wifi != null) {
                Enumeration<InetAddress> addresses = wifi.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress candidate = addresses.nextElement();
                    if (candidate instanceof Inet4Address && !candidate.isLoopbackAddress()) {
                        address = candidate;
                        break;
                    }
                }
            }
            if (address == null) {
                Log.w(TAG, "No Wi-Fi IPv4 address; join hotspot and restart app");
                return;
            }
            try (ServerSocket server = new ServerSocket()) {
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(address, 8080));
                Log.i(TAG, "HTTP ready " + address.getHostAddress() + ":8080 version=1.4");
                long count = 0;
                while (true) {
                    try (Socket client = server.accept()) {
                        client.setSoTimeout(3000);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), "US-ASCII"));
                        String line = reader.readLine();
                        if (line == null) continue;
                        int size = line.length();
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            size += line.length();
                            if (size > 8192) throw new java.io.IOException("Headers too large");
                        }
                        count++;
                        String html = "<!doctype html><html lang='zh-CN'><meta charset='utf-8'>"
                            + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                            + "<title>手机热点联通性测试</title><body><h1>访问成功</h1>"
                            + "<p>浏览器已访问到安卓 HTTP 服务。</p>"
                            + "<p>测试版本：1.4（API 17）</p><p>安卓地址：" + address.getHostAddress()
                            + ":8080</p><p>访问来源：" + client.getInetAddress().getHostAddress()
                            + "</p><p>请求序号：" + count + "</p><p>响应时间戳：" + System.currentTimeMillis()
                            + "</p><p>此结果确认 TCP 8080 可达，CarPlay 会话仍需单独确认。</p>"
                            + "<p><a href='/'>刷新测试</a></p></body></html>";
                        byte[] body = html.getBytes("UTF-8");
                        OutputStream out = client.getOutputStream();
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n"
                            + "Cache-Control: no-store\r\nConnection: close\r\nContent-Length: "
                            + body.length + "\r\n\r\n").getBytes("US-ASCII"));
                        out.write(body);
                        out.flush();
                        Log.i(TAG, "HTTP response sent peer=" + client.getInetAddress().getHostAddress() + " request=" + count);
                    } catch (java.io.IOException e) {
                        Log.w(TAG, "HTTP client failed: " + e.getClass().getSimpleName());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP listener failed: " + e.getClass().getSimpleName());
        }
    }
}
