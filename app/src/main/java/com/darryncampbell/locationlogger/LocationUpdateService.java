package com.darryncampbell.locationlogger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Window;

import java.util.ArrayList;

//  This class taken and modified slightly from https://github.com/darryncampbell/Location-API-Exerciser

//  The LocationUpdateService class is the authority on where the device is and has been
public class LocationUpdateService extends Service {

    LocationManagerWrapper locationManagerWrapper = null;
    LocationServicesWrapper locationServicesWrapper = null;
    GMapsGeolocationAPIWrapper gmapsGeolocationAPIWrapper = null;
    Boolean mStarted = true;
    private Location mLastGpsLocation = null;
    private Location mLastNetworkLocation = null;
    private Location mLastFusedLocation = null;
    private Location mLastApBasedLocation = null;
    private String mLastApBasedLocationError = "";
    //  Recorded positions
    ArrayList<LocationRecord> locationRecordsArray = new ArrayList<>();
    private Storage storage;
    private String storageFileName = "default";
    private Constants.OutputType mOutputType = Constants.OutputType.GPX;

    public LocationUpdateService() {
        storage = new Storage();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        if (intent.getAction().equals(Constants.ACTION.START_LOCATION_UPDATE_SERVCIE))
        {
            if (mStarted)
            {
                //  The service exists so send the latest data back to the UI.
                //  This enables the service to run in the background and when the main activity is
                //  launched it can be updated.
                updateMainActivityUi();
            }
            else
            {
                mStarted = true;
                //  Start location updates
                //  Create the notification that will indicate this is a foreground service
                //  Use a foreground service for a number of reasons, one of which being to work with Oreo Background restrictions
                Intent notificationIntent = new Intent(this, MainActivity.class);
                notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                        notificationIntent, 0);
                Intent recordLocationIntent = new Intent(this, LocationUpdateService.class);
                recordLocationIntent.setAction(Constants.ACTION.RECORD_LOCATION);
                PendingIntent pRecordLocationIntent = PendingIntent.getService(this, 0,
                        recordLocationIntent, 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel notificationChannel = new NotificationChannel(Constants.NOTIFICATION_ID.LOCATION_UPDATE_CHANNEL_ID,
                            Constants.NOTIFICATION_ID.LOCATION_UPDATE_CHANNEL, NotificationManager.IMPORTANCE_HIGH);
                    NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.createNotificationChannel(notificationChannel);
                }
                Notification notification = new NotificationCompat.Builder(this, Constants.NOTIFICATION_ID.LOCATION_UPDATE_CHANNEL_ID)
                        .setContentTitle("Location Logger is running")
                        .setTicker("Location Logger")
                        .setContentText("Track your journey by recording waypoints")
                        .setSmallIcon(R.drawable.ic_stat_name)
                        .setChannelId(Constants.NOTIFICATION_ID.LOCATION_UPDATE_CHANNEL_ID)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_pin_drop,
                                "Record Location", pRecordLocationIntent).build();
                startForeground(Constants.NOTIFICATION_ID.LOCATION_UPDATE_SERVICE,
                        notification);

                //  Start the actual location updates
                String szUpdateFrequency = intent.getStringExtra(Constants.SERVICE_COMMS.LOCATION_POLL_INTERVAL);
                storageFileName = intent.getStringExtra(Constants.SERVICE_COMMS.FILENAME);
                //  These classes are taken and modified slightly from https://github.com/darryncampbell/Location-API-Exerciser
                locationManagerWrapper = new LocationManagerWrapper(getApplicationContext());
                locationManagerWrapper.startAospLocation(Integer.parseInt(szUpdateFrequency));
                locationServicesWrapper = new LocationServicesWrapper(getApplicationContext());
                locationServicesWrapper.onStart(Integer.parseInt(szUpdateFrequency));
                gmapsGeolocationAPIWrapper = new GMapsGeolocationAPIWrapper(getApplicationContext());

                updateMainActivityUi();
            }
        }
        else if (intent.getAction().equals(Constants.ACTION.POLL_INTERVAL_CHANGED))
        {
            //  This is the interval we request from the location providers, the actual rate that
            // the position is reported may vary.
            String szUpdateFrequency = intent.getStringExtra(Constants.SERVICE_COMMS.LOCATION_POLL_INTERVAL);
            if (locationManagerWrapper != null)
            {
                locationManagerWrapper.stopAospLocation();
                locationManagerWrapper.startAospLocation(Integer.parseInt(szUpdateFrequency));
            }
            if (locationServicesWrapper != null)
            {
                locationServicesWrapper.onStop();
                locationServicesWrapper.onStart(Integer.parseInt(szUpdateFrequency));
            }
        }
        else if (intent.getAction().equals(Constants.ACTION.FILENAME_CHANGED))
        {
            storageFileName = intent.getStringExtra(Constants.SERVICE_COMMS.FILENAME);
            storage.persistLocationRecordsToFile(getApplicationContext(), locationRecordsArray, storageFileName, mOutputType);
        }
        else if (intent.getAction().equals(Constants.ACTION.FILETYPE_CHANGED))
        {
            String tempFileType = intent.getStringExtra(Constants.SERVICE_COMMS.FILETYPE);
            if (tempFileType.equalsIgnoreCase("csv"))
                mOutputType = Constants.OutputType.CSV;
            else
                mOutputType = Constants.OutputType.GPX;
            storage.persistLocationRecordsToFile(getApplicationContext(), locationRecordsArray, storageFileName, mOutputType);
        }
        else if (intent.getAction().equals(Constants.ACTION.STOP_LOCATION_UPDATE_SERVICE))
        {
            //  Stop location updates
            mStarted = false;
            if (locationManagerWrapper != null)
                locationManagerWrapper.stopAospLocation();
            if (locationServicesWrapper != null)
                locationServicesWrapper.onStop();

            stopForeground(true);
            stopSelf();
        }
        else if (intent.getAction().equals(Constants.SERVICE_COMMS.LOCATION_UPDATED))
        {
            //  A location has been updated from a provider.  Update the UI and internal variables
            Location location = intent.getParcelableExtra("location");
            if (location != null)
            {
                if (location.getProvider().equals(LocationManager.GPS_PROVIDER))
                {
                    //  GPS update
                    mLastGpsLocation = location;
                }
                else if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER))
                {
                    //  Network update
                    mLastNetworkLocation = location;
                }
                else if (location.getProvider().equals("fused"))
                {
                    //  Fused update
                    mLastFusedLocation = location;
                }
                //  Send the location to the UI if it is listening
                Intent locationIntent = new Intent(Constants.SERVICE_COMMS.LOCATION_UPDATED);
                locationIntent.putExtra("location", location);
                sendBroadcast(locationIntent);
                //if (mSaveOnEveryLocationUpdate)
                //{
                //    Does not really make sense to do this because the AP based location is only triggered manually (otherwise the API quota would probably exceed if location left on)
                //    saveRecordsToFile();
                //}
            }
        }
        else if (intent.getAction().equals(Constants.SERVICE_COMMS.GEOLOCATION_API_RETURNED) ||
                intent.getAction().equals(Constants.SERVICE_COMMS.GEOLOCATION_API_STATUS))
        {
            //  A call to the Geolocation REST API has returned, OK to now record the position.
            if (intent.hasExtra("location"))
            {
                //  Geolocation returned an actual location
                Location location = intent.getParcelableExtra("location");
                mLastApBasedLocation = location;
                mLastApBasedLocationError = "";
                Intent locationIntent = new Intent(Constants.SERVICE_COMMS.LOCATION_UPDATED);
                locationIntent.putExtra("location", location);
                sendBroadcast(locationIntent);
            }
            else
            {
                //  Geolocation returned some error e.g. no APs found
                String message = intent.getStringExtra("message");
                mLastApBasedLocation = null;
                mLastApBasedLocationError = message;
                Intent locationIntent = new Intent(Constants.SERVICE_COMMS.LOCATION_STATUS_UPDATED);
                locationIntent.putExtra("location_provider", GMapsGeolocationAPIWrapper.GEOLOCATE_PROVIDER);
                locationIntent.putExtra("message", message);
                sendBroadcast(locationIntent);
            }

            LocationRecord record = new LocationRecord(mLastGpsLocation, mLastNetworkLocation, mLastFusedLocation, mLastApBasedLocation, mLastApBasedLocationError);
            locationRecordsArray.add(record);
            saveRecordsToFile();
            //  Send the updated list to the Activity UI (if listening)
            updateMainActivityUiWithRecordList();
            //  Also update the latest location from the rest of the provideres
            updateMainActivityUi();
        }
        else if (intent.getAction().equals(Constants.ACTION.REQUEST_LATEST_DATA))
        {
            if (mStarted)
            {
                updateMainActivityUiWithRecordList();
                updateMainActivityUi();
            }
        }
        else if (intent.getAction().equals(Constants.SERVICE_COMMS.LOCATION_STATUS_UPDATED))
        {
            //  Status reported from some provider has changed.  Forward this directly to the UI
            Intent locationStatusIntent = new Intent(Constants.SERVICE_COMMS.LOCATION_STATUS_UPDATED);
            locationStatusIntent.putExtras(intent.getExtras());
            sendBroadcast(locationStatusIntent);
        }
        else if (intent.getAction().equals(Constants.ACTION.RECORD_LOCATION))
        {
            if (mStarted)
            {
                //  Record the location
                Log.d(Constants.LOG.LOG_TAG, "Instructed to record location position");

                //  First scan for the location from nearby APs (actual recording will happen in the callback for that)
                gmapsGeolocationAPIWrapper.ScanForAPsAndReportPosition();
            }
        }
        else if (intent.getAction().equals(Constants.ACTION.UPDATE_RECORD_NOTE))
        {
            //  The note data associated with a position can only be updated by the UI
            int pos = intent.getIntExtra("position", -1);
            String noteData = intent.getStringExtra("note_data");
            if (pos > -1 && pos < locationRecordsArray.size())
            {
                LocationRecord temp = locationRecordsArray.get(pos);
                temp.setNote(noteData);
                locationRecordsArray.set(pos, temp);
                saveRecordsToFile();
            }
        }
        else if (intent.getAction().equals(Constants.ACTION.DELETE_RECORD))
        {
            //  UI has requested we delete a position from the recorded positions array
            int pos = intent.getIntExtra("position", -1);
            if (pos > -1 && pos < locationRecordsArray.size())
            {
                locationRecordsArray.remove(pos);
            }
            updateMainActivityUiWithRecordList();
        }
        else if (intent.getAction().equals(Constants.ACTION.CLEAR_PINNED_LOCATIONS))
        {
            //  UI has requested that we clear all pinned positions
            locationRecordsArray.clear();
            saveRecordsToFile();
            updateMainActivityUiWithRecordList();
        }
        return START_REDELIVER_INTENT;
    }

    private void saveRecordsToFile() {
        storage.persistLocationRecordsToFile(getApplicationContext(), locationRecordsArray, storageFileName, mOutputType);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(Constants.LOG.LOG_TAG, "In onDestroy");
    }

    @Override
    public IBinder onBind(Intent intent) {
        //  Used only for bound services.
        return null;
    }

    private void updateMainActivityUi()
    {
        //  Provide the main activity with the data it requires for its UI (current location)
        Intent locationIntent = new Intent(Constants.SERVICE_COMMS.LOCATION_UPDATED);
        locationIntent.putExtra("location", mLastGpsLocation);
        sendBroadcast(locationIntent);
        locationIntent.putExtra("location", mLastNetworkLocation);
        sendBroadcast(locationIntent);
        locationIntent.putExtra("location", mLastFusedLocation);
        sendBroadcast(locationIntent);
        locationIntent.putExtra("location", mLastApBasedLocation);
        sendBroadcast(locationIntent);
        updateMainActivityUiWithRecordList();
    }

    private void updateMainActivityUiWithRecordList()
    {
        //  Provide the main activity with the data it requires for its UI (recorded positions)
        Intent recordListIntent = new Intent(Constants.SERVICE_COMMS.LOCATION_RECORD_LIST);
        recordListIntent.putParcelableArrayListExtra("record_list", locationRecordsArray);
        sendBroadcast(recordListIntent);
    }
}
