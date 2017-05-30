/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
package fr.louisbl.cordova.gpslocation;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class CordovaGPSLocation extends CordovaPlugin {

    private LocationManager mLocationManager;
    private FusedLocationHelper mFusedLocationHelper;
    private CordovaLocationListener mCordovaLocationListener;
    private CallbackContext gpsCallbackContext = null;

    String TAG = "CordovaGPSLocation";
    String [] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION };
    CallbackContext context;

    LocationManager getLocationManager() {
        return mLocationManager;
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        mLocationManager = (LocationManager) cordova.getActivity().getSystemService(Context.LOCATION_SERVICE);
        mFusedLocationHelper = new FusedLocationHelper(cordova.getActivity(), this);
        registerGpsProviderChanges(cordova.getActivity());
        cordova.setActivityResultCallback(this);
    }

    private BroadcastReceiver broadcastGpsChanges = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (gpsCallbackContext != null) {
                checkGPSStatus();
            }
        }
    };

    private void registerGpsProviderChanges(Context context) {
        context.registerReceiver(broadcastGpsChanges, new IntentFilter("android.location.PROVIDERS_CHANGED"));
    }

    private void unregisterGpsProviderChanges(Context context) {
        try {
            context.unregisterReceiver(broadcastGpsChanges);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action
     *            The action to execute.
     * @param args
     *            JSONArry of arguments for the plugin.
     * @param callbackContext
     *            The callback id used when calling back into JavaScript.
     * @return True if the action was valid, or false if not.
     * @throws JSONException
     */
    public boolean execute(final String action, final JSONArray args,
            final CallbackContext callbackContext) {
        context = callbackContext;

        if (action == null || !action.matches("getPermission|getLocation|addWatch|clearWatch|requestPermissions|addGPSWatch")) {
            return false;
        }

        if(!hasPermisssion()) {
            PermissionHelper.requestPermissions(this, 0, permissions);
            return true;
        }

        if (action.equals("requestPermissions")) {
            mFusedLocationHelper.checkLocationSettings();
        }

        final String id = args.optString(0, LocationUtils.EMPTY_STRING);

        if (action.equals("clearWatch")) {
            clearWatch(id);
            return true;
        }

        if (action.equals("getLocation")) {
            getLastLocation();
        } else if (action.equals("addWatch")) {
            addWatch(id, callbackContext);
        } else if (action.equals("addGPSWatch")) {
            addGPSWatch(callbackContext);
        }

        return true;
    }

    /**
     * Called when the activity is to be shut down. Stop listener.
     */
    public void onDestroy() {
        mFusedLocationHelper.stopLocationUpdates();
        unregisterGpsProviderChanges(cordova.getActivity());
    }

    /**
     * Called when the view navigates. Stop the listeners.
     */
    public void onReset() {
        this.onDestroy();
    }


    public void win(Location loc, CallbackContext callbackContext,
            boolean keepCallback) {
        if (callbackContext == null) {
            return;
        }
        PluginResult result = new PluginResult(PluginResult.Status.OK,
                LocationUtils.returnLocationJSON(loc));
        result.setKeepCallback(keepCallback);
        callbackContext.sendPluginResult(result);
    }

    public void win(Location loc){
        win(loc, context, false);
    }

    /**
     * Location failed. Send error back to JavaScript.
     *
     * @param code
     *            The error code
     * @param msg
     *            The error message
     * @throws JSONException
     */
    public void fail(int code, String msg, CallbackContext callbackContext,
            boolean keepCallback) {
        if (callbackContext == null) {
            return;
        }
        JSONObject obj = new JSONObject();
        String backup = null;
        try {
            obj.put("code", code);
            obj.put("message", msg);
        } catch (JSONException e) {
            obj = null;
            backup = "{'code':" + code + ",'message':'"
                    + msg.replaceAll("'", "\'") + "'}";
        }
        PluginResult result;
        if (obj != null) {
            result = new PluginResult(PluginResult.Status.ERROR, obj);
        } else {
            result = new PluginResult(PluginResult.Status.ERROR, backup);
        }

        result.setKeepCallback(keepCallback);
        callbackContext.sendPluginResult(result);
    }

    public void fail(int code, String msg){
        fail(code, msg, context, false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.i(TAG, "onActivityResult called with reqestCode " + requestCode + " and resultCode "
                + resultCode);
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case FusedLocationHelper.REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.i(TAG, "User agreed to make required location settings changes.");
                        getLastLocation();
                        break;
                    case Activity.RESULT_CANCELED:
                        fail(Activity.RESULT_CANCELED,
                                "User chose not to make required location settings changes.",
                                context, false);
                        break;
                }
                break;
        }
    }


    private void getLastLocation() {
        mFusedLocationHelper.getLastAvailableLocation(context);
    }

    private void clearWatch(String id) {
        mFusedLocationHelper.clearWatch(id);
    }

    private void addWatch(String timerId, CallbackContext callbackContext) {
        mFusedLocationHelper.addWatch(timerId, callbackContext);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        PluginResult result;
        //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
        if(context != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    LOG.d(TAG, "Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
                    context.sendPluginResult(result);
                    return;
                }

            }
            result = new PluginResult(PluginResult.Status.OK);
            context.sendPluginResult(result);
        }
    }

    public boolean hasPermisssion() {
        for(String p : permissions)
        {
            if(!PermissionHelper.hasPermission(this, p))
            {
                return false;
            }
        }
        return true;
    }

    /*
     * We override this so that we can access the permissions variable, which no longer exists in
     * the parent class, since we can't initialize it reliably in the constructor!
     */

    public void requestPermissions(int requestCode)
    {
        PermissionHelper.requestPermissions(this, requestCode, permissions);
    }

    public void addGPSWatch(CallbackContext callbackContext) {
        addGPSCallback(callbackContext);
        checkGPSStatus();
    }

    private void addGPSCallback(CallbackContext callbackContext) {
        gpsCallbackContext = callbackContext;
    }

    private void checkGPSStatus() {
        PluginResult result;
        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            result = new PluginResult(PluginResult.Status.OK);
        } else {
            result = new PluginResult(PluginResult.Status.ERROR);
        }
        result.setKeepCallback(true);
        gpsCallbackContext.sendPluginResult(result);
    }
}
