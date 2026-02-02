package com.granolaa.app;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket client that sends screen and webcam frames to a remote server.
 * Each stream is identified by a unique client ID and stream type.
 */
public class StreamClient {

    private final String serverUrl;
    private final String clientId;
    private WebSocketClient screenClient;
    private WebSocketClient webcamClient;
    private final ScreenCapture screenCapture;
    private final WebcamCapture webcamCapture;
    private final AtomicBoolean screenConnected = new AtomicBoolean(false);
    private final AtomicBoolean webcamConnected = new AtomicBoolean(false);
    private Thread screenSenderThread;
    private Thread webcamSenderThread;

    public StreamClient(String serverUrl, ScreenCapture screenCapture, WebcamCapture webcamCapture) {
        this.serverUrl = serverUrl;
        this.clientId = UUID.randomUUID().toString();
        this.screenCapture = screenCapture;
        this.webcamCapture = webcamCapture;
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
        if (screenClient != null) {
            screenClient.close();
        }
        if (webcamClient != null) {
            webcamClient.close();
        }
    }

    private void startScreenStream() {
        try {
            URI screenUri = new URI(serverUrl + "/stream?type=screen&clientId=" + clientId);
            screenClient = new WebSocketClient(screenUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    screenConnected.set(true);
                    System.out.println("Screen stream connected to server");
                }

                @Override
                public void onMessage(String message) {
                    // Server can send control messages if needed
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    screenConnected.set(false);
                    System.out.println("Screen stream disconnected: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("Screen stream error: " + ex.getMessage());
                    screenConnected.set(false);
                }
            };
            screenClient.connect();

            screenSenderThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (screenConnected.get() && screenClient.isOpen()) {
                            byte[] frame = screenCapture.getLatestFrame();
                            if (frame != null && frame.length > 0) {
                                // Send frame as binary data
                                screenClient.send(frame);
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
        } catch (Exception e) {
            System.err.println("Failed to start screen stream: " + e.getMessage());
        }
    }

    private void startWebcamStream() {
        try {
            URI webcamUri = new URI(serverUrl + "/stream?type=webcam&clientId=" + clientId);
            webcamClient = new WebSocketClient(webcamUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    webcamConnected.set(true);
                    System.out.println("Webcam stream connected to server");
                }

                @Override
                public void onMessage(String message) {
                    // Server can send control messages if needed
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    webcamConnected.set(false);
                    System.out.println("Webcam stream disconnected: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("Webcam stream error: " + ex.getMessage());
                    webcamConnected.set(false);
                }
            };
            webcamClient.connect();

            webcamSenderThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        if (webcamConnected.get() && webcamClient.isOpen()) {
                            byte[] frame = webcamCapture.getLatestFrame();
                            if (frame != null && frame.length > 0) {
                                // Send frame as binary data
                                webcamClient.send(frame);
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
        } catch (Exception e) {
            System.err.println("Failed to start webcam stream: " + e.getMessage());
        }
    }

    public String getClientId() {
        return clientId;
    }
}
