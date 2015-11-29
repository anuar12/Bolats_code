package com.frontfootcam.sportshack2015;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.firebase.client.Firebase;

import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

import fr.herverenault.selfhostedgpstracker.R;


public class MainActivity extends Activity implements LocationListener {

    private final static String CONNECTIVITY = "android.net.conn.CONNECTIVITY_CHANGE";
    private FirebaseClient firebaseRef;
    private LocationManager locationManager;
    private ConnectivityManager connectivityManager;
    private String uuid;
    SharedPreferences preferences;
    private TextView text_gps_status;
    private TextView text_network_status;
    private ToggleButton button_toggle;
    private TextView text_running_since;
    private TextView last_server_response;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(SelfHostedGPSTrackerService.NOTIFICATION)) {
                String extra = intent.getStringExtra(SelfHostedGPSTrackerService.NOTIFICATION);
                if (extra != null) {
                    updateServerResponse();
                } else {
                    updateServiceStatus();
                }
            }
            if (action.equals(CONNECTIVITY)) {
                updateNetworkStatus();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        uuid = UUID.randomUUID().toString();
        Firebase.setAndroidContext(this);

        text_gps_status = (TextView) findViewById(R.id.text_gps_status);
        text_network_status = (TextView) findViewById(R.id.text_network_status);
        button_toggle = (ToggleButton) findViewById(R.id.button_toggle);
        text_running_since = (TextView) findViewById(R.id.text_running_since);
        last_server_response = (TextView) findViewById(R.id.last_server_response);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);


        registerReceiver(receiver, new IntentFilter(SelfHostedGPSTrackerService.NOTIFICATION));
        registerReceiver(receiver, new IntentFilter(MainActivity.CONNECTIVITY));

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        int pref_gps_updates = Integer.parseInt(preferences.getString("pref_gps_updates", "2")); // seconds
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
        firebaseRef = new FirebaseClient(uuid);
        if (firebaseRef.getFirebaseRef() == null) {
            Log.e("FIREBASE", "unable to connect to Firebase backend");
            firebaseRef.reconnect();
        }
        firebaseRef.setReference(firebaseRef.getReference().push());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            onProviderEnabled(LocationManager.GPS_PROVIDER);
        } else {
            onProviderDisabled(LocationManager.GPS_PROVIDER);
        }

        updateNetworkStatus();

        updateServiceStatus();

        updateServerResponse();

    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.removeUpdates(this);
        unregisterReceiver(receiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent i;
        switch (item.getItemId()) {
            case R.id.menu_settings:
                i = new Intent(this, SelfHostedGPSTrackerPrefs.class);
                startActivity(i);
                break;
            default:
        }
        return super.onOptionsItemSelected(item);
    }

    public void onToggleClicked(View view) {
        Intent intent = new Intent(this, SelfHostedGPSTrackerService.class);
    }

    /* -------------- GPS stuff -------------- */

    @Override
    public void onLocationChanged(Location location) {
        LatLng temp = new LatLng(location.getLatitude(), location.getLongitude());
        firebaseRef.publishRequest(temp);
    }

    @Override
    public void onProviderDisabled(String provider) {
        text_gps_status.setText(getString(R.string.text_gps_status_disabled));
        text_gps_status.setTextColor(Color.RED);
    }

    @Override
    public void onProviderEnabled(String provider) {
        text_gps_status.setText(getString(R.string.text_gps_status_enabled));
        text_gps_status.setTextColor(Color.BLACK);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    /* ----------- utility methods -------------- */
    private void updateServiceStatus() {

        if (SelfHostedGPSTrackerService.isRunning) {
            Toast.makeText(this, getString(R.string.toast_service_running), Toast.LENGTH_SHORT).show();
            button_toggle.setChecked(true);
            text_running_since.setText(getString(R.string.text_running_since) + " "
                    + DateFormat.getDateTimeInstance().format(SelfHostedGPSTrackerService.runningSince.getTime()));
        } else {
            Toast.makeText(this, getString(R.string.toast_service_stopped), Toast.LENGTH_SHORT).show();
            button_toggle.setChecked(false);
            if (preferences.contains("stoppedOn")) {
                long stoppedOn = preferences.getLong("stoppedOn", 0);
                if (stoppedOn > 0) {
                    text_running_since.setText(getString(R.string.text_stopped_on) + " "
                            + DateFormat.getDateTimeInstance().format(new Date(stoppedOn)));
                } else {
                    text_running_since.setText(getText(R.string.text_killed));
                }
            }
        }
    }

    private void updateNetworkStatus() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnectedOrConnecting()) {
            text_network_status.setText(getString(R.string.text_network_status_enabled));
            text_network_status.setTextColor(Color.BLACK);
        } else {
            text_network_status.setText(getString(R.string.text_network_status_disabled));
            text_network_status.setTextColor(Color.RED);
        }
    }

    private void updateServerResponse() {

    }

}