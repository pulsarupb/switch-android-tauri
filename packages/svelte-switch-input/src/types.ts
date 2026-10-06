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
  axes: Axes;
  raw: Record<string, number>;
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

export interface StateResponse {
  enabled: boolean;
  available: boolean;
  deviceName: string | null;
  pressed: string[];
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
  subscribe(callback: (event: SwitchInputEvent) => void): Promise<() => void>;
  getState(): Promise<StateResponse>;
  setEnabled(enabled: boolean): Promise<StateResponse>;
  vibrate(durationMs: number, amplitude?: number): Promise<void>;
  listDevices(): Promise<DeviceInfo[]>;
}
