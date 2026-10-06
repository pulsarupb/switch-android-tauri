use serde::{Deserialize, Serialize};

/// How a stream is delivered to JavaScript.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum StreamMode {
    Off,
    Push,
    Poll,
}

/// Partial per-stream options. Every field is optional so `configure` is incremental.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StreamOptions {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub mode: Option<StreamMode>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub rate_ms: Option<u64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub deadzone: Option<f64>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub include_raw: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sensor_delay: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub buffer_events: Option<bool>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub buffer_size: Option<usize>,
}

/// Per-stream configuration.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StreamsConfig {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub buttons: Option<StreamOptions>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub axes: Option<StreamOptions>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub touch: Option<StreamOptions>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub imu: Option<StreamOptions>,
}

/// Request to apply stream configuration.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfigureRequest {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub streams: Option<StreamsConfig>,
}

/// Request a snapshot (and optionally drain buffered events).
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PollRequest {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub streams: Option<Vec<String>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub drain: Option<bool>,
}

/// Request to enable or disable input capture.
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SetEnabledRequest {
    pub enabled: bool,
}

/// Request to trigger a rumble/vibration on the controller (or device) vibrator.
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VibrateRequest {
    /// Duration in milliseconds.
    pub duration_ms: u64,
    /// Amplitude in the 1..=255 range. Defaults to 255.
    #[serde(default)]
    pub amplitude: Option<u8>,
}

/// Information about an Android input device.
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceInfo {
    pub id: i32,
    pub name: String,
    pub descriptor: Option<String>,
    pub vendor_id: i32,
    pub product_id: i32,
    pub sources: i32,
    pub is_gamepad: bool,
    pub has_vibrator: bool,
}

/// Response wrapper for the device list.
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceListResponse {
    pub devices: Vec<DeviceInfo>,
}
