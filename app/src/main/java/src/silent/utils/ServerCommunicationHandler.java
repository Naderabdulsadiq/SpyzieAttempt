package src.silent.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by all3x on 2/22/2018.
 */

public class ServerCommunicationHandler {
    public static void executeRegisterPost(final Context context, final String urlString,
                                           final String[] payload, final String username) {
        @SuppressLint("StaticFieldLeak") AsyncTask<Void, Void, Void> networkTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(urlString);
                    connection = (HttpURLConnection) url.openConnection();
                    //connection.setReadTimeout(10000);
                    //connection.setConnectTimeout(15000);
                    connection.setRequestMethod("POST");
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json");

                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("Username", Base64.encodeToString(username.getBytes(),
                            Base64.URL_SAFE));
                    jsonObject.put("IMEI", Base64.encodeToString(payload[0].getBytes(),
                            Base64.URL_SAFE));
                    jsonObject.put("Manufacturer", Base64.encodeToString(payload[1].getBytes(),
                            Base64.URL_SAFE));
                    jsonObject.put("Model", Base64.encodeToString(payload[2].getBytes(),
                            Base64.URL_SAFE));

                    DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                    outputStream.writeBytes(jsonObject.toString());
                    outputStream.flush();
                    outputStream.close();

                    BufferedReader input = new BufferedReader(new InputStreamReader(connection.
                            getInputStream()));
                    String response = "";
                    for (String line; (line = input.readLine()) != null; response += line) ;
                    response = response.replace("\"", "");
                    response = new String(Base64.decode(response, Base64.URL_SAFE), "UTF-8");

                    if (!response.equals("Already registered")) {
                        if (!FileHandler.fileExist(context, "SecurityToken.enc")) {
                            FileHandler.createFile(context, "SecurityToken.enc");
                            FileHandler.writeFile(context, "SecurityToken.enc", response);
                        } else {
                            FileHandler.writeFile(context, "SecurityToken.enc", response);
                        }
                    }

                } catch (Exception ex) {
                    Log.d("MARK", ex.getMessage());
                } finally {
                    connection.disconnect();
                }
                return null;
            }
        };
        networkTask.execute();
    }

    public static void executeDataPost(String urlString, JSONObject bulkData) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            //connection.setReadTimeout(10000);
            //connection.setConnectTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(bulkData.toString());
            outputStream.flush();
            outputStream.close();

            int responseCode = connection.getResponseCode();
        } catch (Exception ex) {

        } finally {
            connection.disconnect();
        }
    }

    public static String getMask(Context context, String urlString, String IMEI) {
        if (!FileHandler.fileExist(context, "SecurityToken.enc")) {
            return null;
        }
        String secToken = FileHandler.readFile(context, "SecurityToken.enc");

        String response = "";
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            //connection.setReadTimeout(10000);
            //connection.setConnectTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JSONObject credentials = new JSONObject();
            credentials.put("IMEI", Base64.encodeToString(IMEI.getBytes(), Base64.URL_SAFE));
            credentials.put("SecToken", Base64.encodeToString(secToken.getBytes(), Base64.URL_SAFE));

            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes(credentials.toString());
            outputStream.flush();
            outputStream.close();

            BufferedReader input = new BufferedReader(new InputStreamReader(connection.
                    getInputStream()));
            for (String line; (line = input.readLine()) != null; response += line) ;
            response = response.replace("\"", "");
            response = new String(Base64.decode(response, Base64.URL_SAFE), "UTF-8");
        } catch (Exception ex) {

        } finally {
            connection.disconnect();
        }

        return response;
    }
}