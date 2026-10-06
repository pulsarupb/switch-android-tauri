import { tauriTransport } from "./transport";
import {
  NEUTRAL_AXES,
  NEUTRAL_IMU,
  TouchAction,
  type Axes,
  type DeviceInfo,
  type ImuState,
  type SwitchInputEvent,
  type SwitchInputTransport,
  type TouchPoint,
} from "./types";

/**
 * Reactive Nintendo Switch input state, powered by Svelte 5 runes.
 *
 * ```svelte
 * <script lang="ts">
 *   import { SwitchInput } from "@pulsarupb/svelte-switch-input";
 *
 *   const input = new SwitchInput();
 *   $effect(() => {
 *     void input.start();
 *     return () => void input.stop();
 *   });
 * </script>
 *
 * <p>A pressed: {input.buttons.A}</p>
 * ```
 */
export class SwitchInput {
  #transport: SwitchInputTransport;
  #unsubscribe: (() => void) | null = null;
  #eventListeners = new Set<(event: SwitchInputEvent) => void>();

  /** Whether native capture is enabled. */
  enabled = $state(false);
  /** Whether native capture is available on the current platform/device. */
  available = $state(false);
  /** Name of the captured controller, if any. */
  deviceName = $state<string | null>(null);
  /** Last error encountered while talking to the native layer. */
  error = $state<string | null>(null);
  /** Whether a subscription to the native event stream is active. */
  running = $state(false);
  /** Number of events received since `start()`. */
  eventCount = $state(0);

  /** Per-button pressed state, keyed by logical button name. */
  buttons = $state<Record<string, boolean>>({});
  /** Normalized analog axes. */
  axes = $state<Axes>({ ...NEUTRAL_AXES });
  /** Raw Android axis values keyed by axis name (X, Y, Z, RX, ...). */
  rawAxes = $state<Record<string, number>>({});
  /** Currently active touch pointers. */
  touches = $state<TouchPoint[]>([]);
  /** Latest IMU reading. */
  imu = $state<ImuState>({ ...NEUTRAL_IMU });
  /** The most recent event of any kind. */
  lastEvent = $state<SwitchInputEvent | null>(null);

  /** Logical names of the buttons currently held down. */
  pressed = $derived(
    Object.entries(this.buttons)
      .filter(([, value]) => value)
      .map(([name]) => name),
  );

  constructor(transport: SwitchInputTransport = tauriTransport()) {
    this.#transport = transport;
  }

  /** Subscribe to the native input stream and refresh the initial state. */
  async start(): Promise<void> {
    if (this.running) return;
    try {
      this.#unsubscribe = await this.#transport.subscribe((event) =>
        this.#applyEvent(event),
      );
      this.running = true;
      this.error = null;
      await this.refresh();
    } catch (error) {
      this.error = error instanceof Error ? error.message : String(error);
      this.available = false;
    }
  }

  /** Stop the subscription and clear transient state. */
  async stop(): Promise<void> {
    this.#unsubscribe?.();
    this.#unsubscribe = null;
    this.running = false;
    this.touches = [];
  }

  /** Re-read the state snapshot from the native layer. */
  async refresh(): Promise<void> {
    try {
      const state = await this.#transport.getState();
      this.enabled = state.enabled;
      this.available = state.available;
      this.deviceName = state.deviceName;
      this.error = null;
    } catch (error) {
      this.error = error instanceof Error ? error.message : String(error);
    }
  }

  /** Enable or disable native capture. */
  async setEnabled(enabled: boolean): Promise<void> {
    const state = await this.#transport.setEnabled(enabled);
    this.enabled = state.enabled;
    this.available = state.available;
    this.deviceName = state.deviceName;
  }

  /** Trigger a rumble/vibration on the controller. */
  async vibrate(durationMs = 200, amplitude?: number): Promise<void> {
    await this.#transport.vibrate(durationMs, amplitude);
  }

  /** List the Android input devices visible to the app. */
  listDevices(): Promise<DeviceInfo[]> {
    return this.#transport.listDevices();
  }

  /** Register a callback invoked for every raw event. Returns an unsubscribe function. */
  onEvent(listener: (event: SwitchInputEvent) => void): () => void {
    this.#eventListeners.add(listener);
    return () => this.#eventListeners.delete(listener);
  }

  #applyEvent(event: SwitchInputEvent): void {
    this.lastEvent = event;
    this.eventCount += 1;

    switch (event.type) {
      case "button":
        this.buttons[event.name] = event.pressed;
        break;
      case "axes":
        this.axes = { ...event.axes };
        this.rawAxes = { ...event.raw };
        break;
      case "touch":
        this.#applyTouch(event);
        break;
      case "imu":
        this.imu = {
          accel: [...event.accel] as ImuState["accel"],
          gyro: [...event.gyro] as ImuState["gyro"],
          timestamp: event.timestamp,
        };
        break;
    }

    for (const listener of this.#eventListeners) {
      listener(event);
    }
  }

  #applyTouch(event: Extract<SwitchInputEvent, { type: "touch" }>): void {
    const map = new Map(this.touches.map((pointer) => [pointer.id, pointer]));
    const lifted = event.pointers[event.actionIndex];

    switch (event.action) {
      case TouchAction.DOWN:
      case TouchAction.POINTER_DOWN:
      case TouchAction.MOVE:
        map.clear();
        for (const pointer of event.pointers) {
          map.set(pointer.id, { ...pointer });
        }
        break;
      case TouchAction.POINTER_UP:
        if (lifted) map.delete(lifted.id);
        for (const pointer of event.pointers) {
          if (!lifted || pointer.id !== lifted.id) {
            map.set(pointer.id, { ...pointer });
          }
        }
        break;
      case TouchAction.UP:
      case TouchAction.CANCEL:
        map.clear();
        break;
    }

    this.touches = Array.from(map.values());
  }
}

/** Convenience factory. */
export function createSwitchInput(
  transport?: SwitchInputTransport,
): SwitchInput {
  return new SwitchInput(transport);
}
