use crate::models::*;
use serde::de::DeserializeOwned;
use serde_json::{json, Value};
use tauri::{plugin::PluginApi, AppHandle, Runtime};

pub fn init<R: Runtime, C: DeserializeOwned>(
    app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> crate::Result<SwitchInput<R>> {
    Ok(SwitchInput(app.clone()))
}

/// Desktop fallback. Native Switch input capture is Android-only, so the API reports the
/// feature as unavailable and configuration is echoed back without any capture.
pub struct SwitchInput<R: Runtime>(AppHandle<R>);

impl<R: Runtime> SwitchInput<R> {
    pub fn configure(&self, payload: ConfigureRequest) -> crate::Result<Value> {
        Ok(merge_config(payload.streams))
    }

    pub fn poll(&self, _payload: PollRequest) -> crate::Result<Value> {
        Ok(json!({ "timestamp": 0 }))
    }

    pub fn state(&self) -> crate::Result<Value> {
        Ok(merge_config(None))
    }

    pub fn set_enabled(&self, enabled: bool) -> crate::Result<Value> {
        let mut config = merge_config(None);
        config["enabled"] = json!(enabled);
        Ok(config)
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

fn default_stream(mode: &str, rate_ms: u64, deadzone: f64, buffer_events: bool, buffer_size: u64) -> Value {
    json!({
        "mode": mode,
        "rateMs": rate_ms,
        "deadzone": deadzone,
        "includeRaw": false,
        "sensorDelay": "ui",
        "bufferEvents": buffer_events,
        "bufferSize": buffer_size,
    })
}

fn merge_config(streams: Option<StreamsConfig>) -> Value {
    let mut buttons = default_stream("push", 0, 0.0, true, 256);
    let mut axes = default_stream("push", 16, 0.01, false, 64);
    let mut touch = default_stream("push", 0, 0.0, false, 64);
    let mut imu = default_stream("off", 50, 0.0, false, 64);

    if let Some(config) = streams {
        apply(&mut buttons, config.buttons.as_ref());
        apply(&mut axes, config.axes.as_ref());
        apply(&mut touch, config.touch.as_ref());
        apply(&mut imu, config.imu.as_ref());
    }

    json!({
        "enabled": false,
        "available": false,
        "rumbleAvailable": false,
        "deviceName": Value::Null,
        "streams": {
            "buttons": buttons,
            "axes": axes,
            "touch": touch,
            "imu": imu,
        },
    })
}

fn apply(target: &mut Value, options: Option<&StreamOptions>) {
    let Some(options) = options else { return };
    if let Some(mode) = options.mode {
        target["mode"] = json!(mode);
    }
    if let Some(rate_ms) = options.rate_ms {
        target["rateMs"] = json!(rate_ms);
    }
    if let Some(deadzone) = options.deadzone {
        target["deadzone"] = json!(deadzone);
    }
    if let Some(include_raw) = options.include_raw {
        target["includeRaw"] = json!(include_raw);
    }
    if let Some(sensor_delay) = &options.sensor_delay {
        target["sensorDelay"] = json!(sensor_delay);
    }
    if let Some(buffer_events) = options.buffer_events {
        target["bufferEvents"] = json!(buffer_events);
    }
    if let Some(buffer_size) = options.buffer_size {
        target["bufferSize"] = json!(buffer_size);
    }
}
