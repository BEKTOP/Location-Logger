package com.darryncampbell.locationlogger;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

//  This class taken and modified slightly from https://github.com/darryncampbell/Location-API-Exerciser

public class LocationManagerWrapper {

    Location gpsLocationAosp;
    Location networkLocationAosp;
    LocationManager locationManager;
    Context context;
    LocationListener gpsListener;
    LocationListener networkListener;
    Boolean mStarted;

    public LocationManagerWrapper(Context c) {
        context = c;
        locationManager = (LocationManager) context.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        gpsListener = null;
        networkListener = null;
        mStarted = false;
    }

    public void stopAospLocation() {
        mStarted = false;

        if (gpsListener != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(gpsListener);
            }
            gpsListener = null;
        }
        if (networkListener != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(networkListener);
            }
            networkListener = null;
        }
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

    public void startAospLocation(int timeBetweenUpdatesInSeconds) {
        if (mStarted)
            return;
        mStarted = true;
        int timeBetweenUpdatesInMs = timeBetweenUpdatesInSeconds * 1000;

        gpsListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                gpsLocationAosp = location;
                Log.i(Constants.LOG.LOG_TAG, "Received Location from GPS: " + location.toString());
                sendLocationUpdate(location);
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {
                switch (i) {
                    case LocationProvider.OUT_OF_SERVICE:
                        sendLocationStatusUpdate(LocationManager.GPS_PROVIDER, "Out of Service");
                        break;
                    case LocationProvider.TEMPORARILY_UNAVAILABLE:
                        //txtGpsProviderStatus.setText("Temporarily Down");
                        sendLocationStatusUpdate(LocationManager.GPS_PROVIDER, "Temporarily Down");
                        break;
                    case LocationProvider.AVAILABLE:
                        //  No need to modify UI as UI shared with actual location
                        break;
                    default:
                        sendLocationStatusUpdate(LocationManager.GPS_PROVIDER, "Error");
                }
            }

            @Override
            public void onProviderEnabled(String s)
            {
                Log.i(Constants.LOG.LOG_TAG, "GPS Provider is Enabled");
            }

            @Override
            public void onProviderDisabled(String s)
            {
                sendLocationStatusUpdate(LocationManager.GPS_PROVIDER, "Disabled");
                Log.i(Constants.LOG.LOG_TAG, "GPS Provider is disabled");
            }
        };


        networkListener = new LocationListener()
        {
            @Override
            public void onLocationChanged(Location location) {
                networkLocationAosp = location;
                Log.i(Constants.LOG.LOG_TAG, "Received Location from Network: " + location.toString());
                sendLocationUpdate(location);
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {
                switch (i) {
                    case LocationProvider.OUT_OF_SERVICE:
                        sendLocationStatusUpdate(LocationManager.NETWORK_PROVIDER, "Out of Service");
                        break;
                    case LocationProvider.TEMPORARILY_UNAVAILABLE:
                        sendLocationStatusUpdate(LocationManager.NETWORK_PROVIDER, "Temporarily Down");
                        break;
                    case LocationProvider.AVAILABLE:
                        break;
                    default:
                        sendLocationStatusUpdate(LocationManager.NETWORK_PROVIDER, "Error");
                }
            }

            @Override
            public void onProviderEnabled(String s)
            {
                Log.i(Constants.LOG.LOG_TAG, "Network provider is enabled");
            }

            @Override
            public void onProviderDisabled(String s) {
                sendLocationStatusUpdate(LocationManager.NETWORK_PROVIDER, "Disabled");
                Log.i(Constants.LOG.LOG_TAG, "Network provider is disabled");
            }
        };


        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //  Just to get rid of Eclipse warnings
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, timeBetweenUpdatesInMs, 0, gpsListener);
            Location lastGpsPosition = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastGpsPosition != null)
            {
                gpsLocationAosp = lastGpsPosition;
            }
            sendLocationUpdate(gpsLocationAosp);
        }
        else
        {
            sendLocationStatusUpdate(LocationManager.GPS_PROVIDER, "Disabled");
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, timeBetweenUpdatesInMs, 0, networkListener);
            Location lastNetworkPosition = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (lastNetworkPosition != null)
            {
                networkLocationAosp = lastNetworkPosition;
            }
            sendLocationUpdate(networkLocationAosp);
        }
        else
        {
            sendLocationStatusUpdate(LocationManager.NETWORK_PROVIDER, "Disabled");
        }

    }

}

