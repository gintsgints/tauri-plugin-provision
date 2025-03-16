package eu.plugin.provision;

import android.app.Activity;
import android.telephony.ims.ProvisioningManager;

import com.espressif.provisioning.ESPProvisionManager;

import app.tauri.annotation.Command;
import app.tauri.annotation.InvokeArg;
import app.tauri.annotation.TauriPlugin;
import app.tauri.plugin.JSObject;
import app.tauri.plugin.Plugin;
import app.tauri.plugin.Invoke;

@InvokeArg
class PingArgs {
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

@TauriPlugin
public class ExamplePlugin extends Plugin {
    private final Example implementation = new Example();
    private final ESPProvisionManager provisionManager;

    public ExamplePlugin(Activity activity) {
        super(activity);
        this.provisionManager = ESPProvisionManager.getInstance(activity);
    }

    @Command
    public void ping(Invoke invoke) {
        PingArgs args = invoke.parseArgs(PingArgs.class);

        JSObject ret = new JSObject();
        ret.put("value", implementation.pong(args.getValue() != null ? args.getValue() : "default value :("));
        invoke.resolve(ret);
    }
}
