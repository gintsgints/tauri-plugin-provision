package eu.plugin.provision;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import java.util.HashMap;

import app.tauri.annotation.Command;
import app.tauri.annotation.TauriPlugin;
import app.tauri.plugin.Invoke;
import app.tauri.plugin.Plugin;
import app.tauri.plugin.JSObject;

@TauriPlugin
public class ProvisionClientPlugin extends Plugin {
    private final ProvisionClient provisionClient;
    public HashMap<String, BluetoothDevice> bluetoothDevices;
    public JSObject foundDevice;
    private Activity activity;

    public interface OnDevice {
        void onDevice(JSObject payload);
    }

    public ProvisionClientPlugin(Activity activity) {
        super(activity);
        this.provisionClient = new ProvisionClient(activity, this);
        this.bluetoothDevices = new HashMap<>();
        this.activity = activity;
    }

    @Command
    public void startScan(Invoke invoke) {
        provisionClient.startScan(invoke, (JSObject payload) -> {
            trigger("onDevice", payload);
        });
    }

    @Command
    public void wifiProvision(Invoke invoke) {
        provisionClient.wifiProvision(invoke);
    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(activity);
    }

    @Override
    public void onPause() {
        EventBus.getDefault().unregister(this);
        super.onPause();
    }
}
