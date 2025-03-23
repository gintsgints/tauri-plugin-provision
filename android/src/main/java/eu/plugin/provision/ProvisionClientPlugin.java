package eu.plugin.provision;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import java.util.HashMap;

import app.tauri.annotation.Command;
import app.tauri.annotation.TauriPlugin;
import app.tauri.plugin.Invoke;
import app.tauri.plugin.JSObject;
import app.tauri.plugin.Plugin;

@TauriPlugin
public class ProvisionClientPlugin extends Plugin {
    private final ProvisionClient provisionClient;
    public HashMap<BluetoothDevice, String> bluetoothDevices;

    public ProvisionClientPlugin(Activity activity) {
        super(activity);
        this.provisionClient = new ProvisionClient(activity, this);
    }

    @Command
    public void startScan(Invoke invoke) {
        provisionClient.startScan(invoke);
    }
}
