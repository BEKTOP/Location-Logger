package com.darryncampbell.locationlogger;

import android.content.Context;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Created by darry on 29/11/2017.
 */

//  Persist the currently recorded locations to a file (done everytime a location is pinned).  This
//  file is shared when requested via the 'Share' option

public class Storage {

    Boolean persistLocationRecordsToFile(Context context, ArrayList<LocationRecord> locationRecords, String fileName, Constants.OutputType outputType)
    {
        File path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS);
        File file;
        if (outputType == Constants.OutputType.CSV)
            file = new File(path, fileName + ".csv");
        else
            file = new File(path, fileName + ".gpx");
        Boolean isWriteable = isExternalStorageWritable();
        if (isWriteable)
        {
            try
            {
                path.mkdirs();
                file.createNewFile();
                if (file.exists())
                {
                    Log.i(Constants.LOG.LOG_TAG, "Log file location: " + file.getAbsolutePath());
                    String newLine = "\n";
                    OutputStream os = new FileOutputStream(file);
                    if (outputType == Constants.OutputType.CSV)
                    {
                    String deviceInformation = "Device Model: " + Build.MODEL + newLine +
                            "Build Number: " + Build.ID + newLine + "Serial Number: " + Build.SERIAL + newLine;
                    os.write(deviceInformation.getBytes());
                        os.write(CSVHeaderRow().getBytes());
                    os.write(newLine.getBytes());
                    for (int i = 0; i < locationRecords.size(); i++)
                    {
                        os.write(locationRecords.get(i).CSVRow().getBytes());
                        os.write(newLine.getBytes());
                        }
                    }
                    else
                    {
                        String gpx = GPXHeader("Location Logger.  Device Model: " + Build.MODEL +
                                ", Build Number: " + Build.ID + ", Serial Number: " + Build.SERIAL);
                        gpx += GPXTrackStart("GPS Location Positions");
                        for (int i = 0; i < locationRecords.size(); i++)
                        {
                            gpx += locationRecords.get(i).convertLocationToGPXString(LocationManager.GPS_PROVIDER);
                        }
                        gpx += GPXTrackEnd();
                        gpx += GPXTrackStart("Network Location Positions");
                        for (int i = 0; i < locationRecords.size(); i++)
                        {
                            gpx += locationRecords.get(i).convertLocationToGPXString(LocationManager.NETWORK_PROVIDER);
                        }
                        gpx += GPXTrackEnd();
                        gpx += GPXTrackStart("Fused Location Positions");
                        for (int i = 0; i < locationRecords.size(); i++)
                        {
                            gpx += locationRecords.get(i).convertLocationToGPXString(LocationServicesWrapper.FUSED_PROVIDER);
                        }
                        gpx += GPXTrackEnd();
                        gpx += GPXTrackStart("Geolocate Location Positions");
                        for (int i = 0; i < locationRecords.size(); i++)
                        {
                            gpx += locationRecords.get(i).convertLocationToGPXString(GMapsGeolocationAPIWrapper.GEOLOCATE_PROVIDER);
                        }
                        gpx += GPXTrackEnd();
                        gpx += GPXFooter();
                        os.write(gpx.getBytes());
                    }
                    os.close();

                    // Tell the media scanner about the new file so that it is
                    // immediately available to the user.
                    MediaScannerConnection.scanFile(context,
                            new String[] { file.toString() }, null,
                            new MediaScannerConnection.OnScanCompletedListener() {
                                public void onScanCompleted(String path, Uri uri) {
                                    Log.i("ExternalStorage", "Scanned " + path + ":");
                                    Log.i("ExternalStorage", "-> uri=" + uri);
                                }
                            });
                }
                else
                {
                    Log.e(Constants.LOG.LOG_TAG, "Error creating log file");
                    Toast.makeText(context, "Error creating log file", Toast.LENGTH_LONG).show();
                    return false;
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Log.e(Constants.LOG.LOG_TAG, "Error writing " + file, e);
                Toast.makeText(context, "Error writing log file", Toast.LENGTH_LONG).show();
                return false;
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(Constants.LOG.LOG_TAG, "Error writing " + file, e);
                Toast.makeText(context, "Error writing log file", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        else
        {
            Log.e(Constants.LOG.LOG_TAG, "External Storage was not writeable, could not create log");
            Toast.makeText(context, "External storage is not writeable, could not create log", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    public static String CSVHeaderRow()
    {
        return "Date," +
                "Time," +
                "GPS Latitude," +
                "GPS Longitude," +
                "GPS Altitude," +
                "GPS Accuracy," +
                "GPS Age (ms)," +
                "Network Latitude," +
                "Network Longitude," +
                "Network Height," +
                "Network Accuracy," +
                "Network Age (ms)," +
                "Fused Latitude," +
                "Fused Longitude," +
                "Fused Altitude," +
                "Fused Accuracy," +
                "Fused Age (ms)," +
                "AP Based Latitude," +
                "AP Based Longitude," +
                "AP Based Altitude," +
                "AP Based Accuracy," +
                "AP Based Age (ms)," +
                "Notes,";
    }
    public static String GPXHeader(String metaData)
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n" +
                "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd \">\n" +
                "  <metadata>\n  " + metaData + "\n  </metadata>\n";
    }
    public static String GPXTrackStart(String name)
    {
        return "  <trk>\n" + "    <name>" + name + "</name>\n" + "    <trkseg>\n";
    }
    public static String GPXTrackEnd()
    {
        return "    </trkseg>\n" + "  </trk>\n";
    }
    public static String GPXFooter()
    {
        return  "</gpx>\n";
    }
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }
}
