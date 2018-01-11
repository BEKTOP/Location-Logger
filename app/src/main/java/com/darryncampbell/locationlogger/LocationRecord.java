package com.darryncampbell.locationlogger;

import android.annotation.TargetApi;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;

//  Class to define an entry in the location record array and UI for the positions a device has been
public class LocationRecord implements Parcelable {
    private Location mGpsLocation;
    private Location mNetworkLocation;
    private Location mFusedLocation;
    private Location mMapsLocation;
    private String mMapsLocationLastError = "Updated when Position Pinned";
    private long mGpsAge;
    private long mNetworkAge;
    private long mFusedAge;
    private long mMapsAge;
    private Date mUpdateTime;
    private String mNote;

    public LocationRecord(Location gpsLocation, Location networkLocation, Location fusedLocation, Location mapsLocation, String lastApBasedLocationError) {
        //  Any of the locations provided can be null;
        mGpsLocation = gpsLocation;
        mNetworkLocation = networkLocation;
        mFusedLocation = fusedLocation;
        mMapsLocation = mapsLocation;
        mMapsLocationLastError = lastApBasedLocationError;

        Calendar c = Calendar.getInstance();
        mUpdateTime = c.getTime();
        mGpsAge = age_ms(mGpsLocation);
        mNetworkAge = age_ms(mNetworkLocation);
        mFusedAge = age_ms(mFusedLocation);
        mMapsAge = 0;
    }

    protected LocationRecord(Parcel in) {
        mGpsLocation = (Location) in.readValue(Location.class.getClassLoader());
        mNetworkLocation = (Location) in.readValue(Location.class.getClassLoader());
        mFusedLocation = (Location) in.readValue(Location.class.getClassLoader());
        mMapsLocation = (Location) in.readValue(Location.class.getClassLoader());
        mMapsLocationLastError = in.readString();
        mGpsAge = in.readLong();
        mNetworkAge = in.readLong();
        mFusedAge = in.readLong();
        mMapsAge = in.readLong();
        long tmpMUpdateTime = in.readLong();
        mUpdateTime = tmpMUpdateTime != -1 ? new Date(tmpMUpdateTime) : null;
        mNote = in.readString();
    }

    public void setNote(String newNote) {
        this.mNote = newNote;
    }
    public String getNote() {
        if (this.mNote == null)
            return "";
        else
            return this.mNote;
    }

    public String getGpsLocation() {
        if (mGpsLocation == null) {
            return "Unavailable";
        } else {
            return convertLocationToUiString(mGpsLocation);
        }
    }

    public String getNetworkLocation() {
        if (mNetworkLocation == null) {
            return "Unavailable";
        } else {
            return convertLocationToUiString(mNetworkLocation);
        }
    }

    public String getFusedLocation() {
        if (mFusedLocation == null) {
            return "Unavailable";
        } else {
            return convertLocationToUiString(mFusedLocation);
        }
    }

    public String getAPBasedLocation() {
        if (mMapsLocation == null) {
            return mMapsLocationLastError;
        } else {
            return convertLocationToUiString(mMapsLocation);
        }
    }

    public String getTimestamp() {
        if (mUpdateTime == null) {
            return "Unavailable";
        } else {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return df.format(mUpdateTime);
        }
    }
    public String getTimestampZulu()
    {
        if (mUpdateTime == null)
            return "";
        else
        {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            return df.format(mUpdateTime);
        }
    }

    private String convertLocationToUiString(Location location)
    {
        String formattedLatitude = new DecimalFormat("#.######").format(location.getLatitude());
        String formattedLongitude = new DecimalFormat("#.######").format(location.getLongitude());
        String formattedAccuracy = new DecimalFormat("#.#").format(location.getAccuracy());
        String formattedAltitude = new DecimalFormat("#.#").format(location.getAltitude());
        return "lat:" + formattedLatitude + ", long:" + formattedLongitude + ", acc:" + formattedAccuracy;
    }

    private String convertLocationToCSVString(Location location, String textWhenLocationIsNull)
    {
        if (location == null)
        {
            return textWhenLocationIsNull + "," + textWhenLocationIsNull + "," +
                    textWhenLocationIsNull + "," + textWhenLocationIsNull + ",";
        }
        else
        {
            String formattedLatitude = new DecimalFormat("#.#########").format(location.getLatitude());
            String formattedLongitude = new DecimalFormat("#.#########").format(location.getLongitude());
            String formattedAccuracy = new DecimalFormat("#.###").format(location.getAccuracy());
            String formattedAltitude = new DecimalFormat("#.###").format(location.getAltitude());
            if (location.getProvider().equals(GMapsGeolocationAPIWrapper.GEOLOCATE_PROVIDER))
                formattedAltitude = "unavailable";  //  not returned from the GMaps Geolocation API
            return formattedLatitude + "," + formattedLongitude + "," + formattedAltitude + "," +
                    formattedAccuracy + ",";
        }
    }


    public String CSVRow()
    {
        DateFormat dfDate = new SimpleDateFormat("yyyy-MM-dd");
        DateFormat dfTime = new SimpleDateFormat("HH:mm:ss");
        String date = dfDate.format(mUpdateTime);
        String time = dfTime.format(mUpdateTime);
        String row =  date + "," + time + "," + convertLocationToCSVString(mGpsLocation, "unavailable") + mGpsAge + "," +
                convertLocationToCSVString(mNetworkLocation, "unvailable") + mNetworkAge + "," +
                convertLocationToCSVString(mFusedLocation, "unavailable") + mFusedAge + "," +
                convertLocationToCSVString(mMapsLocation, mMapsLocationLastError) + mMapsAge + "," +
                "'" + getNote() + "'";
        return row;
    }

    public String convertLocationToGPXString(String locationProvider){
        Location location = null;
        if (locationProvider.equals(LocationManager.GPS_PROVIDER))
            location = mGpsLocation;
        else if (locationProvider.equals(LocationManager.NETWORK_PROVIDER))
            location = mNetworkLocation;
        else if (locationProvider.equals(LocationServicesWrapper.FUSED_PROVIDER))
            location = mFusedLocation;
        else if (locationProvider.equals(GMapsGeolocationAPIWrapper.GEOLOCATE_PROVIDER))
            location = mMapsLocation;
        String newLine = "\n";
        if (location == null)
    {
        //  The header row for the CSV output
            return "";
        }
        else
        {
            String trackSegment = "      <trkpt lat=\"" + location.getLatitude() + "\" lon=\"" + location.getLongitude() + "\">" + newLine;
            trackSegment += "        <ele>" + location.getAltitude() + "</ele>" + newLine;
            trackSegment += "        <time>" + getTimestampZulu() + "</time>" + newLine;
            trackSegment += "        <cmt>" + getNote() + "</cmt>" + newLine;
            trackSegment += "      </trkpt>" + newLine;
            return trackSegment;
        }
    }

    //  Per StackOverflow https://stackoverflow.com/questions/15308326/how-long-ago-was-the-last-known-location-recorded
    public long age_ms(Location last) {
        if (last == null)
            return 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            return age_ms_api_17(last);
        return age_ms_api_pre_17(last);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private long age_ms_api_17(Location last) {
        return (SystemClock.elapsedRealtimeNanos() - last
                .getElapsedRealtimeNanos()) / 1000000;
    }

    private long age_ms_api_pre_17(Location last) {
        return System.currentTimeMillis() - last.getTime();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int i) {
        dest.writeValue(mGpsLocation);
        dest.writeValue(mNetworkLocation);
        dest.writeValue(mFusedLocation);
        dest.writeValue(mMapsLocation);
        dest.writeString(mMapsLocationLastError);
        dest.writeLong(mGpsAge);
        dest.writeLong(mNetworkAge);
        dest.writeLong(mFusedAge);
        dest.writeLong(mMapsAge);
        dest.writeLong(mUpdateTime != null ? mUpdateTime.getTime() : -1L);
        dest.writeString(mNote);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<LocationRecord> CREATOR = new Parcelable.Creator<LocationRecord>() {
        @Override
        public LocationRecord createFromParcel(Parcel in) {
            return new LocationRecord(in);
        }

        @Override
        public LocationRecord[] newArray(int size) {
            return new LocationRecord[size];
        }
    };
}
