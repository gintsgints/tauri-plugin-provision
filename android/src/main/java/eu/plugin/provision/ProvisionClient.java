package eu.plugin.provision;

import static android.content.Context.MODE_PRIVATE;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.WiFiAccessPoint;
import com.espressif.provisioning.listeners.BleScanListener;
import com.espressif.provisioning.listeners.ProvisionListener;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.provisioning.listeners.WiFiScanListener;
import com.espressif.provisioning.DeviceConnectionEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import app.tauri.annotation.InvokeArg;
import app.tauri.plugin.Invoke;
import app.tauri.plugin.JSObject;


public class ProvisionClient {
    private static final String TAG = ProvisionClientPlugin.class.getSimpleName();
    // Time out
    private static final long DEVICE_CONNECT_TIMEOUT = 20000;

    private Activity activity;
    private ProvisionClientPlugin plugin;
    private final ESPProvisionManager provisionManager;
    private final ESPDevice espDevice;
    private boolean isScanning = false;
    private ResponseListener listener;
    private Handler handler;
    private ProvisionRequest req;

    public ProvisionClient(Activity activity, ProvisionClientPlugin plugin) {
        this.handler = new Handler();
        this.activity = activity;
        this.plugin = plugin;
        this.provisionManager = ESPProvisionManager.getInstance(activity);
        this.espDevice = provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1);
        EventBus.getDefault().register(this);
    }

    @InvokeArg
    public static class ProvisionRequest {
        public String address;
        public String pop;
        public String ssid;
        public String password;

        public ProvisionRequest() {}
    }
    public void wifiProvision(Invoke invoke) {
        req = invoke.parseArgs(ProvisionRequest.class);
        BluetoothDevice device = plugin.bluetoothDevices.get(req.address);
        this.espDevice.connectBLEDevice(device, req.address);
        handler.postDelayed(disconnectDeviceTask, DEVICE_CONNECT_TIMEOUT);
        invoke.resolve();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(DeviceConnectionEvent event) {
        String protoVerStr = espDevice.getVersionInfo();
        try {
            JSONObject jsonObject = new JSONObject(protoVerStr);
            JSONObject provInfo = jsonObject.getJSONObject("prov");
            if (provInfo.has("sec_ver")) {
                int serVer = provInfo.optInt("sec_ver");
                if (serVer == 1) {
                    espDevice.setSecurityType(ESPConstants.SecurityType.SECURITY_1);
                }
            }
        } catch (JSONException e) {
            Toast.makeText(activity, "Capabilities JSON not available.", Toast.LENGTH_SHORT).show();
        }

        this.espDevice.setProofOfPossession(req.pop);

        listener = new ResponseListener() {
            @Override
            public void onSuccess(byte[] returnData) {
                ArrayList<String> deviceCaps = espDevice.getDeviceCapabilities();
                if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN)) {
                    espDevice.scanNetworks(new WiFiScanListener() {
                        @Override
                        public void onWifiListReceived(ArrayList<WiFiAccessPoint> wifiList) {
                            espDevice.provision(req.ssid, req.password, null);
                        }

                        @Override
                        public void onWiFiScanFailed(Exception e) {
                            Toast.makeText(activity, "Failure of scanning networks", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN)) {
                    Toast.makeText(activity, "Thread scan not implemented", Toast.LENGTH_SHORT).show();
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_PROV)) {
                    Toast.makeText(activity, "Thread provisioning not implemented", Toast.LENGTH_SHORT).show();
                } else {
                    espDevice.provision(req.ssid, req.password, new ProvisionListener() {
                        @Override
                        public void createSessionFailed(Exception e) {
                            Toast.makeText(activity, "Create session failed", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void wifiConfigSent() {
                        }

                        @Override
                        public void wifiConfigFailed(Exception e) {
                        }

                        @Override
                        public void wifiConfigApplied() {
                            Toast.makeText(activity, "Wifi config applied", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void wifiConfigApplyFailed(Exception e) {
                            Toast.makeText(activity, "Device provisioning failed apply", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void provisioningFailedFromDevice(ESPConstants.ProvisionFailureReason failureReason) {
                            Toast.makeText(activity, "Provisioning failed from device", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void deviceProvisioningSuccess() {
                            Toast.makeText(activity, "Device provisioning success", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onProvisioningFailed(Exception e) {
                            Toast.makeText(activity, "Device provisioning failed with exception", Toast.LENGTH_SHORT).show();
                            throw new RuntimeException("Device provisioning failed with exception", e);
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(activity, "Failure of provision initalization: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        this.espDevice.initSession(listener);
    }

    public void startScan(Invoke invoke, ProvisionClientPlugin.OnDevice onDevice) {
        if (isScanning) {
            invoke.reject("Scan already running");
            return;
        }
        if (!checkPermissions()){
            invoke.reject("Missing permissions");
            return;
        }

        isScanning = true;
        plugin.bluetoothDevices.clear();

        BleScanListener bleScanListener = new BleScanListener() {

            @Override
            public void scanStartFailed() {
                JSObject no_device = new JSObject();
                no_device.put("error", "Scan failed. Make sure bluetooth is enabled");
                onDevice.onDevice(no_device);
            }

            @Override
            public void onPeripheralFound(BluetoothDevice device, ScanResult scanResult) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "====== onPeripheralFound ===== " + device.getName());
                    }
                } else {
                    Log.d(TAG, "====== onPeripheralFound ===== " + device.getName());
                }

                boolean deviceExists = false;
                String serviceUuid = "";

                if (scanResult.getScanRecord().getServiceUuids() != null && scanResult.getScanRecord().getServiceUuids().size() > 0) {
                    serviceUuid = scanResult.getScanRecord().getServiceUuids().get(0).toString();
                }
                Log.d(TAG, "Add service UUID : " + serviceUuid);

                if (plugin.bluetoothDevices.containsKey(serviceUuid)) {
                    deviceExists = true;
                }

                if (!deviceExists) {
                    Toast.makeText(activity, "Peripheral Found", Toast.LENGTH_LONG).show();
                    plugin.bluetoothDevices.put(serviceUuid, device);
                    plugin.foundDevice = new JSObject();
                    plugin.foundDevice.put("name", device.getName());
                    plugin.foundDevice.put("address", serviceUuid);
                    plugin.foundDevice.put("error", "");
                }
            }

            @Override
            public void scanCompleted() {
                Toast.makeText(activity, "Scan is completed", Toast.LENGTH_SHORT).show();
                isScanning = false;
                if (plugin.foundDevice != null) {
                    onDevice.onDevice(plugin.foundDevice);
                } else {
                    JSObject no_device = new JSObject();
                    no_device.put("error", "No device found");
                    onDevice.onDevice(no_device);
                }
            }

            @Override
            public void onFailure(Exception e) {
                JSObject no_device = new JSObject();
                no_device.put("error", "Failure scanning. Error:" + e.getMessage());
                onDevice.onDevice(no_device);
            }
        };

        provisionManager.searchBleEspDevices("PROV_", bleScanListener);
        invoke.resolve();
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private boolean checkPermissions() {
        String [] permissions = {
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADMIN,
//                Manifest.permission.ACCESS_FINE_LOCATION,
//                ,
//                Manifest.permission.ACCESS_WIFI_STATE,
//                Manifest.permission.CHANGE_WIFI_STATE,
        };

        for (String perm: permissions ) {
            if (ActivityCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
                if (firstPermissionRequest(perm) || activity.shouldShowRequestPermissionRationale(perm)) {
                    // this will open the permission dialog
                    markFirstPermissionRequest(perm);
                    activity.requestPermissions(permissions, 1);
                    return false;
                } else{
                    // this will open settings which asks for permission
                    Intent intent = new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${activity.packageName}")
                    );
                    activity.startActivity(intent);
                    Toast.makeText(activity, "Allow Permission: $perm", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        }
        return true;
    }

    private void markFirstPermissionRequest(String perm) {
        SharedPreferences sharedPreferences =
                activity.getSharedPreferences("PREFS_PERMISSION_FIRST_TIME_ASKING", MODE_PRIVATE);
        sharedPreferences.edit().putBoolean(perm, false).apply();
    }

    private boolean firstPermissionRequest(String perm) {
        return activity.getSharedPreferences(perm, MODE_PRIVATE).getBoolean(perm, true);
    }

    private Runnable disconnectDeviceTask = new Runnable() {
        @Override
        public void run() {
            Log.e(TAG, "Disconnect device");
        }
    };
}
