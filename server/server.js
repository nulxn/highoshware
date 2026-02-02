const express = require('express');
const WebSocket = require('ws');
const http = require('http');
const path = require('path');

const app = express();
const server = http.createServer(app);

// Serve static files from public directory
app.use(express.static(path.join(__dirname, 'public')));

// Store active streams: clientId -> { screen: WebSocket, webcam: WebSocket }
const activeStreams = new Map();

// WebSocket server for receiving streams from clients
const wss = new WebSocket.Server({ 
    server,
    path: '/stream'
});

wss.on('connection', (ws, req) => {
    const url = new URL(req.url, `http://${req.headers.host}`);
    const streamType = url.searchParams.get('type'); // 'screen' or 'webcam'
    const clientId = url.searchParams.get('clientId');

    if (!streamType || !clientId) {
        ws.close(1008, 'Missing type or clientId parameter');
        return;
    }

    console.log(`Client ${clientId} connected with ${streamType} stream`);

    // Initialize client entry if not exists
    if (!activeStreams.has(clientId)) {
        activeStreams.set(clientId, { screen: null, webcam: null });
    }

    const clientStreams = activeStreams.get(clientId);
    clientStreams[streamType] = ws;

    // Broadcast to all viewing clients that a new stream is available
    broadcastStreamUpdate();

    ws.on('message', (data) => {
        // Forward frame to all viewing clients
        broadcastFrame(clientId, streamType, data);
    });

    ws.on('close', () => {
        console.log(`Client ${clientId} disconnected ${streamType} stream`);
        const clientStreams = activeStreams.get(clientId);
        if (clientStreams) {
            clientStreams[streamType] = null;
            // Remove client if both streams are closed
            if (!clientStreams.screen && !clientStreams.webcam) {
                activeStreams.delete(clientId);
            }
        }
        broadcastStreamUpdate();
    });

    ws.on('error', (error) => {
        console.error(`WebSocket error for client ${clientId} ${streamType}:`, error);
    });
});

// WebSocket server for viewing clients (browsers)
const viewWss = new WebSocket.Server({ 
    server,
    path: '/view'
});

const viewingClients = new Set();

viewWss.on('connection', (ws) => {
    viewingClients.add(ws);
    console.log('Viewing client connected. Total viewers:', viewingClients.size);
    
    // Send current stream list
    sendStreamList(ws);

    ws.on('close', () => {
        viewingClients.delete(ws);
        console.log('Viewing client disconnected. Total viewers:', viewingClients.size);
    });

    ws.on('error', (error) => {
        console.error('Viewing client error:', error);
    });
});

function broadcastFrame(clientId, streamType, frameData) {
    const message = JSON.stringify({
        type: 'frame',
        clientId,
        streamType,
        data: frameData.toString('base64')
    });

    viewingClients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            try {
                client.send(message);
            } catch (error) {
                console.error('Error sending frame to viewing client:', error);
            }
        }
    });
}

function broadcastStreamUpdate() {
    const streamList = Array.from(activeStreams.entries()).map(([clientId, streams]) => ({
        clientId,
        hasScreen: streams.screen !== null,
        hasWebcam: streams.webcam !== null
    }));

    const message = JSON.stringify({
        type: 'streams',
        streams: streamList
    });

    viewingClients.forEach((client) => {
        if (client.readyState === WebSocket.OPEN) {
            try {
                client.send(message);
            } catch (error) {
                console.error('Error sending stream update:', error);
            }
        }
    });
}

function sendStreamList(ws) {
    const streamList = Array.from(activeStreams.entries()).map(([clientId, streams]) => ({
        clientId,
        hasScreen: streams.screen !== null,
        hasWebcam: streams.webcam !== null
    }));

    const message = JSON.stringify({
        type: 'streams',
        streams: streamList
    });

    if (ws.readyState === WebSocket.OPEN) {
        ws.send(message);
    }
}

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Granolaa server running on http://localhost:${PORT}`);
    console.log(`WebSocket stream endpoint: ws://localhost:${PORT}/stream`);
    console.log(`WebSocket view endpoint: ws://localhost:${PORT}/view`);
});
