use crate::models::*;
use crate::Result;
use crate::SwitchInputExt;
use tauri::{command, AppHandle, Runtime};

#[command]
pub(crate) async fn configure<R: Runtime>(
    app: AppHandle<R>,
    payload: ConfigureRequest,
) -> Result<serde_json::Value> {
    app.switch_input().configure(payload)
}

#[command]
pub(crate) async fn poll<R: Runtime>(
    app: AppHandle<R>,
    payload: PollRequest,
) -> Result<serde_json::Value> {
    app.switch_input().poll(payload)
}

#[command]
pub(crate) async fn get_state<R: Runtime>(app: AppHandle<R>) -> Result<serde_json::Value> {
    app.switch_input().state()
}

#[command]
pub(crate) async fn set_enabled<R: Runtime>(
    app: AppHandle<R>,
    payload: SetEnabledRequest,
) -> Result<serde_json::Value> {
    app.switch_input().set_enabled(payload.enabled)
}

#[command]
pub(crate) async fn vibrate<R: Runtime>(
    app: AppHandle<R>,
    payload: VibrateRequest,
) -> Result<()> {
    app.switch_input().vibrate(payload)
}

#[command]
pub(crate) async fn list_devices<R: Runtime>(app: AppHandle<R>) -> Result<DeviceListResponse> {
    app.switch_input().list_devices()
}
