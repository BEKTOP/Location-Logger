package com.darryncampbell.locationlogger;

/**
 * Created by darry on 30/11/2017.
 */

public class Constants {
    //  Constants sent to the Location Update Service
    public interface PERMISSION {
        int PERMISSION_REQUEST = 1;
        int NUM_PERMISSIONS_REQUESTED = 2;
    }

    public interface ACTION {
        //  Messages sent from the main activity to the service
        String START_LOCATION_UPDATE_SERVCIE = "com.darryncampbell.locationupdateservice.action.start";
        String STOP_LOCATION_UPDATE_SERVICE = "com.darryncampbell.locationupdateservice.action.stop";
        String MAIN_ACTION = "com.darryncampbell.locationupdateservice.action.main";
        String RECORD_LOCATION = "com.darryncampbell.locationupdateservice.action.record_location";
        String FILENAME_CHANGED = "com.darryncampbell.locationupdateservice.service.filename_changed";
        String FILETYPE_CHANGED = "com.darryncampbell.locationupdateservice.service.filetype_changed";
        String UPDATE_RECORD_NOTE = "com.darryncampbell.locationupdateservice.service.update_record";
        String POLL_INTERVAL_CHANGED = "com.darryncampbell.locationupdateservice.action.poll_interval_changed";
        String CLEAR_PINNED_LOCATIONS = "com.darryncampbell.locationupdateservice.action.clear_pinned_locations";
        String DELETE_RECORD = "com.darryncampbell.locationupdateservice.action.delete_record";
        String REQUEST_LATEST_DATA = "com.darryncampbell.locationupdateservice.service.request_latest_data";
    }

    public interface SERVICE_COMMS
    {
        //  Messages received from the service, sent to the UI or sent by the location providers to notify the service of something
        String LOCATION_RECORD_LIST = "com.darryncampbell.locationupdateservice.service.record_list";
        String LOCATION_UPDATED = "com.darryncampbell.locationupdateservice.service.location_updated";
        String LOCATION_STATUS_UPDATED = "com.darryncampbell.locationupdateservice.service.location_status_updated";
        String LOCATION_POLL_INTERVAL = "com.darryncampbell.locationupdateservice.service.location_poll_interval";
        String FILENAME = "com.darryncampbell.locationupdateservice.service.filename";
        String FILETYPE = "com.darryncampbell.locationupdateservice.service.filetype";
        String GEOLOCATION_API_RETURNED = "com.darryncampbell.locationupdateservice.service.geolocation_api_returned";
        String GEOLOCATION_API_STATUS = "com.darryncampbell.locationupdateservice.service.geolocation_api_status";
    }

    public interface NOTIFICATION_ID {
        int LOCATION_UPDATE_SERVICE = 1;
        String LOCATION_UPDATE_CHANNEL_ID = "com.darryncampbell.locationupdateservice.channel.general";
        String LOCATION_UPDATE_CHANNEL = "location channel";
    }

    public interface LOG
    {
        String LOG_TAG = "LocationLog";
    }

    public enum OutputType {
        CSV, GPX
    }
}
