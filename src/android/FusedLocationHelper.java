package fr.louisbl.cordova.gpslocation;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import org.apache.cordova.CallbackContext;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;

public class FusedLocationHelper extends Activity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, ResultCallback<LocationSettingsResult>,
        LocationListener {

    protected static final int REQUEST_CHECK_SETTINGS = 0x1;
    protected static final String TAG = "fusedlocation-plugin";

    protected Activity mActivity = null;
    private CordovaGPSLocation mPlugin;

    protected GoogleApiClient mGoogleApiClient;
    protected LocationSettingsRequest mLocationSettingsRequest;
    protected LocationRequest mLocationRequest;

    private boolean showingGooglePlayServicesDialog;

    public HashMap<String, CallbackContext> watches = new HashMap<String, CallbackContext>();


    public FusedLocationHelper(Activity activity, CordovaGPSLocation plugin) {
        mActivity = activity;
        mPlugin = plugin;

        setupLocationFetching();
    }

    protected void checkForGooglePlayServices() {
        GoogleApiAvailability gServicesAvailability = GoogleApiAvailability.getInstance();
        final int status = gServicesAvailability.isGooglePlayServicesAvailable(mActivity);
        if (status != ConnectionResult.SUCCESS) {

            Dialog errorDialog = gServicesAvailability
                    .getErrorDialog(mActivity, status, 10, new DialogInterface.OnCancelListener() {

                        @Override
                        public void onCancel(DialogInterface dialog) {
                            mPlugin.fail(status, "onCancel called on ErrorDialog. ");
                        }
                    });
            if (errorDialog != null) {
                errorDialog.show();
            } else {
                mPlugin.fail(status,"checkForGooglePlayServices failed. Error code: " + status);
            }
        }
    }

    protected void setupLocationFetching() {
        checkForGooglePlayServices();
        buildGoogleApiClient();
        createLocationRequest();
        buildLocationSettingsRequest();
        connectGoogleApiClient();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(mActivity)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    protected void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    private void connectGoogleApiClient(){
        mGoogleApiClient.connect();
    }

    // region Watches

    public void addWatch(String timerId, CallbackContext callbackContext) {
        watches.put(timerId, callbackContext);

        if (watches.size() == 1) {
            scheduleLocationUpdates();
        }
    }

    public void clearWatch(String timerId) {
        if (watches.containsKey(timerId)) {
            watches.remove(timerId);
        }
        if (watches.size() == 0) {
            stopLocationUpdates();
        }
    }
    // endregion

    // region Plugin communication
    protected void fail(int code, String message) {

        if (watches.size() == 0) {
            stopLocationUpdates();
        }

        for (CallbackContext callbackContext : watches.values()) {
            mPlugin.fail(code, message, callbackContext, true);
        }
    }

    protected void win(Location loc) {

        if (watches.size() == 0) {
            stopLocationUpdates();
        }

        for (CallbackContext callbackContext : watches.values()) {
            mPlugin.win(loc, callbackContext, true);
        }
    }

    // endregion


    public void scheduleLocationUpdates(){
        if(mGoogleApiClient.isConnected()){
            LocationServices.FusedLocationApi
                    .requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    public void stopLocationUpdates(){
        watches.clear();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    public void getLastAvailableLocation() {
        Location lastLocation;
        if (mGoogleApiClient.isConnected()) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mPlugin.win(lastLocation);
        } else {
            mPlugin.fail(0,"No location available");
        }
    }

    protected void checkLocationSettings() {
        if(!showingGooglePlayServicesDialog) {
            showingGooglePlayServicesDialog = true;
            PendingResult<LocationSettingsResult> result =
                    LocationServices.SettingsApi.checkLocationSettings(
                            mGoogleApiClient,
                            mLocationSettingsRequest
                    );
            result.setResultCallback(this);
        }
    }

    @Override
    public void onResult(LocationSettingsResult locationSettingsResult) {
        final Status status = locationSettingsResult.getStatus();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                Log.i(TAG, "All location settings are satisfied.");
                getLastAvailableLocation();
                break;
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to" +
                        "upgrade location settings ");

                try {
                    // Show the dialog by calling startResolutionForResult(), and check the result
                    // in onActivityResult().
                    status.startResolutionForResult(mActivity, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException e) {
                    mPlugin.fail(0,"PendingIntent unable to execute request.");
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                mPlugin.fail(0, "Location settings are inadequate, and cannot be fixed here. "
                        + "Dialog not created.");
                break;
        }
        showingGooglePlayServicesDialog = false;
    }

    // region Google Play Services Connection Callbacks
    @Override
    public void onConnected(Bundle connectionHint) {
        checkLocationSettings();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        mPlugin.fail(result.getErrorCode(),"onConnectionFailed. Error code: " + result.getErrorCode());
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        connectGoogleApiClient();
    }

    // endregion

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "The location has been updated!");
        win(location);
    }
}
