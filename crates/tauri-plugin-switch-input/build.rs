const COMMANDS: &[&str] = &[
    "set_enabled",
    "get_state",
    "configure",
    "poll",
    "vibrate",
    "list_devices",
    "register_listener",
    "remove_listener",
];

fn main() {
    tauri_plugin::Builder::new(COMMANDS)
        .android_path("android")
        .build();
}
