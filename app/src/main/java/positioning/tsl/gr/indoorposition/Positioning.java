package positioning.tsl.gr.indoorposition;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.Region;
import com.google.gson.Gson;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Positioning extends Activity {

    private static final Region ALL_ESTIMOTE_BEACONS_REGION = new Region("ALL", null, null, null);
    BeaconManager beaconManager;
    WifiManager wifiManager;
    WifiManager.WifiLock wifiLock;
    Button findMeButton;
    SensorManager sensorManager;
    int wifiCount;
    int wifiTotalCount;
    int beaconCount;
    int beaconTotalCount;
    ProgressDialog ringProgressDialog;
    ArrayList<DeviceWifiMeasurements> deviceWifiMeasurements;
    ArrayList<DeviceBeaconMeasurements> deviceBeaconMeasurements;
    ArrayList<DeviceMagneticMeasurements> deviceMagneticMeasurements;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_positioning);

        findMeButton = (Button) findViewById(R.id.findMeButton);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        findMeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                ringProgressDialog = ProgressDialog.show(Positioning.this, "Please wait...", "Positioning in progress...", true);
                ringProgressDialog.setCancelable(false);
                ringProgressDialog.show();

                wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Set wifi on for measurements");
                wifiLock.acquire();

                wifiCount = 0;
                wifiTotalCount = 2;
                beaconCount = 0;
                beaconTotalCount = 2;

                deviceWifiMeasurements = new ArrayList<DeviceWifiMeasurements>();
                deviceBeaconMeasurements = new ArrayList<DeviceBeaconMeasurements>();
                deviceMagneticMeasurements = new ArrayList<DeviceMagneticMeasurements>();

                new MeasureWifi().execute();

            }
        });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_positioning, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class MeasureWifi extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {

            final WifiManager wManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wManager.startScan();
            registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {

        }

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected void onProgressUpdate(Void... values) {

        }
    }

    private BroadcastReceiver wifiReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context c, Intent intent) {

            wifiCount = wifiCount + 1;

            if (wifiCount > wifiTotalCount) {

                System.out.println("WiFi Measurement Finished!");

                sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

                CountDownTimer magneticCountDownTimer = new CountDownTimer(3000, 1000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        sensorManager.registerListener(magneticListener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL);
                    }

                    @Override
                    public void onFinish() {

                        retrieveBLEStats();

                    }
                };

                magneticCountDownTimer.start();

            } else {

                System.out.println("WiFi Measurement: " + wifiCount + "...");

                WifiManager wManager = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);

                List<ScanResult> wifiList = wManager.getScanResults();
                for (int i = 0; i < wifiList.size(); i++) {

                    DeviceWifiMeasurements deviceWifiMeasurement = new DeviceWifiMeasurements();
                    ScanResult wifi = wManager.getScanResults().get(i);

                    deviceWifiMeasurement.setSsid(wifi.SSID);
                    deviceWifiMeasurement.setRssi(String.valueOf(wifi.level));
                    deviceWifiMeasurements.add(deviceWifiMeasurement);

                    String outputInfo = "SSID: " + wifi.SSID + " " + "Level: " + wifi.level;
                    System.out.println(outputInfo);

                }
                new MeasureWifi().execute();
            }
        }
    };

    private SensorEventListener magneticListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {


            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {

                DeviceMagneticMeasurements deviceMagneticMeasurement = new DeviceMagneticMeasurements();

                deviceMagneticMeasurement.setxValue(String.valueOf(event.values[0]));
                deviceMagneticMeasurement.setyValue(String.valueOf(event.values[1]));
                deviceMagneticMeasurement.setzValue(String.valueOf(event.values[2]));

                deviceMagneticMeasurements.add(deviceMagneticMeasurement);

                Log.i("INFO", "X value of Magnetic: " + String.valueOf(event.values[0]));
                Log.i("INFO", "Y value of Magnetic: " + String.valueOf(event.values[1]));
                Log.i("INFO", "Z value of Magnetic: " + String.valueOf(event.values[2]));

                sensorManager.unregisterListener(magneticListener);
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private void retrieveBLEStats() {

        System.out.println("Measuring Beacons...");

        beaconManager = new BeaconManager(this);
        if (beaconManager.hasBluetooth() && beaconManager.isBluetoothEnabled()) {

            beaconManager.setRangingListener(new BeaconManager.RangingListener() {
                @Override
                public void onBeaconsDiscovered(Region region, final List<Beacon> beacons) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            System.out.println("Beacon Measurement: " + beaconCount);

                            if (beaconCount < beaconTotalCount) {

                                for (Beacon beacon : beacons) {

                                    DeviceBeaconMeasurements deviceBeaconMeasurement = new DeviceBeaconMeasurements();
                                    deviceBeaconMeasurement.setMinor(String.valueOf(beacon.getMinor()));
                                    deviceBeaconMeasurement.setRssi(String.valueOf(beacon.getRssi()));

                                    deviceBeaconMeasurements.add(deviceBeaconMeasurement);

                                    Log.i("INFO", "Beacon Minor: " + beacon.getMinor() + " RSSI: " + beacon.getRssi());

                                }

                                beaconCount++;

                            } else {

                                String url;
                                try {
                                    url = "http://83.212.238.209:8080/IndoorPositioningServer/monitor/positioningService";
                                    System.out.println(url);
                                    new RestClient().execute(url);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                try {
                                    Log.i("INFO", "Stopping BLE Ranging...");
                                    beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
                                } catch (RemoteException e) {
                                    Log.d("Error", "Error while stopping ranging", e);
                                }
                            }
                        }
                    });
                }
            });

            beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
                @Override
                public void onServiceReady() {
                    try {
                        beaconManager.startRanging(ALL_ESTIMOTE_BEACONS_REGION);
                    } catch (RemoteException e) {
                        Log.e("ERROR", "Cannot start ranging", e);
                    }
                }
            });
        }

    }

    class RestClient extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... uri) {

            Gson gson = new Gson();

            String wifiMeasurements = gson.toJson(deviceWifiMeasurements);
            String beaconMeasurements = gson.toJson(deviceBeaconMeasurements);
            String magneticMeasurements = gson.toJson(deviceMagneticMeasurements);

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost("http://83.212.238.209:8080/IndoorPositioningServer/monitor/positioningService");
            HttpResponse response;
            String responseString = null;
            try {

                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
                nameValuePairs.add(new BasicNameValuePair("WiFiMeasurements", wifiMeasurements));
                nameValuePairs.add(new BasicNameValuePair("BeaconMeasurements", beaconMeasurements));
                nameValuePairs.add(new BasicNameValuePair("MagneticMeasurements", magneticMeasurements));
                httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
                httppost.setHeader("Content-type", "application/x-www-form-urlencoded; charset=UTF8");
                response = httpclient.execute(httppost);
                System.out.println(response);

            } catch (ClientProtocolException e) {
                //TODO Handle problems..
            } catch (IOException e) {
                //TODO Handle problems..
            }
            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            System.out.println("INFO: " + result);
            unregisterReceiver(wifiReceiver);

            ringProgressDialog.dismiss();

            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();

            Toast.makeText(getApplicationContext(), "Positioning Finished!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroy() {

        unregisterReceiver(wifiReceiver);
        wifiLock.release();

        try {
            beaconManager.stopRanging(ALL_ESTIMOTE_BEACONS_REGION);
        } catch (RemoteException e) {
            Log.d("Error", "Error while stopping ranging", e);
        }

    }
}
