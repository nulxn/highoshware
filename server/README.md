# Granolaa Server

Node.js server for receiving and displaying multiple Granolaa streams.

## Installation

```bash
npm install
```

## Running

```bash
npm start
```

Or with a custom port:

```bash
PORT=8080 npm start
```

## Endpoints

- `GET /` - Web interface for viewing streams
- `POST /stream/screen` - HTTP (chunked) for **senders** (Java app) to push screen frames. Query: `clientId`
- `POST /stream/webcam` - HTTP (chunked) for **senders** (Java app) to push webcam frames. Query: `clientId`
- `WS /view` - WebSocket for **viewers** (browser) to receive stream list and live frames

## Architecture

The server maintains a map of active streams and forwards frames from streaming clients to viewing clients in real-time. Each client can have both a screen and webcam stream.

## Environment Variables

- `PORT` - Server port (default: 3000)
