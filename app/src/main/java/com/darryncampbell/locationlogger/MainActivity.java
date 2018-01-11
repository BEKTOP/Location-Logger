package com.darryncampbell.locationlogger;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.nio.file.WatchKey;
import java.util.ArrayList;

import static com.darryncampbell.locationlogger.Constants.SERVICE_COMMS.FILETYPE;
import static com.darryncampbell.locationlogger.Constants.SERVICE_COMMS.LOCATION_POLL_INTERVAL;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private String mLocationLogFileName;
    private String mLocationPollFrequency; //  Seconds
    private ArrayList<LocationRecord> locationRecordsArray;
    LocationRecordAdapter adapter;
    private LocationUpdateServiceReceiver locationUpdateServiceReceiver;
    private Boolean mLocationLoggingActive = true;
    ProgressDialog locationRecordingDialog;
    private Location mLastGpsLocation = null;
    private Location mLastNetworkLocation = null;
    private Location mLastFusedLocation = null;
    private Location mLastApBasedLocation = null;
    private Constants.OutputType mOutputType = Constants.OutputType.GPX;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        locationRecordingDialog = new ProgressDialog(this);

        setLogFileName();
        setFileType();
        setLocationFrequency();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RecordLocation();
            }
        });

        locationRecordsArray = new ArrayList<LocationRecord>();
        adapter = new LocationRecordAdapter(this, locationRecordsArray);
        ListView listView = (ListView) findViewById(R.id.location_list);
        listView.setAdapter(adapter);

        permissionChecks();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            settings.registerOnSharedPreferenceChangeListener(this);
            return false;
        }
        else if (id == R.id.action_share)
        {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            File path = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS);
            File file;
            String type = "text/csv";
            if (mOutputType == Constants.OutputType.CSV)
                file = new File(path, mLocationLogFileName + ".csv");
            else
            {
                file = new File(path, mLocationLogFileName + ".gpx");
                type = "text/plain";
            }
            Uri fileUri;
            if (Build.VERSION.SDK_INT > 21) { //use this if Lollipop_Mr1 (API 22) or above
                fileUri = FileProvider.getUriForFile(getApplicationContext(), getPackageName()+".fileprovider", file);
            } else {
                fileUri = Uri.fromFile(file);
            }
            sharingIntent.setType(type);
            sharingIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            startActivity(Intent.createChooser(sharingIntent, "Share Log file using"));
        }
        else if (id == R.id.action_clear)
        {
            new AlertDialog.Builder(this)
                    .setTitle("Clear?")
                    .setMessage("This will clear all captured positions. Proceed?")
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            {
                                Intent startIntent = new Intent(MainActivity.this, LocationUpdateService.class);
                                startIntent.setAction(Constants.ACTION.CLEAR_PINNED_LOCATIONS);
                                startService(startIntent);
                            }
                        }})
                    .setNegativeButton(android.R.string.no, null).show();
        }
        else if (id == R.id.action_pin_location)
        {
            RecordLocation();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if (key.equals(getString(R.string.log_filename_key)))
        {
            //  Log file name has changed
            setLogFileName();
            if (mLocationLoggingActive)
            {
                Intent startIntent = new Intent(MainActivity.this, LocationUpdateService.class);
                startIntent.putExtra(Constants.SERVICE_COMMS.FILENAME, mLocationLogFileName);
                startIntent.setAction(Constants.ACTION.FILENAME_CHANGED);
                startService(startIntent);
            }
        }
        else if (key.equals(getString(R.string.location_frequency_key)))
        {
            //  Location update frequency has changed
            setLocationFrequency();
            if (mLocationLoggingActive)
            {
                Intent startIntent = new Intent(MainActivity.this, LocationUpdateService.class);
                startIntent.putExtra(LOCATION_POLL_INTERVAL, mLocationPollFrequency);
                startIntent.setAction(Constants.ACTION.POLL_INTERVAL_CHANGED);
                startService(startIntent);
            }
        }
        else if (key.equals(getString(R.string.location_tracking_key)))
        {
            Boolean bLocationTracking = SP.getBoolean(getString(R.string.location_tracking_key), true);
            if (bLocationTracking)
                StartLocationUpdates();
            else
                StopLocationUpdates();
        }
        else if (key.equals(getString(R.string.keep_screen_on_key)))
        {
            Boolean bKeepScreenOn = SP.getBoolean(getString(R.string.keep_screen_on_key), true);
            if (bKeepScreenOn)
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else if (key.equals(getString(R.string.filetype_key)))
        {
            String fileTypeAsString = setFileType();
            if (mLocationLoggingActive)
            {
                Intent startIntent = new Intent(MainActivity.this, LocationUpdateService.class);
                startIntent.putExtra(FILETYPE, fileTypeAsString);
                startIntent.setAction(Constants.ACTION.FILETYPE_CHANGED);
                startService(startIntent);
            }
        }
    }

    private void setLogFileName()
    {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        mLocationLogFileName = SP.getString(getString(R.string.log_filename_key), getString(R.string.pref_default_file_name));
    }

    private void setLocationFrequency()
    {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        mLocationPollFrequency = SP.getString(getString(R.string.location_frequency_key), getString(R.string.pref_default_location_frequency));
    }
    private String setFileType()
    {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String tempOutputType = SP.getString(getString(R.string.filetype_key), getString(R.string.pref_default_filetype));
        if (tempOutputType.equalsIgnoreCase("csv"))
            mOutputType = Constants.OutputType.CSV;
        else
            mOutputType= Constants.OutputType.GPX;
        return tempOutputType;
    }

    @Override
    public void onResume()
    {
        super.onResume();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        //  We are only listening for this when the Settings activity is shown
        settings.unregisterOnSharedPreferenceChangeListener(this);

        Boolean bKeepScreenOn = settings.getBoolean(getString(R.string.keep_screen_on_key), true);
        if (bKeepScreenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (locationUpdateServiceReceiver == null)
            locationUpdateServiceReceiver = new LocationUpdateServiceReceiver();
        //  Listen for messages from the LocationUpdateService
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.SERVICE_COMMS.LOCATION_RECORD_LIST);
        intentFilter.addAction(Constants.SERVICE_COMMS.LOCATION_UPDATED);
        intentFilter.addAction(Constants.SERVICE_COMMS.LOCATION_STATUS_UPDATED);
        registerReceiver(locationUpdateServiceReceiver, intentFilter);

        //  Update the UI in case logging happened whilst we were minimized
        Intent startIntent = new Intent(MainActivity.this, LocationUpdateService.class);
        startIntent.setAction(Constants.ACTION.REQUEST_LATEST_DATA);
        startService(startIntent);
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (locationUpdateServiceReceiver != null)
            unregisterReceiver(locationUpdateServiceReceiver);
    }

    @Override
    public void onStart()
    {
        super.onStart();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode)
        {
            //  Just require both permissions, we don't have a reduced functionality state (other permissions
            //  do not require user to agree)
            case Constants.PERMISSION.PERMISSION_REQUEST:
            {
                if (grantResults.length >= Constants.PERMISSION.NUM_PERMISSIONS_REQUESTED && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED)
                {
                    //  Permission was granted for writing to external storage & location
                    StartLocationUpdates();
                }
                else
                {
                    //  Permission to write to external storage was denied
                    Log.e(Constants.LOG.LOG_TAG, "External storage card access & Location are required to run this application & save locations");
                    Toast.makeText(this, "External storage card access & Location are required to run this application & save locations", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }

    public void permissionChecks()
    {
        int permissionCheckStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionCheckLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheckStorage != PackageManager.PERMISSION_GRANTED || permissionCheckLocation != PackageManager.PERMISSION_GRANTED)
        {
            //  Request permission to write to the Storage card
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION},
                    Constants.PERMISSION.PERMISSION_REQUEST);
        }
        else
        {
            //  Only start location updates if we have permission.  This is called on launch and when the
            //  setting is changed.
            StartLocationUpdates();
        }
    }

    private void StartLocationUpdates()
    {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        Boolean bLocationTracking = SP.getBoolean(getString(R.string.location_tracking_key), true);
        if (bLocationTracking)
        {
            //  Check that location is enabled
            if (!Utilities.isLocationEnabled(this))
            {
                //  Change the app's 'location tracking' setting to False
                Toast.makeText(this, "Device location is not enabled", Toast.LENGTH_LONG).show();
                SharedPreferences.Editor editor = SP.edit();
                editor.putBoolean(getString(R.string.location_tracking_key), true);
                editor.commit();
            }
            else
            {
                Intent startIntent = new Intent(MainActivity.this, LocationUpdateService.class);
                startIntent.putExtra(LOCATION_POLL_INTERVAL, mLocationPollFrequency);
                startIntent.putExtra(Constants.SERVICE_COMMS.FILENAME, mLocationLogFileName);
                startIntent.setAction(Constants.ACTION.START_LOCATION_UPDATE_SERVCIE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(startIntent);
                }
                else
                {
                startService(startIntent);
                }
                mLocationLoggingActive = true;
            }
        }
        else
        {
            UpdateUiLocationTrackingDisabled();
        }
    }

    private void StopLocationUpdates()
    {
        mLocationLoggingActive = true;
        Intent startIntent = new Intent(MainActivity.this, LocationUpdateService.class);
        startIntent.setAction(Constants.ACTION.STOP_LOCATION_UPDATE_SERVICE);
        startService(startIntent);

        //  Update the UI
        UpdateUiLocationTrackingDisabled();
    }

    private void RecordLocation()
    {
        //  Request the Location Update Service to record the location
        if (mLocationLoggingActive)
        {
            Toast.makeText(this,"RecordLocation",Toast.LENGTH_LONG).show();
            locationRecordingDialog.setMessage("Locating..");
            locationRecordingDialog.setTitle("Retrieving location from nearby APs");
            locationRecordingDialog.setIndeterminate(false);
            locationRecordingDialog.setCancelable(true);
            locationRecordingDialog.show();

            //  Send a message to the Service that we want to record a location
            Intent startIntent = new Intent(MainActivity.this, LocationUpdateService.class);
            startIntent.setAction(Constants.ACTION.RECORD_LOCATION);
            startService(startIntent);
        }
        else
        {
            Toast.makeText(this, "Location Tracking is disabled", Toast.LENGTH_LONG).show();
        }
    }

    private void UpdateUiLocationTrackingDisabled() {
        TextView txtGpsLocation = findViewById(R.id.txtCurrentGpsLocation);
        TextView txtNetworkLocation = findViewById(R.id.txtCurrentNetworkLocation);
        TextView txtFusedLocation = findViewById(R.id.txtCurrentFusedLocation);
        TextView txtApBasedLocation = findViewById(R.id.txtCurrentApBasedLocation);
        txtGpsLocation.setText("Location Tracking Disabled");
        txtNetworkLocation.setText("Location Tracking Disabled");
        txtFusedLocation.setText("Location Tracking Disabled");
        txtApBasedLocation.setText("Location Tracking Disabled");
    }

    private class LocationUpdateServiceReceiver extends BroadcastReceiver {
        //  Receive messages from the LocationUpdateService

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.SERVICE_COMMS.LOCATION_RECORD_LIST))
            {
                //  Received the authoritative list of recorded locations from the service
                ArrayList<LocationRecord> receivedArray = intent.getParcelableArrayListExtra("record_list");
                locationRecordsArray.clear();
                if (receivedArray != null)
                {
                    for (int i = 0; i < receivedArray.size(); i++)
                        locationRecordsArray.add(receivedArray.get(i));
                }
                adapter.notifyDataSetChanged();
            }
            else if (intent.getAction().equals(Constants.SERVICE_COMMS.LOCATION_UPDATED))
            {
                //  This is the current location of the device (to show on the UI)
                Location location = intent.getParcelableExtra("location");
                if (location != null)
                {
                    if (location.getProvider().equals(LocationManager.GPS_PROVIDER))
                    {
                        //  GPS update
                        mLastGpsLocation = location;
                        TextView txtGpsLocation = (TextView) findViewById(R.id.txtCurrentGpsLocation);
                        txtGpsLocation.setText(Utilities.formatLocationForUi(mLastGpsLocation));
                    }
                    else if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER))
                    {
                        //  Network update
                        mLastNetworkLocation = location;
                        TextView txtNetworkLocation = (TextView) findViewById(R.id.txtCurrentNetworkLocation);
                        txtNetworkLocation.setText(Utilities.formatLocationForUi(mLastNetworkLocation));
                    }
                    else if (location.getProvider().equals("fused"))
                    {
                        //  Fused update
                        mLastFusedLocation = location;
                        TextView txtFusedLocation = (TextView) findViewById(R.id.txtCurrentFusedLocation);
                        txtFusedLocation.setText(Utilities.formatLocationForUi(mLastFusedLocation));
                    }
                    else if (location.getProvider().equals(GMapsGeolocationAPIWrapper.GEOLOCATE_PROVIDER))
                    {
                        //  AP based update
                        mLastApBasedLocation = location;
                        TextView txtApBasedLocation = (TextView) findViewById(R.id.txtCurrentApBasedLocation);
                        txtApBasedLocation.setText(Utilities.formatLocationForUi(mLastApBasedLocation));
                        locationRecordingDialog.dismiss();
                    }
                }
            }
            else if (intent.getAction().equals(Constants.SERVICE_COMMS.LOCATION_STATUS_UPDATED))
            {
                //  This is the status of the specified location provider (to show on the UI e.g. GPS is disabled)
                String provider = intent.getStringExtra("location_provider");
                String status = intent.getStringExtra("status");
                if (provider.equals(LocationManager.GPS_PROVIDER))
                {
                    TextView txtGpsLocation = (TextView) findViewById(R.id.txtCurrentGpsLocation);
                    txtGpsLocation.setText(status);
                }
                else if (provider.equals(LocationManager.NETWORK_PROVIDER))
                {
                    TextView txtNetworkLocation = (TextView) findViewById(R.id.txtCurrentNetworkLocation);
                    txtNetworkLocation.setText(status);
                }
                else if (provider.equals("fused"))
                {
                    TextView txtFusedLocation = (TextView) findViewById(R.id.txtCurrentFusedLocation);
                    txtFusedLocation.setText(status);
                }
                else if (provider.equals(GMapsGeolocationAPIWrapper.GEOLOCATE_PROVIDER))
                {
                    String errorMessageForApLocation = intent.getStringExtra("message");
                    TextView txtApBasedLocation = (TextView) findViewById(R.id.txtCurrentApBasedLocation);
                    txtApBasedLocation.setText(errorMessageForApLocation);
                    locationRecordingDialog.dismiss();
                }
            }
        }
    }
}
