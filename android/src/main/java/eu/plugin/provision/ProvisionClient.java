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
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.provisioning.ESPDevice;
import com.espressif.provisioning.WiFiAccessPoint;
import com.espressif.provisioning.listeners.BleScanListener;
import com.espressif.provisioning.listeners.ProvisionListener;
import com.espressif.provisioning.listeners.ResponseListener;
import com.espressif.provisioning.listeners.WiFiScanListener;

import java.util.ArrayList;

import app.tauri.annotation.InvokeArg;
import app.tauri.plugin.Invoke;
import app.tauri.plugin.JSObject;

public class ProvisionClient {
    private static final String TAG = ProvisionClientPlugin.class.getSimpleName();

    private Activity activity;
    private ProvisionClientPlugin plugin;
    private final ESPProvisionManager provisionManager;
    private final ESPDevice espDevice;
    private boolean isScanning = false;

    public ProvisionClient(Activity activity, ProvisionClientPlugin plugin) {
        this.activity = activity;
        this.plugin = plugin;
        this.provisionManager = ESPProvisionManager.getInstance(activity);
        this.espDevice = provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_1);
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
        ProvisionRequest req = invoke.parseArgs(ProvisionRequest.class);

        ProvisionListener provisionListener = new ProvisionListener() {
            @Override
            public void createSessionFailed(Exception e) {
                Toast.makeText(activity, "Create session failed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void wifiConfigSent() {
                Toast.makeText(activity, "Wifi config sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void wifiConfigFailed(Exception e) {
                Toast.makeText(activity, "wifi config failed", Toast.LENGTH_SHORT).show();
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
            }
        };

        BluetoothDevice device = plugin.bluetoothDevices.get(req.address);
        provisionManager.getEspDevice().connectBLEDevice(device, req.address);
        provisionManager.getEspDevice().setProofOfPossession(req.pop);
        provisionManager.getEspDevice().initSession(new ResponseListener() {
            @Override
            public void onSuccess(byte[] returnData) {
                ArrayList<String> deviceCaps = provisionManager.getEspDevice().getDeviceCapabilities();
                if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN)) {
                    provisionManager.getEspDevice().scanNetworks(new WiFiScanListener() {
                        @Override
                        public void onWifiListReceived(ArrayList<WiFiAccessPoint> wifiList) {
                            provisionManager.getEspDevice().provision(req.ssid, req.password, provisionListener);
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
                    provisionManager.getEspDevice().provision(req.ssid, req.password, provisionListener);
                }
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(activity, "Failure of provision initalization", Toast.LENGTH_SHORT).show();
            }
        });
        invoke.resolve();
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

    private boolean checkPermissions() {
        String [] permissions = { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT };

        for (String perm: permissions ) {
            if (ActivityCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
}
