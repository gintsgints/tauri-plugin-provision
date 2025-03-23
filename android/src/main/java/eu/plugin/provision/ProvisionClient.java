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
import com.espressif.provisioning.listeners.BleScanListener;

import app.tauri.plugin.Invoke;

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
        this.espDevice = provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants.SecurityType.SECURITY_0);

    }

    public void startScan(Invoke invoke) {
        if (isScanning) {
            invoke.reject("Scan already running");
            return;
        }
        if (!checkPermissions()){
            invoke.reject("Missing permissions");
            return;
        }

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

    private BleScanListener bleScanListener = new BleScanListener() {

        @Override
        public void scanStartFailed() {
            Toast.makeText(activity, "Please turn on Bluetooth to connect BLE device", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPeripheralFound(BluetoothDevice device, ScanResult scanResult) {
            Toast.makeText(activity, "Peripheral Found", Toast.LENGTH_LONG).show();

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

            if (plugin.bluetoothDevices.containsKey(device)) {
                deviceExists = true;
            }

            if (!deviceExists) {
                plugin.bluetoothDevices.put(device, serviceUuid);
            }
        }

        @Override
        public void scanCompleted() {
            Toast.makeText(activity, "Scan is completed", Toast.LENGTH_SHORT).show();
            isScanning = false;
        }

        @Override
        public void onFailure(Exception e) {
            Toast.makeText(activity, "Failure...!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
    };

}
