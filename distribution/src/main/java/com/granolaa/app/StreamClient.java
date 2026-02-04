package com.granolaa.app;

import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Sender side: sends screen and webcam frames to the server via HTTP POST (chunked).
 * WebSocket is used only by viewers (browser) to receive streams; this client uses
 * HTTP POST to /stream/screen and /stream/webcam with length-prefixed frames
 * (4-byte big-endian length + JPEG bytes).
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
        // Convert WebSocket URLs to HTTP/HTTPS URLs
        if (serverUrl.startsWith("wss://")) {
            this.httpBaseUrl = serverUrl.replaceFirst("^wss://", "https://");
        } else if (serverUrl.startsWith("ws://")) {
            this.httpBaseUrl = serverUrl.replaceFirst("^ws://", "http://");
        } else {
            this.httpBaseUrl = serverUrl;
        }
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .followRedirects(false) // Prevent redirects that might convert POST to GET
                .addInterceptor(new LoggingInterceptor())
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

    private static final long RECONNECT_DELAY_MS = 2_000;

    private void startScreenStream() {
        screenSenderThread = new Thread(() -> {
            while (running) {
                String url = httpBaseUrl + "/stream/screen?clientId=" + clientId;
                Request request = new Request.Builder()
                        .url(url)
                        .post(new ChunkedFrameBody(screenCapture::getLatestFrame, "screen"))
                        .header("Content-Type", "application/octet-stream")
                        .build();
                try {
                    Call call = okHttpClient.newCall(request);
                    screenCall.set(call);
                    System.out.println("Screen stream connecting...");
                    try (var response = call.execute()) {
                        if (response.isSuccessful()) {
                            System.out.println("Screen stream ended normally");
                        } else {
                            System.err.println("Screen stream response: " + response.code());
                        }
                    }
                } catch (IOException e) {
                    if (running) System.err.println("Screen stream error: " + e.getMessage());
                } finally {
                    screenCall.set(null);
                }
                if (running) {
                    System.out.println("Screen stream reconnecting in " + (RECONNECT_DELAY_MS / 1000) + "s...");
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "screen-sender");
        screenSenderThread.setDaemon(true);
        screenSenderThread.start();
    }

    private void startWebcamStream() {
        webcamSenderThread = new Thread(() -> {
            while (running) {
                String url = httpBaseUrl + "/stream/webcam?clientId=" + clientId;
                Request request = new Request.Builder()
                        .url(url)
                        .post(new ChunkedFrameBody(webcamCapture::getLatestFrame, "webcam"))
                        .header("Content-Type", "application/octet-stream")
                        .build();
                try {
                    Call call = okHttpClient.newCall(request);
                    webcamCall.set(call);
                    System.out.println("Webcam stream connecting...");
                    try (var response = call.execute()) {
                        if (response.isSuccessful()) {
                            System.out.println("Webcam stream ended normally");
                        } else {
                            System.err.println("Webcam stream response: " + response.code());
                        }
                    }
                } catch (IOException e) {
                    if (running) System.err.println("Webcam stream error: " + e.getMessage());
                } finally {
                    webcamCall.set(null);
                }
                if (running) {
                    System.out.println("Webcam stream reconnecting in " + (RECONNECT_DELAY_MS / 1000) + "s...");
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "webcam-sender");
        webcamSenderThread.setDaemon(true);
        webcamSenderThread.start();
    }

    public String getClientId() {
        return clientId;
    }

    /** Logs every outgoing request and its response. */
    private static final class LoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            String ts = Instant.now().toString();
            System.out.println("[" + ts + "] " + request.method() + " " + request.url());
            long start = System.currentTimeMillis();
            Response response = chain.proceed(request);
            long duration = System.currentTimeMillis() - start;
            System.out.println("[" + ts + "] " + request.method() + " " + request.url() + " -> " + response.code() + " (" + duration + "ms)");
            return response;
        }
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

        /** Unknown length so OkHttp uses chunked transfer encoding and streams the body. */
        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            int frameCount = 0;
            while (running && !Thread.currentThread().isInterrupted()) {
                byte[] frame = frameSupplier.get();
                if (frame != null && frame.length > 0) {
                    sink.writeInt(frame.length);
                    sink.write(frame);
                    sink.flush();
                    frameCount++;
                    if (frameCount % 50 == 1) {
                        System.out.println("[" + name + "] sent frame #" + frameCount);
                    }
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[" + name + "] stream ended after " + frameCount + " frames");
        }
    }
}
