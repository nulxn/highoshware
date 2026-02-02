package com.granolaa.app;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * HTTP client that sends screen and webcam frames to the server via chunked POST.
 * Uses length-prefixed frames (4-byte big-endian length + JPEG bytes) to avoid
 * WebSocket control-frame issues (RSV1, "Control frames must be final").
 */
public class StreamClient {

    private final String httpBaseUrl;
    private final String clientId;
    private final ScreenCapture screenCapture;
    private final WebcamCapture webcamCapture;
    private final OkHttpClient okHttpClient;

    private final AtomicReference<Call> screenCall = new AtomicReference<>();
    private final AtomicReference<Call> webcamCall = new AtomicReference<>();
    private Thread screenSenderThread;
    private Thread webcamSenderThread;
    private volatile boolean running = true;

    public StreamClient(String serverUrl, ScreenCapture screenCapture, WebcamCapture webcamCapture) {
        this.clientId = UUID.randomUUID().toString();
        this.screenCapture = screenCapture;
        this.webcamCapture = webcamCapture;
        this.httpBaseUrl = serverUrl.replaceFirst("^ws", "http");
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .build();
    }

    public void start() {
        if (screenCapture.isScreenCaptureSupported()) {
            startScreenStream();
        }
        startWebcamStream();
    }

    public void stop() {
        running = false;
        Call sc = screenCall.getAndSet(null);
        if (sc != null) sc.cancel();
        Call wc = webcamCall.getAndSet(null);
        if (wc != null) wc.cancel();
        if (screenSenderThread != null) screenSenderThread.interrupt();
        if (webcamSenderThread != null) webcamSenderThread.interrupt();
    }

    private void startScreenStream() {
        Request request = new Request.Builder()
                .url(httpBaseUrl + "/stream/screen?clientId=" + clientId)
                .post(new ChunkedFrameBody(screenCapture::getLatestFrame, "screen"))
                .build();
        screenSenderThread = new Thread(() -> {
            try {
                Call call = okHttpClient.newCall(request);
                screenCall.set(call);
                System.out.println("Screen stream connecting...");
                try (var response = call.execute()) {
                    if (response.isSuccessful()) {
                        System.out.println("Screen stream ended normally");
                    }
                }
            } catch (IOException e) {
                if (running) System.err.println("Screen stream error: " + e.getMessage());
            } finally {
                screenCall.set(null);
            }
        }, "screen-sender");
        screenSenderThread.setDaemon(true);
        screenSenderThread.start();
    }

    private void startWebcamStream() {
        Request request = new Request.Builder()
                .url(httpBaseUrl + "/stream/webcam?clientId=" + clientId)
                .post(new ChunkedFrameBody(webcamCapture::getLatestFrame, "webcam"))
                .build();
        webcamSenderThread = new Thread(() -> {
            try {
                Call call = okHttpClient.newCall(request);
                webcamCall.set(call);
                System.out.println("Webcam stream connecting...");
                try (var response = call.execute()) {
                    if (response.isSuccessful()) {
                        System.out.println("Webcam stream ended normally");
                    }
                }
            } catch (IOException e) {
                if (running) System.err.println("Webcam stream error: " + e.getMessage());
            } finally {
                webcamCall.set(null);
            }
        }, "webcam-sender");
        webcamSenderThread.setDaemon(true);
        webcamSenderThread.start();
    }

    public String getClientId() {
        return clientId;
    }

    private class ChunkedFrameBody extends RequestBody {
        private final Supplier<byte[]> frameSupplier;
        private final String name;

        ChunkedFrameBody(Supplier<byte[]> frameSupplier, String name) {
            this.frameSupplier = frameSupplier;
            this.name = name;
        }

        @Override
        public MediaType contentType() {
            return MediaType.get("application/octet-stream");
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            while (running && !Thread.currentThread().isInterrupted()) {
                byte[] frame = frameSupplier.get();
                if (frame != null && frame.length > 0) {
                    sink.writeInt(frame.length);
                    sink.write(frame);
                    sink.flush();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
