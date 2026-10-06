use serde::{Deserialize, Serialize};

/// Request to enable or disable input capture.
#[derive(Debug, Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SetEnabledRequest {
    pub enabled: bool,
}

/// Snapshot of the current capture state.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct StateResponse {
    pub enabled: bool,
    /// Whether native capture is actually available on this platform/device.
    pub available: bool,
    /// Name of the controller currently being captured, if any.
    pub device_name: Option<String>,
    /// Logical names of the buttons currently held down.
    pub pressed: Vec<String>,
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
