package com.example.Android_Presentation_Trainer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Activity that starts when app is launched
 *
 * @author Philipp Scholl
 * @since 17.05.2019
 *
 */
public class LaunchActivity extends AppCompatActivity {

    // Requesting permission to record audio
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {android.Manifest.permission.RECORD_AUDIO};

    // Text fields for ports and IP address
    EditText etTcpSenderPort;
    EditText etTcpListenerPort;
    EditText etTcpFilePort;
    EditText etUdpSenderPort;
    EditText etUdpListenerPort;
    EditText etIpHub;

    public static final String KEY_TCP_SENDER_PORT = "TCP_SENDER_PORT";
    public static final String KEY_TCP_LISTENER_PORT = "TCP_LISTENER_PORT";
    public static final String KEY_TCP_FILE_PORT = "TCP_FILE_PORT";
    public static final String KEY_UDP_SENDER_PORT = "UDP_SENDER_PORT";
    public static final String KEY_UDP_LISTENER_PORT = "UDP_LISTENER_PORT";
    public static final String KEY_IP_HUB = "IP_HUB";

    // Object for app-wide information/settings storage
    SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        // Get reference to text fields
        etTcpSenderPort = findViewById(R.id.tcpSenderPort);
        etTcpListenerPort = findViewById(R.id.tcpListenerPort);
        etTcpFilePort = findViewById(R.id.tcpFilePort);
        etUdpSenderPort = findViewById(R.id.udpSenderPort);
        etUdpListenerPort = findViewById(R.id.udpListenerPort);
        etIpHub = findViewById(R.id.ipHub);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        // Sets values of text fields from shared preferences
        if (sharedPref.contains(KEY_TCP_SENDER_PORT)) {
            etTcpSenderPort.setText(String.valueOf(sharedPref.getInt(KEY_TCP_SENDER_PORT, 00000)));
        }
        if (sharedPref.contains(KEY_TCP_LISTENER_PORT)) {
            etTcpListenerPort.setText(String.valueOf(sharedPref.getInt(KEY_TCP_LISTENER_PORT, 00000)));
        }
        if (sharedPref.contains(KEY_TCP_FILE_PORT)) {
            etTcpFilePort.setText(String.valueOf(sharedPref.getInt(KEY_TCP_FILE_PORT, 00000)));
        }
        if (sharedPref.contains(KEY_UDP_SENDER_PORT)) {
            etUdpSenderPort.setText(String.valueOf(sharedPref.getInt(KEY_UDP_SENDER_PORT, 00000)));
        }
        if (sharedPref.contains(KEY_UDP_LISTENER_PORT)) {
            etUdpListenerPort.setText(String.valueOf(sharedPref.getInt(KEY_UDP_LISTENER_PORT, 00000)));
        }
        if (sharedPref.contains(KEY_IP_HUB)) {
            etIpHub.setText(sharedPref.getString(KEY_IP_HUB, "0000"));
        }

        //Log.i("MA", "ON CREATE");

    }

    // Requests permission for recording audio which appears when starting the app the first time
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }

    /**
     * Saves values set to text fields in app-wide shared preferences (like app settings)
     *
     */
    public void saveSettings(View v) {
        int tcpSenderPort  = Integer.valueOf(etTcpSenderPort.getText().toString());
        int tcpListenerPort  = Integer.valueOf(etTcpListenerPort.getText().toString());
        int tcpFilePort  = Integer.valueOf(etTcpFilePort.getText().toString());
        int udpSenderPort  = Integer.valueOf(etUdpSenderPort.getText().toString());
        int udpListenerPort  = Integer.valueOf(etUdpListenerPort.getText().toString());
        String ipHub  = etIpHub.getText().toString();

        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putInt(KEY_TCP_SENDER_PORT, tcpSenderPort);
        editor.putInt(KEY_TCP_LISTENER_PORT, tcpListenerPort);
        editor.putInt(KEY_TCP_FILE_PORT, tcpFilePort);
        editor.putInt(KEY_UDP_SENDER_PORT, udpSenderPort);
        editor.putInt(KEY_UDP_LISTENER_PORT, udpListenerPort);
        editor.putString(KEY_IP_HUB, ipHub);

        editor.commit();

        Toast toast = Toast.makeText(this, "Saved", Toast.LENGTH_SHORT);
        toast.show();
    }

    /**
     * Switches to the main activity on click of "Initialize" button
     *
     * @param view
     */
    public void initialize(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Log.i("MA", "ON PAUSE");
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Log.i("MA", "ON RESUME");
    }

    @Override
    protected void onStop() {
        super.onStop();
        //Log.i("MA", "ON STOP");
    }

}
