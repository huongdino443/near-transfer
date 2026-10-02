package com.nearbyshare.legacy;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

final class SavedDeviceStore {
    private static final String PREFERENCES = "near_transfer";
    private static final String KEY_DEVICES = "saved_devices";

    private SavedDeviceStore() {
    }

    static ArrayList<SavedDevice> load(Context context) throws JSONException {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        JSONArray json = new JSONArray(preferences.getString(KEY_DEVICES, "[]"));
        ArrayList<SavedDevice> devices = new ArrayList<SavedDevice>();
        for (int i = 0; i < json.length(); i++) {
            JSONObject item = json.getJSONObject(i);
            String name = item.getString("name").trim();
            String address = item.getString("address").trim();
            int port = item.getInt("port");
            if (name.length() == 0 || !isIpv4Address(address) ||
                    port < 1 || port > 65535) {
                throw new JSONException("Invalid saved device at index " + i);
            }
            devices.add(new SavedDevice(name, address, port));
        }
        return devices;
    }

    static boolean save(Context context, ArrayList<SavedDevice> devices)
            throws JSONException {
        JSONArray json = new JSONArray();
        for (SavedDevice device : devices) {
            JSONObject item = new JSONObject();
            item.put("name", device.name);
            item.put("address", device.address);
            item.put("port", device.port);
            json.put(item);
        }
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        return preferences.edit().putString(KEY_DEVICES, json.toString()).commit();
    }

    private static boolean isIpv4Address(String address) {
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.length() == 0 || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (part.charAt(i) < '0' || part.charAt(i) > '9') {
                    return false;
                }
            }
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}