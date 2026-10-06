import {
  addPluginListener,
  invoke,
  type PluginListener,
} from "@tauri-apps/api/core";
import type {
  DeviceInfo,
  EffectiveConfig,
  PollOptions,
  Snapshot,
  StreamName,
  StreamsConfig,
  SwitchInputEvent,
  SwitchInputTransport,
} from "./types";

const PLUGIN = "switch-input";

/**
 * Transport backed by the `switch-input` Tauri plugin (Android native capture).
 */
export function tauriTransport(): SwitchInputTransport {
  return {
    async subscribe(stream, callback) {
      const listener: PluginListener = await addPluginListener(
        PLUGIN,
        stream,
        (payload) => callback(payload as SwitchInputEvent),
      );
      return () => {
        void listener.unregister();
      };
    },

    configure(streams: StreamsConfig) {
      return invoke<EffectiveConfig>(`plugin:${PLUGIN}|configure`, {
        payload: { streams },
      });
    },

    poll(options?: PollOptions) {
      return invoke<Snapshot>(`plugin:${PLUGIN}|poll`, {
        payload: { streams: options?.streams, drain: options?.drain },
      });
    },

    getState() {
      return invoke<EffectiveConfig>(`plugin:${PLUGIN}|get_state`);
    },

    setEnabled(enabled) {
      return invoke<EffectiveConfig>(`plugin:${PLUGIN}|set_enabled`, {
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
