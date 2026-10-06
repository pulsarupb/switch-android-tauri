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
use desktop::SwitchInput;
#[cfg(mobile)]
use mobile::SwitchInput;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the
/// switch-input APIs.
pub trait SwitchInputExt<R: Runtime> {
    fn switch_input(&self) -> &SwitchInput<R>;
}

impl<R: Runtime, T: Manager<R>> crate::SwitchInputExt<R> for T {
    fn switch_input(&self) -> &SwitchInput<R> {
        self.state::<SwitchInput<R>>().inner()
    }
}

/// Initializes the plugin.
pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("switch-input")
        .invoke_handler(tauri::generate_handler![
            commands::set_enabled,
            commands::get_state,
            commands::vibrate,
            commands::list_devices,
        ])
        .setup(|app, api| {
            #[cfg(mobile)]
            let switch_input = mobile::init(app, api)?;
            #[cfg(desktop)]
            let switch_input = desktop::init(app, api)?;
            app.manage(switch_input);
            Ok(())
        })
        .build()
}
