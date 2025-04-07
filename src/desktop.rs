use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
  app: &AppHandle<R>,
  _api: PluginApi<R, C>,
) -> crate::Result<Provision<R>> {
  Ok(Provision(app.clone()))
}

/// Access to the provision APIs.
pub struct Provision<R: Runtime>(AppHandle<R>);

impl<R: Runtime> Provision<R> {
  pub fn start_scan(&self, _payload: ScanRequest) -> crate::Result<()> {
    Ok(())
  }

  pub fn wifi_provision(&self, _payload: ProvisionRequest) -> crate::Result<()> {
    Ok(())
  }
}
