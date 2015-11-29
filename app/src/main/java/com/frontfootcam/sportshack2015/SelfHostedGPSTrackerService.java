package com.frontfootcam.sportshack2015;

import android.Manifest;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.net.ssl.SSLHandshakeException;

import fr.herverenault.selfhostedgpstracker.R;

public class SelfHostedGPSTrackerService extends IntentService implements LocationListener {

    public static final String NOTIFICATION = "com.frontfootcam.sportshack2015";

    public static boolean isRunning;
    public static Calendar runningSince;
    public static String lastServerResponse;

    public Calendar stoppedOn;

    private final static String MY_TAG = "SelfHostedGPSTrackerSrv";

    private SharedPreferences preferences;
    private String urlText;
    private LocationManager locationManager;
    private int pref_gps_updates;
    private long latestUpdate;
    private int pref_max_run_time;
    private boolean pref_timestamp;
    private FirebaseClient firebaseRef;
    private String params;

    public SelfHostedGPSTrackerService() {
        super("SelfHostedGPSTrackerService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(MY_TAG, "in onCreate, init GPS stuff");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            onProviderEnabled(LocationManager.GPS_PROVIDER);
        } else {
            onProviderDisabled(LocationManager.GPS_PROVIDER);
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("stoppedOn", 0);
        editor.commit();
        pref_gps_updates = Integer.parseInt(preferences.getString("pref_gps_updates", "1")); // seconds
        pref_max_run_time = Integer.parseInt(preferences.getString("pref_max_run_time", "3")); // hours
        pref_timestamp = preferences.getBoolean("pref_timestamp", false);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, pref_gps_updates * 1000, 1, this);

        Intent notifIntent = new Intent(NOTIFICATION);
        notifIntent.putExtra(NOTIFICATION, "START");
        sendBroadcast(notifIntent);



        //new SelfHostedGPSTrackerRequest().start();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(MY_TAG, "in onHandleIntent, run for maximum time set in preferences");

        isRunning = true;
        runningSince = Calendar.getInstance();
        Intent notifIntent = new Intent(NOTIFICATION);
        sendBroadcast(notifIntent);


        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable. ic_notif)
                .setContentTitle("Tracker is on")
                .setWhen(System.currentTimeMillis())
                .setContentIntent(pendingIntent);

        Notification notification = builder.getNotification();
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(R.drawable.ic_notif, notification);
        startForeground(R.id.logo, notification);

        long endTime = System.currentTimeMillis() + pref_max_run_time * 60 * 60 * 1000;
        while (System.currentTimeMillis() < endTime) {
            try {
                Thread.sleep(60 * 1000); // note: when device is sleeping, it may last up to 5 minutes or more
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onDestroy() {
        // (user clicked the stop button, or max run time has been reached)
        Log.d(MY_TAG, "in onDestroy, stop listening to the GPS");
        new SelfHostedGPSTrackerRequest().start("tracker=stop");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.removeUpdates(this);

        isRunning = false;
        stoppedOn = Calendar.getInstance();

        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("stoppedOn", stoppedOn.getTimeInMillis());
        editor.commit();

        Intent notifIntent = new Intent(NOTIFICATION);
        sendBroadcast(notifIntent);
    }

    /* -------------- GPS stuff -------------- */

    @Override
    public void onLocationChanged(Location location) {
        Log.d(MY_TAG, "in onLocationChanged, latestUpdate == " + latestUpdate);
        long currentTime = System.currentTimeMillis();

        // Tolerate devices which sometimes send GPS updates 1 second too early,
        if ((currentTime - latestUpdate) < (pref_gps_updates - 1) * 1000) {
            return;
        }

        latestUpdate = currentTime;

        new SelfHostedGPSTrackerRequest().start(
                "lat=" + location.getLatitude()
                        + "&lon=" + location.getLongitude());
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private class SelfHostedGPSTrackerRequest extends Thread {
        private final static String MY_TAG = "SelfHostedGPSTrackerReq";
        private String params;

        public void run() {

            //LatLng temp = new LatLng(location.getLatitude(), location.getLongitude());
            firebaseRef.publishRequest(params);

            Intent notifIntent = new Intent(NOTIFICATION);
            notifIntent.putExtra(NOTIFICATION, "HTTP");
            sendBroadcast(notifIntent);

        }

        public void start(String params) {
            this.params = params;
            super.start();
        }
    }
}

