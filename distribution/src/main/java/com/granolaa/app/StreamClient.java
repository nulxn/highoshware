package com.granolaa.app;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket client that sends screen and webcam frames to a remote server.
 * Uses OkHttp WebSocket (no permessage-deflate) to avoid RSV1/compression errors.
 * Each stream is identified by a unique client ID and stream type.
 */
public class StreamClient {

    private final String serverUrl;
    private final String clientId;
    private final ScreenCapture screenCapture;
    private final WebcamCapture webcamCapture;
    private final OkHttpClient okHttpClient;

    private final AtomicReference<WebSocket> screenWebSocket = new AtomicReference<>();
    private final AtomicReference<WebSocket> webcamWebSocket = new AtomicReference<>();
    private volatile boolean screenConnected;
    private volatile boolean webcamConnected;
    private Thread screenSenderThread;
    private Thread webcamSenderThread;

    public StreamClient(String serverUrl, ScreenCapture screenCapture, WebcamCapture webcamCapture) {
        this.serverUrl = serverUrl;
        this.clientId = UUID.randomUUID().toString();
        this.screenCapture = screenCapture;
        this.webcamCapture = webcamCapture;
        this.okHttpClient = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    public void start() {
        if (screenCapture.isScreenCaptureSupported()) {
            startScreenStream();
        }
        startWebcamStream();
    }

    public void stop() {
        if (screenSenderThread != null) {
            screenSenderThread.interrupt();
        }
        if (webcamSenderThread != null) {
            webcamSenderThread.interrupt();
        }
        WebSocket sw = screenWebSocket.getAndSet(null);
        if (sw != null) {
            sw.close(1000, "Client shutdown");
        }
        WebSocket ww = webcamWebSocket.getAndSet(null);
        if (ww != null) {
            ww.close(1000, "Client shutdown");
        }
    }

    private void startScreenStream() {
        String url = serverUrl + "/stream?type=screen&clientId=" + clientId;
        Request request = new Request.Builder().url(url).build();
        okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                screenWebSocket.set(webSocket);
                screenConnected = true;
                System.out.println("Screen stream connected to server");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) { }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) { }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                screenConnected = false;
                screenWebSocket.set(null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                screenConnected = false;
                screenWebSocket.set(null);
                System.out.println("Screen stream disconnected: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                screenConnected = false;
                screenWebSocket.set(null);
                System.err.println("Screen stream error: " + (t != null ? t.getMessage() : "unknown"));
            }
        });

        screenSenderThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WebSocket ws = screenWebSocket.get();
                    if (screenConnected && ws != null) {
                        byte[] frame = screenCapture.getLatestFrame();
                        if (frame != null && frame.length > 0) {
                            ws.send(ByteString.of(frame));
                        }
                    }
                    Thread.sleep(100); // ~10 FPS
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error sending screen frame: " + e.getMessage());
                }
            }
        }, "screen-sender");
        screenSenderThread.setDaemon(true);
        screenSenderThread.start();
    }

    private void startWebcamStream() {
        String url = serverUrl + "/stream?type=webcam&clientId=" + clientId;
        Request request = new Request.Builder().url(url).build();
        okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                webcamWebSocket.set(webSocket);
                webcamConnected = true;
                System.out.println("Webcam stream connected to server");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) { }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) { }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webcamConnected = false;
                webcamWebSocket.set(null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                webcamConnected = false;
                webcamWebSocket.set(null);
                System.out.println("Webcam stream disconnected: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                webcamConnected = false;
                webcamWebSocket.set(null);
                System.err.println("Webcam stream error: " + (t != null ? t.getMessage() : "unknown"));
            }
        });

        webcamSenderThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WebSocket ws = webcamWebSocket.get();
                    if (webcamConnected && ws != null) {
                        byte[] frame = webcamCapture.getLatestFrame();
                        if (frame != null && frame.length > 0) {
                            ws.send(ByteString.of(frame));
                        }
                    }
                    Thread.sleep(100); // ~10 FPS
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error sending webcam frame: " + e.getMessage());
                }
            }
        }, "webcam-sender");
        webcamSenderThread.setDaemon(true);
        webcamSenderThread.start();
    }

    public String getClientId() {
        return clientId;
    }
}
