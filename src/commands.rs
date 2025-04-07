use tauri::{AppHandle, command, Runtime};

use crate::Result;
use crate::ProvisionExt;
use crate::models::*;

#[command]
pub(crate) async fn start_scan<R: Runtime>(
    app: AppHandle<R>,
    payload: ScanRequest,
) -> Result<()> {
    app.provision().start_scan(payload)
}

#[command]
pub(crate) async fn wifi_provision<R: Runtime>(
    app: AppHandle<R>,
    payload: ProvisionRequest,
) -> Result<()> {
    app.provision().wifi_provision(payload)
}
