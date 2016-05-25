/*
 * Copyright (C) 2015 louisbl
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.louisbl.cordova.gpslocation;

import org.json.JSONException;
import org.json.JSONObject;

import android.location.Location;

/**
 * Defines app-wide constants and utilities
 */
public final class LocationUtils {

    public static int PERMISSION_DENIED = 1;
    public static int POSITION_UNAVAILABLE = 2;
    public static int TIMEOUT = 3;
    public static int RESOLUTION_REQUIRED = 4;

    // Debugging tag for the application
    public static final String APPTAG = "CDVGPSlocationPlugin";

    // Create an empty string for initializing strings
    public static final String EMPTY_STRING = "";


    public static JSONObject returnLocationJSON(Location loc) {
        JSONObject o = new JSONObject();

        try {
            o.put("latitude", loc.getLatitude());
            o.put("longitude", loc.getLongitude());
            o.put("altitude", (loc.hasAltitude() ? loc.getAltitude() : null));
            o.put("accuracy", loc.getAccuracy());
            o.put("heading",
                    (loc.hasBearing() ? (loc.hasSpeed() ? loc.getBearing()
                            : null) : null));
            o.put("velocity", loc.getSpeed());
            o.put("timestamp", loc.getTime());
            o.put("provider", loc.getProvider());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return o;
    }

}
