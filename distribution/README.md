# Granolaa Distribution Client

Java client application that captures screen and webcam, then streams them to a remote server via WebSocket.

## Building

```bash
mvn clean package
```

This creates `target/granolaa-1.0-SNAPSHOT.jar`.

## Running

### Development

```bash
mvn clean compile exec:java
```

### Production

```bash
java -jar target/granolaa-1.0-SNAPSHOT.jar
```

### Custom Server URL

```bash
mvn exec:java -Dserver=ws://your-server.com:3000
```

Or with JAR:

```bash
java -Dserver=ws://your-server.com:3000 -jar target/granolaa-1.0-SNAPSHOT.jar
```

Or via environment variable:

```bash
SERVER_URL=ws://your-server.com:3000 java -jar target/granolaa-1.0-SNAPSHOT.jar
```

## Configuration

- **Server URL**: Set via `-Dserver` system property or `SERVER_URL` environment variable
- **Default**: `ws://localhost:3000`

## Features

- Screen capture (macOS, Windows)
- Webcam capture (macOS, Windows, Linux)
- Automatic reconnection on connection loss
- Low-latency WebSocket streaming

## Platform Support

- **Screen Capture**: macOS and Windows only (not supported on Linux Wayland)
- **Webcam**: macOS (JavaCV), Windows/Linux (sarxos)
