package com.darryncampbell.locationlogger;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;


import java.util.ArrayList;

public class LocationServicesWrapper implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    public static final String FUSED_PROVIDER = "fused";

        protected GoogleApiClient mGoogleApiClient;
        Location fusedLocation;
        Boolean pollingGMS;
        Context context;
        int timeBetweenUpdatesInMs = 5000;

        public LocationServicesWrapper(Context context) {
            pollingGMS = false;
            this.context = context;
        }

        protected synchronized void buildGoogleApiClient() {
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        public GoogleApiClient getGoogleApiClient() {
            return mGoogleApiClient;
        }

        public boolean isConnected() {
            if (mGoogleApiClient != null)
                return mGoogleApiClient.isConnected();
            else
                return false;
        }

        public void initializeAndConnect(int timeBetweenUpdatesInSeconds) {
            timeBetweenUpdatesInMs = timeBetweenUpdatesInSeconds * 1000;
            buildGoogleApiClient();
            if (!mGoogleApiClient.isConnected()) {
                mGoogleApiClient.connect();
            }
        }

        public void onStart(int timeBetweenUpdatesInSeconds) {
            initializeAndConnect(timeBetweenUpdatesInSeconds);
        }

        public void onStop() {
            if (mGoogleApiClient != null) {
                stopGMSLocation();
                if (mGoogleApiClient.isConnected()) {
                    mGoogleApiClient.disconnect();
                }
            }
        }

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.i(Constants.LOG.LOG_TAG, "Location Services connected");
            try {
                fusedLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if (fusedLocation != null) {
                    sendLocationUpdate(fusedLocation);
                } else
                    sendLocationStatusUpdate(FUSED_PROVIDER, "No Location Available");
            }
            catch (SecurityException e)
            {
                Log.e(Constants.LOG.LOG_TAG, "Unhandled Security Exception: " + e.getMessage());
            }
            startGMSLocation();
        }



        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.w(Constants.LOG.LOG_TAG, "Location Services connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
            sendLocationStatusUpdate(FUSED_PROVIDER, "Location Services not connected");
        }


        @Override
        public void onConnectionSuspended(int cause) {
            Log.w(Constants.LOG.LOG_TAG, "Location Services connection suspended");
            sendLocationStatusUpdate(FUSED_PROVIDER, "Location Services not connected");
            mGoogleApiClient.connect();
        }

        public void startGMSLocation()
        {
            if (pollingGMS)
                return;

            pollingGMS = true;

            LocationRequest locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(timeBetweenUpdatesInMs);
            int maxWaitTime = timeBetweenUpdatesInMs / 2 * 3;
            locationRequest.setMaxWaitTime(maxWaitTime);
            int fiveSeconds = 1000 * 5;
            locationRequest.setFastestInterval(fiveSeconds);
            try {
                final PendingResult<Status> result =
                        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
                result.setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (!status.isSuccess()) {
                            //  Something went wrong
                            Log.w(Constants.LOG.LOG_TAG, "Failed to register for Location updates via location services");
                        } else {
                            //  Everything went OK
                            Log.i(Constants.LOG.LOG_TAG, "Location service updates successfully registered for");
                        }
                    }
                });
            }
            catch (SecurityException e)
            {
                Log.e(Constants.LOG.LOG_TAG, "Unhandled Security Exception: " + e.getMessage());
            }
        }

        public void stopGMSLocation()
        {
            pollingGMS = false;
            if (mGoogleApiClient != null && mGoogleApiClient.isConnected())
            {
                final PendingResult<Status> result =
                        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                result.setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (!status.isSuccess())
                        {
                            //  Something went wrong
                            Log.w(Constants.LOG.LOG_TAG, "Failed to unregister Location updates via location services");
                        }
                        else
                        {
                            //  Everything went OK
                            Log.i(Constants.LOG.LOG_TAG, "Location service updates successfully unregistered");
                        }
                    }
                });
            }
        }

        @Override
        public void onLocationChanged(Location location) {
            //  GMS Location Listener
            fusedLocation = location;
            sendLocationUpdate(fusedLocation);
            //ui.UpdateUIWithFusedLocation(fusedLocation);
            Log.i(Constants.LOG.LOG_TAG, "Received location from Google Services: " + location.toString());
        }

    private void sendLocationUpdate(Location location)
    {
        Intent locationUpdatedIntent = new Intent(context, LocationUpdateService.class);
        locationUpdatedIntent.setAction(Constants.SERVICE_COMMS.LOCATION_UPDATED);
        locationUpdatedIntent.putExtra("location", location);
        context.startService(locationUpdatedIntent);
    }

    private void sendLocationStatusUpdate(String locationProvider, String status)
    {
        Intent locationUpdatedIntent = new Intent(context, LocationUpdateService.class);
        locationUpdatedIntent.setAction(Constants.SERVICE_COMMS.LOCATION_STATUS_UPDATED);
        locationUpdatedIntent.putExtra("location_provider", locationProvider);
        locationUpdatedIntent.putExtra("status", status);
        context.startService(locationUpdatedIntent);
    }

}
