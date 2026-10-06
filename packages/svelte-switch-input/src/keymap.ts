import type { SwitchButton } from "./types";

/** Human readable labels for each logical button. */
export const BUTTON_LABELS: Record<SwitchButton, string> = {
  A: "A",
  B: "B",
  X: "X",
  Y: "Y",
  L: "L",
  R: "R",
  ZL: "ZL",
  ZR: "ZR",
  MINUS: "−",
  PLUS: "+",
  HOME: "⌂",
  LSTICK: "LS",
  RSTICK: "RS",
  UP: "↑",
  DOWN: "↓",
  LEFT: "←",
  RIGHT: "→",
  Z: "Z",
};

/** Buttons grouped for rendering a controller layout. */
export const BUTTON_LAYOUT: SwitchButton[][] = [
  ["L", "ZL", "MINUS", "PLUS", "ZR", "R"],
  ["UP", "LEFT", "RIGHT", "DOWN"],
  ["X", "Y", "A", "B"],
  ["LSTICK", "RSTICK", "HOME"],
];

export const ALL_BUTTONS: SwitchButton[] = [
  "A",
  "B",
  "X",
  "Y",
  "L",
  "R",
  "ZL",
  "ZR",
  "MINUS",
  "PLUS",
  "HOME",
  "LSTICK",
  "RSTICK",
  "UP",
  "DOWN",
  "LEFT",
  "RIGHT",
];

export function labelFor(button: string): string {
  return BUTTON_LABELS[button as SwitchButton] ?? button;
}
