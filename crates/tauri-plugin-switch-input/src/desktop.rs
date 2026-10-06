use crate::models::*;
use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<SwitchInput<R>> {
    Ok(SwitchInput(app.clone()))
}

/// Desktop fallback. Native Switch input capture is Android-only, so all operations are
/// no-ops that report the feature as unavailable.
pub struct SwitchInput<R: Runtime>(AppHandle<R>);

impl<R: Runtime> SwitchInput<R> {
    pub fn set_enabled(&self, enabled: bool) -> crate::Result<StateResponse> {
        Ok(StateResponse {
            enabled,
            available: false,
            rumble_available: false,
            device_name: None,
            pressed: Vec::new(),
        })
    }

    pub fn state(&self) -> crate::Result<StateResponse> {
        Ok(StateResponse {
            enabled: false,
            available: false,
            rumble_available: false,
            device_name: None,
            pressed: Vec::new(),
        })
    }

    pub fn vibrate(&self, _payload: VibrateRequest) -> crate::Result<()> {
        Ok(())
    }

    pub fn list_devices(&self) -> crate::Result<DeviceListResponse> {
        Ok(DeviceListResponse {
            devices: Vec::new(),
        })
    }
}
