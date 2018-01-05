package com.sergiopaniegoblanco.webrtcexampleapp;

import com.neovisionaries.ws.client.ThreadType;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;

/**
 * Created by sergiopaniegoblanco on 02/12/2017.
 */

public final class CustomWebSocketAdapter implements WebSocketListener {

    private static final String JSONRPCVERSION = "2.0";

    private MainActivity mainActivity;
    private PeerConnection localPeer;
    private int pingMessageInterval = 3;
    private int id;
    private String sessionId;
    private List<Map<String, String>> iceCandidatesParams;
    private Map<String, String> localOfferParams;
    private String userId;
    private String remoteUserId;
    private String storedSessionDescription;

    public CustomWebSocketAdapter(MainActivity mainActivity, PeerConnection localPeer) {
        this.mainActivity = mainActivity;
        this.localPeer = localPeer;
        this.id = 0;
        iceCandidatesParams = new ArrayList<>();
    }


    public String getUserId() {
        return userId;
    }
    public String getRemoteUserId() {
        return remoteUserId;
    }

    public synchronized int getId() {
        return id;
    }
    public synchronized void updateId() {
        id++;
    }

    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
        System.out.println("State changed: " + newState.name());
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        System.out.println("Connected");

        // Send ping
        pingMessageHandler(websocket);

        // Send request to join the room
        Map<String, String> joinRoomParams = new HashMap<>();
        joinRoomParams.put("dataChannels", "false");
        joinRoomParams.put("metadata", "{\"clientData\": \"Participant38\"}");
        joinRoomParams.put("secret", "MY_SECRET");
        joinRoomParams.put("session", "wss://demos.openvidu.io:8443/SessionA");
        joinRoomParams.put("token", "gr50nzaqe6avt65cg5v06");
        sendJson(websocket, "joinRoom", joinRoomParams);


        if (localOfferParams != null) {
            sendJson(websocket, "publishVideo", localOfferParams);
            System.out.println("PUBLISH VIDEO" + localOfferParams);
        }
    }

    private void pingMessageHandler(final WebSocket webSocket) {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1);
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                Map<String, String> pingParams = new HashMap<>();
                if (id == 0) {
                    pingParams.put("interval", "3000");
                }
                sendJson(webSocket, "ping", pingParams);
            }
        }, 0L, pingMessageInterval, TimeUnit.SECONDS);
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {
        System.out.println("Connect error: " + cause);
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        System.out.println("Disconnected " + serverCloseFrame.getCloseReason() + " " + clientCloseFrame.getCloseReason() + " " + closedByServer);
    }

    @Override
    public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        // System.out.println("Frame!");
    }

    @Override
    public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        System.out.println("Continuation Frame");
    }

    @Override
    public void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        // System.out.println("Text Frame!");
    }

    @Override
    public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        System.out.println("Binary Frame");
    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        System.out.println("Close Frame");
    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        System.out.println("Ping Frame");
    }

    @Override
    public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        System.out.println("Pong Frame");
    }

    @Override
    public void onTextMessage(final WebSocket websocket, String text) throws Exception {
        System.err.println("ENTRADA " + text);
        JSONObject json = new JSONObject(text);
        if (json.has("result")) {
            JSONObject result = new JSONObject(json.getString("result"));
            if (result.has("sdpAnswer")) {
                // System.out.println(text);
                // System.out.println("ANSWER!");
                saveAnswer(websocket, result);
            } else if (result.has("sessionId")) {
                if (result.has("value")) {
                    // addOtherParticipants
                    // receiveVideoFrom
                    if (result.getJSONArray("value").length() != 0) {
                        // System.out.println("HAY MÁS CONEXIONES");
                        this.remoteUserId = new JSONObject(result.getJSONArray("value").getJSONObject(0).getString("metadata")).getString("clientData");
                        mainActivity.call();
                    }
                    this.userId = result.getString("id");
                    for (Map<String, String> iceCandidate : iceCandidatesParams) {
                        iceCandidate.put("endpointName", this.userId);
                        sendJson(websocket, "onIceCandidate", iceCandidate);
                        // System.out.println("ICE CANDIDATE SENT " + this.userId);
                    }
                }
                // System.out.println(text);
                this.sessionId = result.getString("sessionId");
                // System.out.println("SESION ID -> " + this.sessionId);
            } else if (result.has("value")) {
                // System.out.println("PONG!!!!!!!!!!!");
            } else {
                // System.out.println("UNRECOGNIZED " + result);
            }
        } else {
            // METHOD
            if(!json.has("params")) {
                // System.out.println(json);
            } else {
                JSONObject params = new JSONObject(json.getString("params"));
                String method = json.getString("method");
                switch (method) {
                    case "iceCandidate":
                        if (!params.getString("endpointName").equals(userId)) {
                            saveIceCandidate(json.getJSONObject("params"), false);
                        } else {
                            saveIceCandidate(json.getJSONObject("params"), true);
                        }
                        break;
                    case "participantJoined":
                        remoteUserId = params.getString("id");
                        mainActivity.call();
                        break;
                    case "participantPublished":
                        mainActivity.getRemotePeer().createOffer(new CustomSdpObserver("remoteCreateOffer") {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {
                                super.onCreateSuccess(sessionDescription);
                                mainActivity.getRemotePeer().setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc"), sessionDescription);
                                Map<String, String> remoteOfferParams = new HashMap<>();
                                remoteOfferParams.put("sdpOffer", sessionDescription.description);
                                remoteOfferParams.put("sender", remoteUserId + "_webcam");
                                sendJson(websocket, "receiveVideoFrom", remoteOfferParams);
                            }
                        }, new MediaConstraints());
                        break;
                    case "participantLeft":
                        mainActivity.setRemotePeer(null);
                        break;
                }
            }
        }
    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
        System.out.println("Binary Message");
    }

    @Override
    public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        System.out.println("Sending Frame");
    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        // System.out.println("Frame sent");
    }

    @Override
    public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        System.out.println("Frame unsent");
    }

    @Override
    public void onThreadCreated(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {
        System.out.println("Thread created");
    }

    @Override
    public void onThreadStarted(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {
        System.out.println("Thread started");
    }

    @Override
    public void onThreadStopping(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {
        System.out.println("Thread stopping");
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        System.out.println("Error! " + cause);
    }

    @Override
    public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {
        System.out.println("Frame error");
    }

    @Override
    public void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) throws Exception {
        System.out.println("Message error! "+ cause);
    }

    @Override
    public void onMessageDecompressionError(WebSocket websocket, WebSocketException cause, byte[] compressed) throws Exception {
        System.out.println("Message Decompression Error");
    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws Exception {
        System.out.println("Text Message Error! " + cause);
    }

    @Override
    public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {
        System.out.println("Send Error! " + cause);
    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws Exception {
        System.out.println("Unexpected error! " + cause);
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
        System.out.println("Handle callback error! " + cause);
    }

    @Override
    public void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]> headers) throws Exception {
        // System.out.println("Sending Handshake! Hello!");
    }

    private void saveIceCandidate(JSONObject json, boolean local) throws JSONException {
        IceCandidate iceCandidate = new IceCandidate(json.getString("sdpMid"), Integer.parseInt(json.getString("sdpMLineIndex")), json.getString("candidate"));
        if (local) {
            localPeer.addIceCandidate(iceCandidate);
        } else {
            mainActivity.getRemotePeer().addIceCandidate(iceCandidate);
        }
    }

    private void saveAnswer(WebSocket websocket, JSONObject json) throws JSONException {
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdpAnswer"));
        if (localPeer.getRemoteDescription() == null) {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemoteDesc"), sessionDescription);
        } else {
            mainActivity.getRemotePeer().setRemoteDescription(new CustomSdpObserver("remoteSetRemoteDesc"), sessionDescription);
        }
    }

    public void sendJson(WebSocket webSocket, String method, Map<String, String> params) {
        try {
            JSONObject paramsJson = new JSONObject();
            for (Map.Entry<String, String> param : params.entrySet()) {
                paramsJson.put(param.getKey(), param.getValue());
            }
            String jsonString;
            if (method.equals("joinRoom")) {
                jsonString = new JSONObject()
                        .put("id", 1)
                        .put("jsonrpc", JSONRPCVERSION)
                        .put("method", method)
                        .put("params", paramsJson).toString();
            } else if (paramsJson.length() > 0) {
                jsonString = new JSONObject()
                        .put("id", getId())
                        .put("jsonrpc", JSONRPCVERSION)
                        .put("method", method)
                        .put("params", paramsJson).toString();
            } else {
                jsonString = new JSONObject()
                        .put("id", getId())
                        .put("jsonrpc", JSONRPCVERSION)
                        .put("method", method).toString();
            }
            updateId();
            webSocket.sendText(jsonString);
            System.err.println("SALIDA " + jsonString);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void addIceCandidate(Map<String, String> iceCandidateParams) {
        iceCandidatesParams.add(iceCandidateParams);
    }

    public void setLocalOfferParams(Map<String, String> offerParams) {
        this.localOfferParams = offerParams;
    }
}