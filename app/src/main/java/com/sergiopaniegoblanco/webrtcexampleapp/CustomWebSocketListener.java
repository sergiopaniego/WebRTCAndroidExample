package com.sergiopaniegoblanco.webrtcexampleapp;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Created by sergiopaniegoblanco on 02/12/2017.
 */

public final class CustomWebSocketListener extends WebSocketListener {

    private MainActivity mainActivity;
    private PeerConnection peerConnection;
    private WebSocket webSocket;

    public CustomWebSocketListener(MainActivity mainActivity, PeerConnection peerConnection) {
        super();
        this.mainActivity = mainActivity;
        this.peerConnection = peerConnection;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        System.out.println("Open : " + response);
    }
    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            JSONObject json = new JSONObject(new JSONObject(text).getString("utf8Data"));
            if (json.getString("type").equals("candidate")) {
                saveIceCandidate(json);
            } else {
                if (json.getString("type").equals("OFFER")) {
                    saveOfferAndAnswer(json);
                } else if (json.getString("type").equals("ANSWER")) {
                    saveAnswer(json);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        System.out.println("Receiving bytes : " + bytes.hex());
    }
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        System.out.println("Closing : " + code + " / " + reason);
    }
    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        System.out.println("Error : " + t.getMessage());
    }

    public void saveIceCandidate(JSONObject json) throws JSONException {
        IceCandidate iceCandidate = new IceCandidate(json.getString("id"),Integer.parseInt(json.getString("label")),json.getString("candidate"));
        peerConnection.addIceCandidate(iceCandidate);
    }

    public void saveAnswer(JSONObject json) throws JSONException {
        SessionDescription sessionDescription;
        sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"));
        mainActivity.setRemoteDescription(sessionDescription);
        mainActivity.setRemoteDescription(sessionDescription);
    }

    public void saveOfferAndAnswer(JSONObject json) throws JSONException {
        SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp"));
        peerConnection.setRemoteDescription(new CustomSdpObserver("remoteSetRemoteDesc"), sessionDescription);

        peerConnection.createAnswer(new CustomSdpObserver("remoteCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc"), sessionDescription);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", sessionDescription.type);
                    json.put("sdp", sessionDescription.description);
                    webSocket.send(json.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new MediaConstraints());
    }
}