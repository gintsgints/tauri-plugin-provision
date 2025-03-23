use tauri::{AppHandle, command, Runtime};

use crate::models::*;
use crate::Result;
use crate::ProvisionExt;

#[command]
pub(crate) async fn start_scan<R: Runtime>(
    app: AppHandle<R>,
    payload: ScanRequest,
) -> Result<()> {
    app.provision().start_scan(payload)
}
