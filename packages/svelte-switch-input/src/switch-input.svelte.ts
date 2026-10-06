import { tauriTransport } from "./transport";
import {
  NEUTRAL_AXES,
  NEUTRAL_IMU,
  TouchAction,
  type Axes,
  type DeviceInfo,
  type EffectiveConfig,
  type EffectiveStreamConfig,
  type ImuState,
  type PollOptions,
  type Snapshot,
  type StreamName,
  type StreamsConfig,
  type SwitchInputEvent,
  type SwitchInputTransport,
  type TouchPoint,
} from "./types";

const STREAM_NAMES: StreamName[] = ["buttons", "axes", "touch", "imu"];

/**
 * The library's default configuration enables every stream as `push` for convenience.
 * Pass a `StreamsConfig` to `start()` to opt out (e.g. `{ imu: { mode: "off" } }`).
 */
export const DEFAULT_STREAMS: StreamsConfig = {
  buttons: { mode: "push" },
  axes: { mode: "push" },
  touch: { mode: "push" },
  imu: { mode: "push" },
};

/**
 * Reactive Nintendo Switch input, powered by Svelte 5 runes.
 *
 * ```svelte
 * <script lang="ts">
 *   import { SwitchInput } from "@pulsarupb/svelte-switch-input";
 *
 *   const input = new SwitchInput();
 *   $effect(() => {
 *     void input.start({ axes: { mode: "poll" }, imu: { mode: "off" } });
 *     return () => void input.stop();
 *   });
 * </script>
 * ```
 */
export class SwitchInput {
  #transport: SwitchInputTransport;
  #unsubscribes = new Map<StreamName, () => void>();
  #streamListeners = new Map<StreamName, Set<(event: SwitchInputEvent) => void>>();
  #anyListeners = new Set<(event: SwitchInputEvent) => void>();

  /** Whether native capture is enabled. */
  enabled = $state(false);
  /** Whether native capture is available on the current platform/device. */
  available = $state(false);
  /** Whether the platform exposes a vibrator (the Switch Lite does not). */
  rumbleAvailable = $state(false);
  /** Name of the captured controller, if any. */
  deviceName = $state<string | null>(null);
  /** Last error encountered while talking to the native layer. */
  error = $state<string | null>(null);
  /** Whether a subscription to the native event stream is active. */
  running = $state(false);
  /** Number of push events received since `start()`. */
  eventCount = $state(0);

  /** The resolved per-stream configuration, as reported by the native layer. */
  streams = $state<Record<StreamName, EffectiveStreamConfig> | null>(null);

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

  /**
   * Configure the native streams, then subscribe to every stream that ends up in `push`
   * mode. Defaults to enabling all streams (`DEFAULT_STREAMS`).
   */
  async start(config: StreamsConfig = DEFAULT_STREAMS): Promise<void> {
    if (this.running) return;
    try {
      const effective = await this.#transport.configure(config);
      this.#applyEffective(effective);
      await this.#resubscribe();
      this.running = true;
      this.error = null;
    } catch (error) {
      this.error = error instanceof Error ? error.message : String(error);
      this.available = false;
    }
  }

  /** Apply an incremental configuration update and resubscribe as needed. */
  async configure(config: StreamsConfig): Promise<EffectiveConfig> {
    const effective = await this.#transport.configure(config);
    this.#applyEffective(effective);
    await this.#resubscribe();
    return effective;
  }

  /** Stop all subscriptions and clear transient state. */
  async stop(): Promise<void> {
    for (const unsubscribe of this.#unsubscribes.values()) unsubscribe();
    this.#unsubscribes.clear();
    this.running = false;
    this.touches = [];
  }

  /**
   * Fetch a snapshot of the configured streams. Use this to poll at your own rate instead
   * of subscribing to push events. Pass `{ drain: true }` to also receive buffered events.
   */
  async poll(options?: PollOptions): Promise<Snapshot> {
    const snapshot = await this.#transport.poll(options);
    this.#applySnapshot(snapshot);
    return snapshot;
  }

  /** Re-read the state/config snapshot from the native layer. */
  async refresh(): Promise<void> {
    try {
      this.#applyEffective(await this.#transport.getState());
      this.error = null;
    } catch (error) {
      this.error = error instanceof Error ? error.message : String(error);
    }
  }

  /** Enable or disable native capture (master switch). */
  async setEnabled(enabled: boolean): Promise<void> {
    this.#applyEffective(await this.#transport.setEnabled(enabled));
  }

  /** Trigger a rumble/vibration on the controller (no-op on the Switch Lite). */
  async vibrate(durationMs = 200, amplitude?: number): Promise<void> {
    await this.#transport.vibrate(durationMs, amplitude);
  }

  /** List the Android input devices visible to the app. */
  listDevices(): Promise<DeviceInfo[]> {
    return this.#transport.listDevices();
  }

  /** Subscribe to a single stream's push events. Returns an unsubscribe function. */
  on(
    stream: StreamName,
    listener: (event: SwitchInputEvent) => void,
  ): () => void {
    let set = this.#streamListeners.get(stream);
    if (!set) {
      set = new Set();
      this.#streamListeners.set(stream, set);
    }
    set.add(listener);
    return () => set.delete(listener);
  }

  /** Subscribe to every push event regardless of stream. */
  onEvent(listener: (event: SwitchInputEvent) => void): () => void {
    this.#anyListeners.add(listener);
    return () => this.#anyListeners.delete(listener);
  }

  #applyEffective(config: EffectiveConfig): void {
    this.enabled = config.enabled;
    this.available = config.available;
    this.rumbleAvailable = config.rumbleAvailable;
    this.deviceName = config.deviceName;
    this.streams = config.streams;
  }

  async #resubscribe(): Promise<void> {
    for (const unsubscribe of this.#unsubscribes.values()) unsubscribe();
    this.#unsubscribes.clear();

    const streams = this.streams;
    if (!streams) return;

    for (const name of STREAM_NAMES) {
      if (streams[name]?.mode !== "push") continue;
      const unsubscribe = await this.#transport.subscribe(name, (event) =>
        this.#applyEvent(event),
      );
      this.#unsubscribes.set(name, unsubscribe);
    }
  }

  #applySnapshot(snapshot: Snapshot): void {
    if (snapshot.buttons) {
      const next: Record<string, boolean> = {};
      for (const name of snapshot.buttons.pressed) next[name] = true;
      this.buttons = next;
    }
    if (snapshot.axes) {
      const { raw, ...axes } = snapshot.axes;
      this.axes = { ...axes };
      this.rawAxes = { ...(raw ?? {}) };
    }
    if (snapshot.touch) {
      this.touches = snapshot.touch.pointers.map((pointer) => ({ ...pointer }));
    }
    if (snapshot.imu) {
      this.imu = {
        accel: [...snapshot.imu.accel] as ImuState["accel"],
        gyro: [...snapshot.imu.gyro] as ImuState["gyro"],
        timestamp: snapshot.timestamp,
      };
    }
  }

  #applyEvent(event: SwitchInputEvent): void {
    this.lastEvent = event;
    this.eventCount += 1;

    switch (event.type) {
      case "button":
        this.buttons[event.name] = event.pressed;
        break;
      case "axes": {
        const { raw, ...axes } = event.axes;
        this.axes = { ...axes };
        this.rawAxes = { ...(raw ?? {}) };
        break;
      }
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

    const streamListeners = this.#streamListeners.get(this.#streamOf(event));
    if (streamListeners) {
      for (const listener of streamListeners) {
        listener(event);
      }
    }
    for (const listener of this.#anyListeners) {
      listener(event);
    }
  }

  #streamOf(event: SwitchInputEvent): StreamName {
    return event.type === "button" ? "buttons" : event.type;
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
