package com.darryncampbell.locationlogger;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import java.text.DecimalFormat;

/**
 * Created by darry on 30/11/2017.
 */

public class Utilities {
    public static String formatLocationForUi(Location location)
    {
        String formattedLatitude = new DecimalFormat("#.#####").format(location.getLatitude());
        String formattedLongitude = new DecimalFormat("#.#####").format(location.getLongitude());
        String formattedAccuracy = new DecimalFormat("#.#").format(location.getAccuracy());
        //String formattedAltitude = new DecimalFormat("#.#").format(location.getAltitude());
        return "lat:" + formattedLatitude + ", long:" + formattedLongitude + ", acc:" + formattedAccuracy + "";
    }

    public static boolean isLocationEnabled(Context context)
    {
        //  The device location settings
        int locationMode = 0;
        String locationProviders;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;

        } else {
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }
}
