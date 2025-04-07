use tauri::{
  plugin::{Builder, TauriPlugin},
  Manager, Runtime,
};

pub use models::*;

#[cfg(desktop)]
mod desktop;
#[cfg(mobile)]
mod mobile;

mod commands;
mod error;
mod models;

pub use error::{Error, Result};

#[cfg(desktop)]
use desktop::Provision;
#[cfg(mobile)]
use mobile::Provision;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the provision APIs.
pub trait ProvisionExt<R: Runtime> {
  fn provision(&self) -> &Provision<R>;
}

impl<R: Runtime, T: Manager<R>> crate::ProvisionExt<R> for T {
  fn provision(&self) -> &Provision<R> {
    self.state::<Provision<R>>().inner()
  }
}

/// Initializes the plugin.
pub fn init<R: Runtime>() -> TauriPlugin<R> {
  Builder::new("provision")
    .invoke_handler(tauri::generate_handler![commands::start_scan, commands::wifi_provision])
    .setup(|app, api| {
      #[cfg(mobile)]
      let provision = mobile::init(app, api)?;
      #[cfg(desktop)]
      let provision = desktop::init(app, api)?;
      app.manage(provision);
      Ok(())
    })
    .build()
}
