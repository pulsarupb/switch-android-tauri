<script lang="ts">
  import { onMount } from "svelte";
  import {
    SwitchInput,
    ALL_BUTTONS,
    labelFor,
    type DeviceInfo,
  } from "@pulsarupb/svelte-switch-input";

  const input = new SwitchInput();

  let devices = $state<DeviceInfo[]>([]);
  let deviceError = $state<string | null>(null);

  onMount(() => {
    void input.start();
    void loadDevices();
    return () => {
      void input.stop();
    };
  });

  async function loadDevices() {
    try {
      devices = await input.listDevices();
      deviceError = null;
    } catch (error) {
      deviceError = error instanceof Error ? error.message : String(error);
    }
  }

  function stickStyle(x: number, y: number) {
    const radius = 42;
    return `transform: translate(calc(-50% + ${x * radius}px), calc(-50% + ${y * radius}px));`;
  }

  function touchStyle(x: number, y: number) {
    return `left: ${(x / 1280) * 100}%; top: ${(y / 720) * 100}%;`;
  }

  const imuAccelMag = $derived(
    Math.hypot(input.imu.accel[0], input.imu.accel[1], input.imu.accel[2]),
  );
  const imuGyroMag = $derived(
    Math.hypot(input.imu.gyro[0], input.imu.gyro[1], input.imu.gyro[2]),
  );
</script>

<main>
  <header>
    <div class="title">
      <h1>Switch Input</h1>
      <p class="subtitle">
        {input.deviceName ?? "No controller detected"}
      </p>
    </div>
    <div class="status">
      <span class="pill" class:ok={input.available} class:bad={!input.available}>
        {input.available ? "native" : "unavailable"}
      </span>
      <span class="pill" class:ok={input.running}>
        {input.running ? "listening" : "stopped"}
      </span>
      <span class="pill">events {input.eventCount}</span>
      {#if input.lastEvent}
        <span class="pill accent">{input.lastEvent.type}</span>
      {/if}
      <button
        class="ghost"
        onclick={() => input.setEnabled(!input.enabled)}
      >
        {input.enabled ? "Disable capture" : "Enable capture"}
      </button>
    </div>
  </header>

  {#if input.error}
    <p class="error">Error: {input.error}</p>
  {/if}

  <section class="grid">
    <div class="card">
      <h2>Buttons</h2>
      <div class="buttons">
        {#each ALL_BUTTONS as button (button)}
          <span class="button" class:active={input.buttons[button]}>
            {labelFor(button)}
          </span>
        {/each}
      </div>
      <p class="hint">Held: {input.pressed.length ? input.pressed.join(", ") : "none"}</p>
    </div>

    <div class="card">
      <h2>Sticks &amp; triggers</h2>
      <div class="sticks">
        <div class="stick">
          <div class="dot" style={stickStyle(input.axes.leftX, input.axes.leftY)}></div>
        </div>
        <div class="stick">
          <div class="dot" style={stickStyle(input.axes.rightX, input.axes.rightY)}></div>
        </div>
      </div>
      <div class="bars">
        <label>L2 <meter min="0" max="1" value={input.axes.l2}></meter></label>
        <label>R2 <meter min="0" max="1" value={input.axes.r2}></meter></label>
        <label>Hat X <meter min="-1" max="1" value={input.axes.hatX}></meter></label>
        <label>Hat Y <meter min="-1" max="1" value={input.axes.hatY}></meter></label>
      </div>
    </div>

    <div class="card">
      <h2>Touch <span class="count">{input.touches.length}</span></h2>
      <div class="touchpad">
        {#each input.touches as pointer (pointer.id)}
          <div class="touch" style={touchStyle(pointer.x, pointer.y)}></div>
        {/each}
      </div>
    </div>

    <div class="card">
      <h2>IMU</h2>
      <div class="imu">
        <div class="axis">
          <span>accel</span>
          <code>{input.imu.accel.map((v) => v.toFixed(2)).join(", ")}</code>
          <meter min="0" max="20" value={imuAccelMag}></meter>
        </div>
        <div class="axis">
          <span>gyro</span>
          <code>{input.imu.gyro.map((v) => v.toFixed(2)).join(", ")}</code>
          <meter min="0" max="10" value={imuGyroMag}></meter>
        </div>
      </div>
    </div>
  </section>

  <section class="card devices">
    <h2>
      Devices
      <button class="ghost small" onclick={loadDevices}>Refresh</button>
    </h2>
    {#if deviceError}
      <p class="error">{deviceError}</p>
    {/if}
    <ul>
      {#each devices as device (device.id)}
        <li class:gamepad={device.isGamepad}>
          <strong>{device.name}</strong>
          <span>id {device.id}</span>
          <span>sources {device.sources}</span>
          {#if device.isGamepad}<span class="tag">gamepad</span>{/if}
          {#if device.hasVibrator}<span class="tag">rumble</span>{/if}
        </li>
      {:else}
        <li class="muted">No devices loaded.</li>
      {/each}
    </ul>
  </section>

  <footer>
    <span>events: {input.eventCount}</span>
    {#if input.lastEvent}
      <span>last: {input.lastEvent.type}</span>
    {/if}
  </footer>
</main>

<style>
  main {
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 0.6rem;
    padding: calc(0.6rem + env(safe-area-inset-top)) 1rem
      calc(0.7rem + env(safe-area-inset-bottom));
    overflow: hidden;
  }

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 1rem;
    flex-wrap: wrap;
  }

  h1 {
    margin: 0;
    font-size: 1.35rem;
    letter-spacing: 0.02em;
  }

  .subtitle {
    margin: 0.15rem 0 0;
    color: #8b98a9;
    font-size: 0.85rem;
  }

  h2 {
    margin: 0 0 0.6rem;
    font-size: 0.95rem;
    text-transform: uppercase;
    letter-spacing: 0.08em;
    color: #9fb0c4;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .status {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .pill {
    padding: 0.2rem 0.6rem;
    border-radius: 999px;
    font-size: 0.72rem;
    background: #1b2431;
    color: #8b98a9;
    border: 1px solid #263140;
  }

  .pill.ok {
    background: #123024;
    color: #58d68d;
    border-color: #1e5c3c;
  }

  .pill.bad {
    background: #341a1a;
    color: #e07a7a;
    border-color: #5c2626;
  }

  .pill.accent {
    background: #2a2140;
    color: #b69cf5;
    border-color: #4a3a75;
  }

  .ghost {
    background: #1b2431;
    color: #d7e1ec;
    border: 1px solid #2b3948;
    border-radius: 0.5rem;
    padding: 0.4rem 0.75rem;
    font: inherit;
    cursor: pointer;
  }

  .ghost:hover {
    background: #223043;
  }

  .ghost.small {
    padding: 0.15rem 0.5rem;
    font-size: 0.7rem;
    text-transform: none;
    letter-spacing: 0;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
    gap: 0.6rem;
    flex: 1;
    min-height: 0;
  }

  .card {
    background: #111823;
    border: 1px solid #1e2836;
    border-radius: 0.8rem;
    padding: 0.65rem 0.8rem;
    min-height: 0;
    overflow: auto;
    display: flex;
    flex-direction: column;
    scrollbar-width: none;
  }

  .card::-webkit-scrollbar {
    display: none;
  }

  .buttons {
    display: flex;
    flex-wrap: wrap;
    gap: 0.4rem;
  }

  .button {
    min-width: 2.4rem;
    padding: 0.35rem 0.55rem;
    text-align: center;
    border-radius: 0.5rem;
    background: #17202d;
    border: 1px solid #263140;
    color: #b7c4d4;
    font-size: 0.85rem;
    transition: background 80ms ease, color 80ms ease, transform 80ms ease;
  }

  .button.active {
    background: #2f81f7;
    border-color: #4c9bff;
    color: #fff;
    transform: translateY(1px) scale(0.97);
  }

  .hint {
    margin: 0.6rem 0 0;
    color: #8b98a9;
    font-size: 0.8rem;
  }

  .sticks {
    display: flex;
    gap: 1rem;
    justify-content: center;
    padding: 0.1rem 0;
  }

  .stick {
    position: relative;
    width: 100px;
    height: 100px;
    border-radius: 50%;
    background: radial-gradient(circle at 50% 50%, #16202c, #0d141d);
    border: 1px solid #263140;
  }

  .dot {
    position: absolute;
    left: 50%;
    top: 50%;
    width: 20px;
    height: 20px;
    border-radius: 50%;
    background: #2f81f7;
    box-shadow: 0 0 12px rgba(47, 129, 247, 0.7);
  }

  .bars {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.3rem 1rem;
    font-size: 0.8rem;
    color: #9fb0c4;
  }

  .bars label {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  meter {
    flex: 1;
    height: 0.6rem;
  }

  .touchpad {
    position: relative;
    width: 100%;
    flex: 1;
    min-height: 120px;
    background: #0d141d;
    border: 1px solid #263140;
    border-radius: 0.5rem;
    overflow: hidden;
  }

  .touch {
    position: absolute;
    width: 26px;
    height: 26px;
    margin: -13px 0 0 -13px;
    border-radius: 50%;
    background: rgba(88, 214, 141, 0.85);
    box-shadow: 0 0 14px rgba(88, 214, 141, 0.7);
  }

  .count {
    color: #58d68d;
  }

  .imu {
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }

  .axis {
    display: grid;
    grid-template-columns: 3rem 1fr;
    gap: 0.3rem 0.5rem;
    align-items: center;
    font-size: 0.75rem;
    color: #9fb0c4;
  }

  .axis code {
    color: #d7e1ec;
    font-size: 0.75rem;
  }

  .axis meter {
    grid-column: 1 / -1;
    height: 0.45rem;
  }

  .devices ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.3rem;
    max-height: 96px;
    overflow: auto;
    scrollbar-width: none;
  }

  .devices ul::-webkit-scrollbar {
    display: none;
  }

  .devices li {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.4rem 0.6rem;
    border-radius: 0.5rem;
    background: #0d141d;
    font-size: 0.8rem;
    color: #9fb0c4;
  }

  .devices li.gamepad {
    border-left: 3px solid #2f81f7;
  }

  .devices strong {
    color: #d7e1ec;
  }

  .tag {
    margin-left: auto;
    padding: 0.1rem 0.45rem;
    border-radius: 999px;
    background: #17202d;
    border: 1px solid #263140;
    font-size: 0.68rem;
  }

  .tag + .tag {
    margin-left: 0;
  }

  .muted {
    color: #5c6b7d;
  }

  .error {
    color: #e07a7a;
    margin: 0;
  }

  footer {
    display: flex;
    gap: 1rem;
    color: #5c6b7d;
    font-size: 0.75rem;
  }
</style>
