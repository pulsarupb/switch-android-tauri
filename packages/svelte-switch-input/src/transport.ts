import {
  addPluginListener,
  invoke,
  type PluginListener,
} from "@tauri-apps/api/core";
import type {
  DeviceInfo,
  StateResponse,
  SwitchInputEvent,
  SwitchInputTransport,
} from "./types";

const PLUGIN = "switch-input";

/**
 * Transport backed by the `switch-input` Tauri plugin (Android native capture).
 */
export function tauriTransport(): SwitchInputTransport {
  return {
    async subscribe(callback) {
      const listener: PluginListener = await addPluginListener(
        PLUGIN,
        "input",
        (payload) => callback(payload as SwitchInputEvent),
      );
      return () => {
        void listener.unregister();
      };
    },

    getState() {
      return invoke<StateResponse>(`plugin:${PLUGIN}|get_state`);
    },

    setEnabled(enabled) {
      return invoke<StateResponse>(`plugin:${PLUGIN}|set_enabled`, {
        payload: { enabled },
      });
    },

    vibrate(durationMs, amplitude) {
      return invoke<void>(`plugin:${PLUGIN}|vibrate`, {
        payload: { durationMs, amplitude },
      });
    },

    async listDevices() {
      const res = await invoke<{ devices: DeviceInfo[] }>(
        `plugin:${PLUGIN}|list_devices`,
      );
      return res.devices;
    },
  };
}
