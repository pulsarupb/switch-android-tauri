/** Logical Nintendo Switch buttons produced by the native capture layer. */
export type SwitchButton =
  | "A"
  | "B"
  | "X"
  | "Y"
  | "L"
  | "R"
  | "ZL"
  | "ZR"
  | "MINUS"
  | "PLUS"
  | "HOME"
  | "LSTICK"
  | "RSTICK"
  | "UP"
  | "DOWN"
  | "LEFT"
  | "RIGHT"
  | "Z";

/** The independently configurable input streams. */
export type StreamName = "buttons" | "axes" | "touch" | "imu";

/**
 * How a stream is delivered:
 * - `off`  — not captured at all
 * - `push` — an event per change (throttled), via the per-stream event name
 * - `poll` — captured and cached, no events; fetched with `poll()`
 */
export type StreamMode = "off" | "push" | "poll";

/** IMU sensor sampling hint. */
export type SensorDelay = "ui" | "game" | "fastest";

/** Per-stream options. All fields are optional; `configure` is incremental. */
export interface StreamOptions {
  mode?: StreamMode;
  /** Minimum milliseconds between pushes (throttle). */
  rateMs?: number;
  /** Minimum axis change required to count as a change (axes only). */
  deadzone?: number;
  /** Include the raw Android axis map (axes only). */
  includeRaw?: boolean;
  /** Sensor sampling hint (imu only). */
  sensorDelay?: SensorDelay;
  /** Buffer discrete events so `poll({ drain: true })` can return them. */
  bufferEvents?: boolean;
  /** Maximum buffered events per stream. */
  bufferSize?: number;
}

export interface StreamsConfig {
  buttons?: StreamOptions;
  axes?: StreamOptions;
  touch?: StreamOptions;
  imu?: StreamOptions;
}

/** The fully-resolved config for a single stream. */
export interface EffectiveStreamConfig {
  mode: StreamMode;
  rateMs: number;
  deadzone: number;
  includeRaw: boolean;
  sensorDelay: SensorDelay;
  bufferEvents: boolean;
  bufferSize: number;
}

export interface EffectiveConfig {
  enabled: boolean;
  available: boolean;
  rumbleAvailable: boolean;
  deviceName: string | null;
  streams: Record<StreamName, EffectiveStreamConfig>;
}

/** Normalized analog axes. Triggers are in 0..1, sticks and hats in -1..1. */
export interface Axes {
  leftX: number;
  leftY: number;
  rightX: number;
  rightY: number;
  l2: number;
  r2: number;
  hatX: number;
  hatY: number;
}

export const NEUTRAL_AXES: Axes = {
  leftX: 0,
  leftY: 0,
  rightX: 0,
  rightY: 0,
  l2: 0,
  r2: 0,
  hatX: 0,
  hatY: 0,
};

/** Axes plus an optional raw Android axis map. */
export interface AxesWithRaw extends Axes {
  raw?: Record<string, number>;
}

/** A single touch pointer, in view pixels. */
export interface TouchPoint {
  id: number;
  x: number;
  y: number;
  pressure: number;
  size: number;
  touchMajor: number;
  touchMinor: number;
  toolMajor: number;
  toolMinor: number;
}

/** Android MotionEvent action codes. */
export const TouchAction = {
  DOWN: 0,
  UP: 1,
  MOVE: 2,
  CANCEL: 3,
  POINTER_DOWN: 5,
  POINTER_UP: 6,
} as const;

export interface ImuState {
  accel: [number, number, number];
  gyro: [number, number, number];
  timestamp: number;
}

export const NEUTRAL_IMU: ImuState = {
  accel: [0, 0, 0],
  gyro: [0, 0, 0],
  timestamp: 0,
};

export interface ButtonEvent {
  type: "button";
  timestamp: number;
  name: string;
  pressed: boolean;
  repeat: number;
  scanCode: number;
  keyCode: number;
  deviceId: number;
  source: number;
  pressedButtons: string[];
}

export interface AxesEvent {
  type: "axes";
  timestamp: number;
  axes: AxesWithRaw;
  deviceId: number;
  source: number;
}

export interface TouchEvent {
  type: "touch";
  timestamp: number;
  action: number;
  actionIndex: number;
  pointerCount: number;
  pointers: TouchPoint[];
  deviceId: number;
  source: number;
}

export interface ImuEvent {
  type: "imu";
  timestamp: number;
  accel: [number, number, number];
  gyro: [number, number, number];
  sensorTimestamp: number;
}

export type SwitchInputEvent = ButtonEvent | AxesEvent | TouchEvent | ImuEvent;

/** A snapshot returned by `poll()`. Only captured (non-`off`) streams are present. */
export interface Snapshot {
  timestamp: number;
  buttons?: {
    pressed: string[];
    state: Record<string, boolean>;
  };
  axes?: AxesWithRaw;
  touch?: {
    pointers: TouchPoint[];
  };
  imu?: {
    accel: [number, number, number];
    gyro: [number, number, number];
    sensorTimestamp: number;
  };
  /** Present when `poll({ drain: true })`; buffered discrete events. */
  events?: SwitchInputEvent[];
}

export interface PollOptions {
  /** Restrict the snapshot to these streams. Defaults to all captured streams. */
  streams?: StreamName[];
  /** Also return and clear buffered events for streams that buffer them. */
  drain?: boolean;
}

export interface DeviceInfo {
  id: number;
  name: string;
  descriptor?: string;
  vendorId: number;
  productId: number;
  sources: number;
  isGamepad: boolean;
  hasVibrator: boolean;
}

/**
 * Pluggable transport for the input stream. The default implementation talks to the
 * `switch-input` Tauri plugin, but any transport can be supplied (tests, other hosts...).
 */
export interface SwitchInputTransport {
  subscribe(
    stream: StreamName,
    callback: (event: SwitchInputEvent) => void,
  ): Promise<() => void>;
  configure(config: StreamsConfig): Promise<EffectiveConfig>;
  poll(options?: PollOptions): Promise<Snapshot>;
  getState(): Promise<EffectiveConfig>;
  setEnabled(enabled: boolean): Promise<EffectiveConfig>;
  vibrate(durationMs: number, amplitude?: number): Promise<void>;
  listDevices(): Promise<DeviceInfo[]>;
}
