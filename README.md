# Granolaa

A distributed streaming system for screen and webcam capture. The system consists of:

* **Distribution Client**: Java application that captures screen/webcam and sends streams to a server.
* **Server**: Node.js server that receives multiple streams and serves a web interface for viewing.

## Architecture

* **Person sending (distribution client)**: The Java app sends screen and webcam frames to the server using **HTTP POST** (chunked) to `/stream/screen` and `/stream/webcam`. It does **not** use WebSocket.
* **Person watching**: A viewer opens the server URL in a **browser**. The browser connects to the server using **WebSocket** (`/view`) to receive the list of streams and live frames.

```
┌─────────────────┐      HTTP POST (chunked)    ┌──────────────┐
│  Client (Java)  │  /stream/screen, /stream/   │   Server     │
│  - Screen       │  webcam                     │  (Node.js)   │
│  - Webcam       │ ─────────────────────────> │              │
└─────────────────┘                             │  ┌────────┐  │
                                               │  │ Viewer│  │
                                               │  │ (WS)  │  │
                                               │  └───┬───┘  │
                                               └──────│──────┘
                                                      │ WebSocket /view
                                                      ▼
                                               ┌──────────────┐
                                               │   Browser    │
                                               │   (viewer)   │
                                               └──────────────┘

```

## Quick Start

### 1. Start the Server

```bash
cd server
npm install
npm start

```

The server will run on `http://localhost:3000` by default.

### 2. Run Client (Development)

In a separate terminal, run the client. The build system will automatically detect your OS and pull the correct native binaries for the webcam.

```bash
cd distribution
mvn clean compile exec:java

```

### 3. View Streams

Open your browser and navigate to `http://localhost:3000` (or your server URL) to view all active streams.

## Building the Client (Distribution)

To keep the executable small, we build **platform-specific JARs**. Instead of bundling every OS binary (Windows, Mac, Linux), the build only includes the binaries for the target system.

### Build for your current OS

This will generate a JAR optimized for your current machine:

```bash
cd distribution
mvn clean package

```

The output will be in `target/` with a name like:

`granolaa-1.0-SNAPSHOT-windows-x86_64.jar` or `granolaa-1.0-SNAPSHOT-macosx-aarch64.jar`.

### Build for a specific platform

If you are on a Mac but want to build the Windows version, use the classifier flag:

* **Windows**: `mvn clean package -Dos.detected.classifier=windows-x86_64`
* **Mac (Apple Silicon)**: `mvn clean package -Dos.detected.classifier=macosx-aarch64`
* **Mac (Intel)**: `mvn clean package -Dos.detected.classifier=macosx-x86_64`
* **Linux**: `mvn clean package -Dos.detected.classifier=linux-x86_64`

### Running the JAR

Run the generated JAR with the following command (replace the filename with your actual generated version):

```bash
java -jar target/granolaa-1.0-SNAPSHOT-[YOUR-OS-CLASSIFIER].jar

```

## Configuration

### Client Configuration

Set the server URL via:

* System property: `-Dserver=http://server-url:port`
* Environment variable: `SERVER_URL=http://server-url:port`
* Default: `http://granolaa.opencodingsociety.com`

### Server Configuration

Set the port via:

* Environment variable: `PORT=3000`
* Default: `3000`

## Platform Support

* **Screen Capture**: macOS and Windows (not supported on Linux Wayland).
* **Webcam**:
* **macOS**: Uses JavaCV (OpenCV) for both Intel and Apple Silicon.
* **Windows/Linux**: Uses Sarxos or JavaCV fallback.
