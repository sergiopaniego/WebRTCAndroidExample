package com.sergiopaniegoblanco.webrtcexampleapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;


import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;


import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.socket.client.IO;
import io.socket.emitter.Emitter;
import io.socket.client.Socket;

public class MainActivity extends AppCompatActivity {

    private final int MY_PERMISSIONS_REQUEST_CAMERA = 100;
    private final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 101;
    private final int MY_PERMISSIONS_REQUEST = 102;

    private PeerConnection localPeer, remotePeer;
    private PeerConnectionFactory peerConnectionFactory;
    private VideoRenderer remoteRenderer;
    private AudioTrack localAudioTrack;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
    private String socketAddress = "http://10.0.2.2:8080";
    private Socket socket;
    private Socket socket2;


    @BindView(R.id.start_call)
    Button start_call;
    @BindView(R.id.init_call)
    Button init_call;
    @BindView(R.id.end_call)
    Button end_call;
    @BindView(R.id.remote_gl_surface_view)
    SurfaceViewRenderer remoteVideoView;
    @BindView(R.id.local_gl_surface_view)
    SurfaceViewRenderer localVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        askForPermissions();
        ButterKnife.bind(this);
        initViews();
    }

    public void askForPermissions() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST);
        } else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);

        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }

    public void initViews() {
        localVideoView.setMirror(true);
        remoteVideoView.setMirror(false);

        EglBase rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setZOrderMediaOverlay(true);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.setZOrderMediaOverlay(true);
    }

    public void start(View view) {
        start_call.setEnabled(false);
        init_call.setEnabled(true);
        PeerConnectionFactory.initializeAndroidGlobals(this, true);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = new PeerConnectionFactory(options);

        VideoCapturer videoGrabberAndroid = createVideoGrabber();
        MediaConstraints constraints = new MediaConstraints();

        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoGrabberAndroid);
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(constraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        videoGrabberAndroid.startCapture(1000, 1000, 30);

        final VideoRenderer localRenderer = new VideoRenderer(localVideoView);
        localVideoTrack.addRenderer(localRenderer);

        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer iceServer = new PeerConnection.IceServer("stun:stun.l.google.com:19302");
        iceServers.add(iceServer);

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        localPeer = peerConnectionFactory.createPeerConnection(iceServers, sdpConstraints, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", "candidate");
                    json.put("label", iceCandidate.sdpMLineIndex);
                    json.put("id", iceCandidate.sdpMid);
                    json.put("candidate", iceCandidate.sdp);
                    socket.emit("message", json.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            socket = IO.socket(socketAddress);
            socket.on("message", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    SessionDescription sessionDescription = null;
                    try {
                        JSONObject jsonObject = new JSONObject(args[0].toString());
                        if (jsonObject.getString("type").equals("candidate")) {
                            IceCandidate iceCandidate = new IceCandidate(jsonObject.getString("id"),Integer.parseInt(jsonObject.getString("label")),jsonObject.getString("candidate"));
                            localPeer.addIceCandidate(iceCandidate);
                        } else {
                            sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, jsonObject.getString("sdp"));
                            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemoteDesc"), sessionDescription);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            }).on("log", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            }).on("joined", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            }).on("full", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            }).on("created", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            }).on("join", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            });
            socket.connect();
            socket.emit("create or join", "foo");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);

        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", sessionDescription.type);
                    json.put("sdp", sessionDescription.description);
                    socket.emit("message", json.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, sdpConstraints);
    }

    public void call(View view) {
        start_call.setEnabled(false);
        init_call.setEnabled(false);
        end_call.setEnabled(true);
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer iceServer = new PeerConnection.IceServer("stun:stun.l.google.com:19302");
        iceServers.add(iceServer);

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        remotePeer = peerConnectionFactory.createPeerConnection(iceServers, sdpConstraints, new CustomPeerConnectionObserver("remotePeerCreation") {

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", "candidate");
                    json.put("label", iceCandidate.sdpMLineIndex);
                    json.put("id", iceCandidate.sdpMid);
                    json.put("candidate", iceCandidate.sdp);
                    socket.emit("message", json.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                super.onIceGatheringChange(iceGatheringState);

            }
        });

        try {
            socket2 = IO.socket(socketAddress);
            socket2.on("message", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    SessionDescription sessionDescription = null;
                    try {
                        JSONObject jsonObject = new JSONObject(args[0].toString());
                        if (jsonObject.getString("type").equals("candidate")) {
                            IceCandidate iceCandidate = new IceCandidate(jsonObject.getString("id"),Integer.parseInt(jsonObject.getString("label")),jsonObject.getString("candidate"));
                            remotePeer.addIceCandidate(iceCandidate);
                        } else {
                            sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, jsonObject.getString("sdp"));
                            remotePeer.setRemoteDescription(new CustomSdpObserver("remoteSetRemoteDesc"), sessionDescription);

                            remotePeer.createAnswer(new CustomSdpObserver("remoteCreateOffer") {
                                @Override
                                public void onCreateSuccess(SessionDescription sessionDescription) {
                                    super.onCreateSuccess(sessionDescription);
                                    remotePeer.setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc"), sessionDescription);
                                    try {
                                        JSONObject json = new JSONObject();
                                        json.put("type", sessionDescription.type);
                                        json.put("sdp", sessionDescription.description);
                                        socket2.emit("message", json.toString());
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }, new MediaConstraints());
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }).on("log", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            }).on("joined", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            }).on("full", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            }).on("created", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            }).on("join", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                }
            });
            socket2.connect();
            socket2.emit("create or join", "foo");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void hangup(View view) {
        socket.emit("message", "bye");
        socket.close();
        socket2.emit("message", "bye");
        socket2.close();
        localPeer.close();
        remotePeer.close();
        localPeer = null;
        remotePeer = null;
        start_call.setEnabled(true);
        init_call.setEnabled(false);
        end_call.setEnabled(false);
    }

    public VideoCapturer createVideoGrabber() {
        VideoCapturer videoCapturer;
        videoCapturer = createCameraGrabber(new Camera1Enumerator(false));
        return videoCapturer;
    }

    public VideoCapturer createCameraGrabber(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void gotRemoteStream(MediaStream stream) {
        final VideoTrack videoTrack = stream.videoTracks.getFirst();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    remoteRenderer = new VideoRenderer(remoteVideoView);
                    remoteVideoView.setVisibility(View.VISIBLE);
                    videoTrack.addRenderer(remoteRenderer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}