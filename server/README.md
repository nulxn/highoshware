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
- `WS /stream` - WebSocket endpoint for clients to send streams
  - Query parameters: `type` (screen|webcam), `clientId` (unique client identifier)
- `WS /view` - WebSocket endpoint for browsers to receive stream updates

## Architecture

The server maintains a map of active streams and forwards frames from streaming clients to viewing clients in real-time. Each client can have both a screen and webcam stream.

## Environment Variables

- `PORT` - Server port (default: 3000)
