package com.darryncampbell.locationlogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Created by darry on 01/12/2017.
 */

//  This class taken and modified slightly from https://github.com/darryncampbell/Location-API-Exerciser

public class GMapsGeolocationAPIWrapper {

    //  API Usage limits are given here: https://developers.google.com/maps/documentation/geolocation/usage-limits
    //  2,500 free calls per day.  50 requests per second, per user

    Context context;
    private final String GEOLOCATE_URL = "https://www.googleapis.com/geolocation/v1/geolocate?key=";
    private final String GEOLOCATE_KEY = "AIzaSyDNsRNkiJddjICdCY9fiFw3U6_nziORLC4";
    public static final String GEOLOCATE_PROVIDER = "GEOLOCATE";

    public GMapsGeolocationAPIWrapper(Context context) {
        this.context = context;
    }

    public void ScanForAPsAndReportPosition()
    {
        WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled())
        {
            IntentFilter wifiScanFilter = new IntentFilter();
            wifiScanFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            context.registerReceiver(mWifiScanReceiver, wifiScanFilter);
            wifiManager.startScan();
        }
        else
        {
            sendLocationStatusUpdate(GEOLOCATE_PROVIDER, "WiFi is turned off");
        }

    }

    public void UnregisterReceiver()
    {
        try
        {
            context.unregisterReceiver(mWifiScanReceiver);
        }
        catch (IllegalArgumentException e)
        {
            //  On my TC55 this is thrown
            Log.i(Constants.LOG.LOG_TAG, "Wifi Scanner receiver not registered");
        }
    }

    private final BroadcastReceiver mWifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (intent.getAction() == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
                List<ScanResult> apList = wifiManager.getScanResults();
                if (apList.size() == 0)
                {
                    //  No APs were found
                    sendLocationStatusUpdate(GEOLOCATE_PROVIDER, "No APs found");
                    return;
                }
                UnregisterReceiver();
                JSONObject params = new JSONObject();
                try
                {
                    //  Specifies whether to fall back to IP geolocation if wifi and cell tower
                    // signals are not available. Note that the IP address in the request header
                    // may not be the IP of the device. Defaults to true. Set considerIp to false
                    // to disable fall back.
                    params.put("considerIp", false);
                    JSONArray wifiAccessPoints = new JSONArray();
                    for (int i = 0; i < apList.size(); i++)
                    {
                        JSONObject macInfo = new JSONObject();
                        macInfo.put("macAddress", apList.get(i).BSSID);
                        macInfo.put("signalStrength", apList.get(i).level);
                        wifiAccessPoints.put(macInfo);
                        Log.i(Constants.LOG.LOG_TAG, "AP: " + apList.get(i).SSID + ", " + apList.get(i).level + "dBm.  " + apList.get(i).BSSID);
                        //  Not displaying found APs on UI
                        //ui.UpdateUIWithAPScanResult(apList.get(i));
                    }
                    params.put("wifiAccessPoints", wifiAccessPoints);

                } catch (JSONException e) {
                    sendLocationStatusUpdate(GEOLOCATE_PROVIDER, "Error adding found APs");
                    Log.e(Constants.LOG.LOG_TAG, "Error adding found APs to JSON object");
                    return;
                }

                String url = GEOLOCATE_URL + GEOLOCATE_KEY;

                JsonObjectRequest jsonRequest = new JsonObjectRequest
                        (Request.Method.POST, url, params, new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                try {
                                    float accuracy = Float.parseFloat(response.getString("accuracy"));
                                    Log.v(Constants.LOG.LOG_TAG, response.toString());
                                    response = response.getJSONObject("location");
                                    Double latitude = response.getDouble("lat");
                                    Double longitude = response.getDouble("lng");
                                    Location location = new Location(GEOLOCATE_PROVIDER);
                                    location.setLatitude(latitude);
                                    location.setLongitude(longitude);
                                    location.setAccuracy(accuracy);
                                    Log.i(Constants.LOG.LOG_TAG, "From Google Maps Geolocate API: Lat: " + latitude + ", Long: " + longitude + ", accuracy: " + accuracy);
                                    sendLocationUpdate(location);
                                } catch (JSONException e) {
                                    sendLocationStatusUpdate(GEOLOCATE_PROVIDER, "Error parsing response");
                                    //ui.UpdateUIWithGoogleMapsAPILocation(null);
                                    Log.e(Constants.LOG.LOG_TAG, "Error parsing response from Geolocate API: " + e.getMessage());
                                }
                            }
                        }, new Response.ErrorListener() {

                            @Override
                            public void onErrorResponse(VolleyError error) {
                                sendLocationStatusUpdate(GEOLOCATE_PROVIDER, "error: " + error.toString());
                                //ui.UpdateUIWithGoogleMapsAPILocation(null);
                                Log.e(Constants.LOG.LOG_TAG, "Error with Geolocate API request: " + error.toString());
                            }
                        });

                Volley.newRequestQueue(context).add(jsonRequest);
            }
        }
    };

    private void sendLocationUpdate(Location location)
    {
        Intent locationUpdatedIntent = new Intent(context, LocationUpdateService.class);
        locationUpdatedIntent.setAction(Constants.SERVICE_COMMS.GEOLOCATION_API_RETURNED);
        locationUpdatedIntent.putExtra("location", location);
        context.startService(locationUpdatedIntent);
    }

    private void sendLocationStatusUpdate(String locationProvider, String status)
    {
        Intent locationUpdatedIntent = new Intent(context, LocationUpdateService.class);
        locationUpdatedIntent.setAction(Constants.SERVICE_COMMS.GEOLOCATION_API_STATUS);
        locationUpdatedIntent.putExtra("location_provider", locationProvider);
        locationUpdatedIntent.putExtra("message", status);
        context.startService(locationUpdatedIntent);
    }

}
