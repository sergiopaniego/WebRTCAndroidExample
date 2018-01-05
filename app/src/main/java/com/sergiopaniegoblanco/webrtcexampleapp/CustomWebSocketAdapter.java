package com.sergiopaniegoblanco.webrtcexampleapp;

import android.os.Handler;
import android.util.Log;

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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by sergiopaniegoblanco on 02/12/2017.
 */

public final class CustomWebSocketAdapter implements WebSocketListener {

    private static final String TAG = "CustomWebSocketAdapter";
    private static final String JSON_RPCVERSION = "2.0";
    private static final int PING_MESSAGE_INTERVAL = 3;

    private MainActivity mainActivity;
    private PeerConnection localPeer;
    private int id;
    private String sessionId;
    private List<Map<String, String>> iceCandidatesParams;
    private Map<String, String> localOfferParams;
    private String userId;
    private String remoteUserId;
    private String sessionName;
    private String participantName;

    public CustomWebSocketAdapter(MainActivity mainActivity, PeerConnection localPeer, String sessionName, String participantName) {
        this.mainActivity = mainActivity;
        this.localPeer = localPeer;
        this.id = 0;
        iceCandidatesParams = new ArrayList<>();
        this.sessionName = sessionName;
        this.participantName = participantName;
    }

    public String getUserId() {
        return userId;
    }
    public String getRemoteUserId() {
        return remoteUserId;
    }
    public int getId() {
        return id;
    }
    public void updateId() {
        id++;
    }

    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
        Log.i(TAG, "State changed: " + newState.name());
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        Log.i(TAG, "Connected");

        pingMessageHandler(websocket);

        Map<String, String> joinRoomParams = new HashMap<>();
        joinRoomParams.put("dataChannels", "false");
        joinRoomParams.put("metadata", "{\"clientData\": \"" + participantName + "\"}");
        joinRoomParams.put("secret", "MY_SECRET");
        joinRoomParams.put("session", "wss://demos.openvidu.io:8443/" + sessionName);
        joinRoomParams.put("token", "gr50nzaqe6avt65cg5v06");
        sendJson(websocket, "joinRoom", joinRoomParams);


        if (localOfferParams != null) {
            sendJson(websocket, "publishVideo", localOfferParams);
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
        }, 0L, PING_MESSAGE_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.i(TAG, "Connect error: " + cause);
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        Log.i(TAG, "Disconnected " + serverCloseFrame.getCloseReason() + " " + clientCloseFrame.getCloseReason() + " " + closedByServer);
    }

    @Override
    public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame");
    }

    @Override
    public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Continuation Frame");
    }

    @Override
    public void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Text Frame");
    }

    @Override
    public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Binary Frame");
    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Close Frame");
    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Ping Frame");
    }

    @Override
    public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Pong Frame");
    }

    @Override
    public void onTextMessage(final WebSocket websocket, String text) throws Exception {
        Log.i(TAG, "Text Message " + text);
        JSONObject json = new JSONObject(text);
        if (json.has("result")) {
            handleResult(websocket, json);
        } else {
            handleMethod(websocket, json);
        }
    }

    private void handleResult(final WebSocket webSocket, JSONObject json) throws Exception {
        JSONObject result = new JSONObject(json.getString("result"));
        if (result.has("sdpAnswer")) {
            saveAnswer(result);
        } else if (result.has("sessionId")) {
            if (result.has("value")) {
                if (result.getJSONArray("value").length() != 0) {
                    this.remoteUserId = result.getJSONArray("value").getJSONObject(0).getString("id");
                    setRemoteParticipantName(new JSONObject(result.getJSONArray("value").getJSONObject(0).getString("metadata")).getString("clientData"));
                    mainActivity.call();
                    mainActivity.getRemotePeer().createOffer(new CustomSdpObserver("remoteCreateOffer") {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                            super.onCreateSuccess(sessionDescription);
                            mainActivity.getRemotePeer().setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc"), sessionDescription);
                            Map<String, String> remoteOfferParams = new HashMap<>();
                            remoteOfferParams.put("sdpOffer", sessionDescription.description);
                            remoteOfferParams.put("sender", remoteUserId + "_webcam");
                            sendJson(webSocket, "receiveVideoFrom", remoteOfferParams);
                        }
                    }, new MediaConstraints());
                }
                this.userId = result.getString("id");
                for (Map<String, String> iceCandidate : iceCandidatesParams) {
                    iceCandidate.put("endpointName", this.userId);
                    sendJson(webSocket, "onIceCandidate", iceCandidate);
                }
            }
            this.sessionId = result.getString("sessionId");
        } else if (result.has("value")) {
            Log.i(TAG, "pong");
        } else {
            Log.e(TAG, "Unrecognized " + result);
        }
    }

    private void handleMethod(final WebSocket webSocket, JSONObject json) throws Exception {
        if(!json.has("params")) {
            Log.e(TAG, "No params");
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
                    setRemoteParticipantName(new JSONObject(params.getString("metadata")).getString("clientData"));
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
                            sendJson(webSocket, "receiveVideoFrom", remoteOfferParams);
                        }
                    }, new MediaConstraints());
                    break;
                case "participantLeft":
                    mainActivity.setRemotePeer(null);
                    break;
            }
        }
    }

    public void setRemoteParticipantName(final String name) {
        Handler mainHandler = new Handler(mainActivity.getMainLooper());

        Runnable myRunnable = new Runnable() {
            @Override
            public void run() { mainActivity.setRemoteParticipantName(name); } // This is your code
        };
        mainHandler.post(myRunnable);
    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
        Log.i(TAG, "Binary Message");
    }

    @Override
    public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Sending Frame");
    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame sent");
    }

    @Override
    public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame unsent");
    }

    @Override
    public void onThreadCreated(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {
        Log.i(TAG, "Thread created");
    }

    @Override
    public void onThreadStarted(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {
        Log.i(TAG, "Thread started");
    }

    @Override
    public void onThreadStopping(WebSocket websocket, ThreadType threadType, Thread thread) throws Exception {
        Log.i(TAG, "Thread stopping");
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.i(TAG, "Error! " + cause);
    }

    @Override
    public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Frame error");
    }

    @Override
    public void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) throws Exception {
        Log.i(TAG, "Message error! "+ cause);
    }

    @Override
    public void onMessageDecompressionError(WebSocket websocket, WebSocketException cause, byte[] compressed) throws Exception {
        Log.i(TAG, "Message Decompression Error");
    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws Exception {
        Log.i(TAG, "Text Message Error! " + cause);
    }

    @Override
    public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {
        Log.i(TAG, "Send Error! " + cause);
    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.i(TAG, "Unexpected error! " + cause);
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {
        Log.i(TAG, "Handle callback error! " + cause);
    }

    @Override
    public void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]> headers) throws Exception {
        Log.i(TAG, "Sending Handshake! Hello!");
    }

    private void saveIceCandidate(JSONObject json, boolean local) throws JSONException {
        IceCandidate iceCandidate = new IceCandidate(json.getString("sdpMid"), Integer.parseInt(json.getString("sdpMLineIndex")), json.getString("candidate"));
        if (local) {
            localPeer.addIceCandidate(iceCandidate);
        } else {
            mainActivity.getRemotePeer().addIceCandidate(iceCandidate);
        }
    }

    private void saveAnswer(JSONObject json) throws JSONException {
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
            JSONObject jsonObject = new JSONObject();
            if (method.equals("joinRoom")) {
                jsonObject.put("id", 1)
                        .put("params", paramsJson).toString();
            } else if (paramsJson.length() > 0) {
                jsonObject.put("id", getId())
                        .put("params", paramsJson).toString();
            } else {
                jsonObject.put("id", getId());
            }
            jsonObject.put("jsonrpc", JSON_RPCVERSION)
                    .put("method", method);
            String jsonString = jsonObject.toString();
            updateId();
            webSocket.sendText(jsonString);
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