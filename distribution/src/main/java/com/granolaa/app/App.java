package com.granolaa.app;

import java.util.Scanner;

public class App {

    private static final String DEFAULT_SERVER_URL = "ws://granolaa.opencodingsociety.com";

    public static void main(String[] args) {
        String serverUrl = System.getProperty("server", 
                System.getenv().getOrDefault("SERVER_URL", DEFAULT_SERVER_URL));
        
        // Ensure server URL starts with ws:// or wss://
        if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
            serverUrl = "ws://" + serverUrl;
        }

        ScreenCapture screenCapture = new ScreenCapture();
        WebcamCapture webcamCapture = new WebcamCapture();

        Thread screenThread = new Thread(screenCapture, "screen-capture");
        Thread webcamThread = new Thread(webcamCapture, "webcam-capture");
        screenThread.setDaemon(true);
        webcamThread.setDaemon(true);
        screenThread.start();
        webcamThread.start();

        // Give capture threads a moment to start
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        StreamClient streamClient = new StreamClient(serverUrl, screenCapture, webcamCapture);
        streamClient.start();

        System.out.println("Granolaa – Client streaming to server");
        System.out.println();
        System.out.println("  Server:  " + serverUrl);
        System.out.println("  Client ID: " + streamClient.getClientId());
        System.out.println();
        System.out.println("Press Enter to stop.");

        // Wait for user input to stop
        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        }

        streamClient.stop();
        screenCapture.stop();
        webcamCapture.stop();
        System.out.println("Stopped.");
    }
}
