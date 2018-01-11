# Location Logger
This application is designed primarily to test the efficacy of the different location providers available on your Android device, **it is NOT intended to be used to track your location during hiking / biking or similar use cases**.  

## Location APIs
The device's current location is tracked through all active available providers (gps, network, fused) as well as Google's [Geolocation API](https://developers.google.com/maps/documentation/geolocation/intro).  Current location must be logged manually either through the activity UI or the notification.  

## Logs
Logs can be associated with notes (e.g. 'I am by the West stairs') to later determine how accurate the location was.  Logs are saved in either CSV or GPX format.  You can extract the logs from the device either via the 'Share' option or copying from the device's public Documents folder.

## App Key Limits
There are limits associated with the key used in this app to call the Geolocate API which is kept in plan text.  This is for simplicity for anybody wishing to try this app out in a limited sense but if you want to use this application extensively then please replace this with your own key for maximum reliability.

## Screenshots
![Main app](https://raw.githubusercontent.com/darryncampbell/Location-Logger/master/screenshots/activity.png)
![Main app](https://raw.githubusercontent.com/darryncampbell/Location-Logger/master/screenshots/notification.png)

