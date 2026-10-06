use crate::models::*;
use serde::de::DeserializeOwned;
use tauri::{
    plugin::{PluginApi, PluginHandle},
    AppHandle, Runtime,
};

pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    api: PluginApi<R, C>,
) -> crate::Result<SwitchInput<R>> {
    let handle = api.register_android_plugin(
        "dev.pulsarupb.switchinput.plugin",
        "SwitchInputPlugin",
    )?;
    Ok(SwitchInput(handle))
}

/// Access to the switch-input APIs.
pub struct SwitchInput<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> SwitchInput<R> {
    pub fn configure(&self, payload: ConfigureRequest) -> crate::Result<serde_json::Value> {
        self.0
            .run_mobile_plugin("configure", payload)
            .map_err(Into::into)
    }

    pub fn poll(&self, payload: PollRequest) -> crate::Result<serde_json::Value> {
        self.0
            .run_mobile_plugin("poll", payload)
            .map_err(Into::into)
    }

    pub fn state(&self) -> crate::Result<serde_json::Value> {
        self.0
            .run_mobile_plugin("getState", ())
            .map_err(Into::into)
    }

    pub fn set_enabled(&self, enabled: bool) -> crate::Result<serde_json::Value> {
        self.0
            .run_mobile_plugin("setEnabled", SetEnabledRequest { enabled })
            .map_err(Into::into)
    }

    pub fn vibrate(&self, payload: VibrateRequest) -> crate::Result<()> {
        self.0
            .run_mobile_plugin("vibrate", payload)
            .map_err(Into::into)
    }

    pub fn list_devices(&self) -> crate::Result<DeviceListResponse> {
        self.0
            .run_mobile_plugin("listDevices", ())
            .map_err(Into::into)
    }
}
