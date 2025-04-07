use serde::de::DeserializeOwned;
use tauri::{
    plugin::{PluginApi, PluginHandle},
    AppHandle, Runtime,
};

use crate::models::*;

#[cfg(target_os = "ios")]
tauri::ios_plugin_binding!(init_plugin_provision);

// initializes the Kotlin or Swift plugin classes
pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    api: PluginApi<R, C>,
) -> crate::Result<Provision<R>> {
    #[cfg(target_os = "android")]
    let handle = api.register_android_plugin("eu.plugin.provision", "ProvisionClientPlugin")?;
    #[cfg(target_os = "ios")]
    let handle = api.register_ios_plugin(init_plugin_provision)?;
    Ok(Provision(handle))
}

/// Access to the provision APIs.
pub struct Provision<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> Provision<R> {
    pub fn start_scan(&self, payload: ScanRequest) -> crate::Result<()> {
        self.0
            .run_mobile_plugin("startScan", payload)
            .map_err(Into::into)
    }

    pub fn wifi_provision(&self, payload: ProvisionRequest) -> crate::Result<()> {
        self.0
            .run_mobile_plugin("wifiProvision", payload)
            .map_err(Into::into)
    }
}
