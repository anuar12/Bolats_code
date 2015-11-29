package com.frontfootcam.sportshack2015;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import com.firebase.client.Firebase;

import java.util.HashMap;

public class FirebaseClient {
    private String uuid;
    private Firebase playerRef;
    private String defaultUploadWebsite = "https://sportshack15geo.firebaseio.com/players";
    private boolean inRequest;

    public FirebaseClient(String uuid) {
        playerRef = new Firebase(defaultUploadWebsite);
        inRequest = false;
        this.uuid = uuid;
    }

    public void publishRequest(Object req) {
        if (inRequest) {
            for (int i = 0; i < 5; i++);
            inRequest = false;
        }
        playerRef.setValue(req);
        inRequest = false;
    }

    public FirebaseClient getFirebaseRef() {
        return this;
    }

    public void reconnect() {
        playerRef = new Firebase(defaultUploadWebsite);
    }

    public void setReference(Firebase ref) {
        this.playerRef = ref;
    }

    public Firebase getReference() {
        return playerRef;
    }
}

