import { settings } from './Settings';

export type Sfx =
  | 'ui-tap'
  | 'ui-back'
  | 'screw'
  | 'screw-done'
  | 'metal'
  | 'plastic'
  | 'glass'
  | 'cable-click'
  | 'ram-click'
  | 'pcie-click'
  | 'cpu-click'
  | 'paste'
  | 'power-button'
  | 'relay'
  | 'fan-spinup'
  | 'psu-hum'
  | 'rgb-on'
  | 'success'
  | 'error'
  | 'post-beep'
  | 'cash';

/**
 * All audio is synthesised at runtime (§43). Nothing to download, nothing to
 * decode, and every sound can be re-tuned by changing numbers here.
 */
class AudioManagerImpl {
  private ctx: AudioContext | null = null;
  private master!: GainNode;
  private sfxBus!: GainNode;
  private musicBus!: GainNode;
  private noiseBuffer: AudioBuffer | null = null;
  private musicNodes: { stop: () => void } | null = null;
  private currentTrack: string | null = null;
  /** Continuous fan/PSU loop while a PC is running. */
  private ambience: { stop: () => void } | null = null;

  /** Browsers require a gesture before audio starts; call from the first tap. */
  unlock(): void {
    if (this.ctx) {
      if (this.ctx.state === 'suspended') void this.ctx.resume();
      return;
    }
    try {
      const Ctor =
        window.AudioContext ??
        (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!Ctor) return;
      this.ctx = new Ctor();
      this.master = this.ctx.createGain();
      this.sfxBus = this.ctx.createGain();
      this.musicBus = this.ctx.createGain();
      this.sfxBus.connect(this.master);
      this.musicBus.connect(this.master);
      this.master.connect(this.ctx.destination);
      this.applyVolumes();
      this.noiseBuffer = this.makeNoise(2);
    } catch {
      // Audio is a nicety; a device without it still plays fine.
      this.ctx = null;
    }
  }

  applyVolumes(): void {
    if (!this.ctx) return;
    const s = settings.get();
    this.master.gain.value = s.masterVolume;
    this.sfxBus.gain.value = s.sfxVolume;
    this.musicBus.gain.value = s.musicVolume;
  }

  private makeNoise(seconds: number): AudioBuffer {
    const ctx = this.ctx!;
    const len = Math.floor(ctx.sampleRate * seconds);
    const buf = ctx.createBuffer(1, len, ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    return buf;
  }

  private now(): number {
    return this.ctx!.currentTime;
  }

  /** A short pitched blip — the building block for clicks and beeps. */
  private tone(
    freq: number,
    duration: number,
    opts: { type?: OscillatorType; gain?: number; delay?: number; sweepTo?: number } = {}
  ): void {
    if (!this.ctx) return;
    const t = this.now() + (opts.delay ?? 0);
    const osc = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    osc.type = opts.type ?? 'sine';
    osc.frequency.setValueAtTime(freq, t);
    if (opts.sweepTo) osc.frequency.exponentialRampToValueAtTime(Math.max(20, opts.sweepTo), t + duration);
    const peak = opts.gain ?? 0.2;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + 0.006);
    g.gain.exponentialRampToValueAtTime(0.0001, t + duration);
    osc.connect(g).connect(this.sfxBus);
    osc.start(t);
    osc.stop(t + duration + 0.02);
  }

  /** Filtered noise burst — impacts, scrapes, air. */
  private burst(
    duration: number,
    opts: {
      filter?: BiquadFilterType;
      freq?: number;
      q?: number;
      gain?: number;
      delay?: number;
      sweepTo?: number;
    } = {}
  ): void {
    if (!this.ctx || !this.noiseBuffer) return;
    const t = this.now() + (opts.delay ?? 0);
    const src = this.ctx.createBufferSource();
    src.buffer = this.noiseBuffer;
    src.loop = true;
    const filter = this.ctx.createBiquadFilter();
    filter.type = opts.filter ?? 'bandpass';
    filter.frequency.setValueAtTime(opts.freq ?? 1800, t);
    if (opts.sweepTo) filter.frequency.exponentialRampToValueAtTime(Math.max(40, opts.sweepTo), t + duration);
    filter.Q.value = opts.q ?? 1.2;
    const g = this.ctx.createGain();
    const peak = opts.gain ?? 0.2;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + 0.005);
    g.gain.exponentialRampToValueAtTime(0.0001, t + duration);
    src.connect(filter).connect(g).connect(this.sfxBus);
    src.start(t);
    src.stop(t + duration + 0.02);
  }

  play(sfx: Sfx): void {
    if (!this.ctx) return;
    if (this.ctx.state === 'suspended') void this.ctx.resume();

    switch (sfx) {
      case 'ui-tap':
        this.tone(1180, 0.05, { type: 'triangle', gain: 0.09 });
        break;
      case 'ui-back':
        this.tone(620, 0.07, { type: 'triangle', gain: 0.08, sweepTo: 420 });
        break;

      case 'screw':
        // A short ratchet: two tight noise ticks with a metallic ring.
        this.burst(0.045, { freq: 2600, q: 3, gain: 0.13 });
        this.tone(3100, 0.035, { type: 'square', gain: 0.035, delay: 0.01 });
        break;
      case 'screw-done':
        this.burst(0.07, { freq: 1500, q: 2, gain: 0.16, sweepTo: 700 });
        this.tone(920, 0.09, { type: 'triangle', gain: 0.1 });
        break;

      case 'metal':
        this.burst(0.12, { freq: 2200, q: 1.4, gain: 0.16, sweepTo: 900 });
        this.tone(1450, 0.14, { type: 'triangle', gain: 0.06, sweepTo: 1100 });
        break;
      case 'plastic':
        this.burst(0.06, { freq: 1100, q: 1.8, gain: 0.13, sweepTo: 600 });
        break;
      case 'glass':
        this.tone(2600, 0.5, { type: 'sine', gain: 0.07 });
        this.tone(3900, 0.42, { type: 'sine', gain: 0.04, delay: 0.01 });
        break;

      case 'cable-click':
        this.burst(0.04, { freq: 900, q: 2.5, gain: 0.16 });
        this.tone(540, 0.05, { type: 'square', gain: 0.05, delay: 0.012 });
        break;
      case 'ram-click':
        // The two-clip snap everyone knows.
        this.burst(0.035, { freq: 1900, q: 3, gain: 0.18 });
        this.burst(0.04, { freq: 1650, q: 3, gain: 0.16, delay: 0.055 });
        this.tone(760, 0.06, { type: 'square', gain: 0.05, delay: 0.055 });
        break;
      case 'pcie-click':
        this.burst(0.05, { freq: 1400, q: 2.2, gain: 0.2 });
        this.tone(420, 0.09, { type: 'square', gain: 0.07, delay: 0.015 });
        break;
      case 'cpu-click':
        this.burst(0.05, { freq: 800, q: 1.6, gain: 0.15, sweepTo: 400 });
        this.tone(300, 0.11, { type: 'triangle', gain: 0.07, delay: 0.02 });
        break;

      case 'paste':
        this.burst(0.36, { filter: 'lowpass', freq: 420, q: 0.7, gain: 0.07 });
        break;

      case 'power-button':
        this.burst(0.05, { freq: 700, q: 2.2, gain: 0.2, sweepTo: 320 });
        this.tone(220, 0.1, { type: 'square', gain: 0.09, delay: 0.01 });
        break;
      case 'relay':
        this.burst(0.03, { freq: 3000, q: 4, gain: 0.22 });
        this.tone(140, 0.07, { type: 'square', gain: 0.12, delay: 0.005 });
        break;

      case 'fan-spinup':
        this.burst(1.5, { filter: 'lowpass', freq: 180, q: 0.6, gain: 0.11, sweepTo: 900 });
        break;
      case 'psu-hum':
        this.tone(100, 0.9, { type: 'sawtooth', gain: 0.035 });
        break;
      case 'rgb-on':
        this.tone(880, 0.2, { type: 'sine', gain: 0.07, sweepTo: 1760 });
        this.tone(1320, 0.24, { type: 'sine', gain: 0.045, delay: 0.05, sweepTo: 2640 });
        break;

      case 'success':
        this.tone(523, 0.12, { type: 'triangle', gain: 0.11 });
        this.tone(659, 0.12, { type: 'triangle', gain: 0.11, delay: 0.09 });
        this.tone(784, 0.22, { type: 'triangle', gain: 0.12, delay: 0.18 });
        break;
      case 'error':
        this.tone(220, 0.16, { type: 'sawtooth', gain: 0.1 });
        this.tone(165, 0.24, { type: 'sawtooth', gain: 0.1, delay: 0.11 });
        break;
      case 'post-beep':
        this.tone(1046, 0.16, { type: 'square', gain: 0.13 });
        break;
      case 'cash':
        this.tone(1320, 0.08, { type: 'triangle', gain: 0.1 });
        this.tone(1760, 0.16, { type: 'triangle', gain: 0.1, delay: 0.06 });
        this.burst(0.2, { freq: 4200, q: 1.5, gain: 0.05, delay: 0.02 });
        break;
    }
  }

  /**
   * Running-PC ambience: layered fan noise plus transformer hum, with the
   * loudness driven by the build's simulated dB (§36).
   */
  startAmbience(db: number): void {
    if (!this.ctx || !this.noiseBuffer) return;
    this.stopAmbience();
    const ctx = this.ctx;
    const level = Math.max(0, Math.min(1, (db - 18) / 32));

    const air = ctx.createBufferSource();
    air.buffer = this.noiseBuffer;
    air.loop = true;
    const airFilter = ctx.createBiquadFilter();
    airFilter.type = 'lowpass';
    airFilter.frequency.value = 420 + level * 700;
    airFilter.Q.value = 0.6;
    const airGain = ctx.createGain();
    airGain.gain.value = 0.0001;
    airGain.gain.linearRampToValueAtTime(0.012 + level * 0.05, ctx.currentTime + 1.2);
    air.connect(airFilter).connect(airGain).connect(this.sfxBus);
    air.start();

    const hum = ctx.createOscillator();
    hum.type = 'sawtooth';
    hum.frequency.value = 100;
    const humFilter = ctx.createBiquadFilter();
    humFilter.type = 'lowpass';
    humFilter.frequency.value = 220;
    const humGain = ctx.createGain();
    humGain.gain.value = 0.0001;
    humGain.gain.linearRampToValueAtTime(0.008 + level * 0.014, ctx.currentTime + 1.2);
    hum.connect(humFilter).connect(humGain).connect(this.sfxBus);
    hum.start();

    this.ambience = {
      stop: () => {
        const t = ctx.currentTime;
        airGain.gain.cancelScheduledValues(t);
        humGain.gain.cancelScheduledValues(t);
        airGain.gain.linearRampToValueAtTime(0.0001, t + 0.4);
        humGain.gain.linearRampToValueAtTime(0.0001, t + 0.4);
        setTimeout(() => {
          try {
            air.stop();
            hum.stop();
          } catch {
            /* already stopped */
          }
        }, 500);
      },
    };
  }

  stopAmbience(): void {
    this.ambience?.stop();
    this.ambience = null;
  }

  /**
   * Background music (§44) — a slow generative pad plus an arpeggio, seeded
   * differently per track so the menu and the workshop feel distinct.
   */
  playMusic(track: 'menu' | 'workshop' | 'victory'): void {
    if (!this.ctx) return;
    if (this.currentTrack === track) return;
    this.stopMusic();
    this.currentTrack = track;
    const ctx = this.ctx;

    const scales: Record<typeof track, number[]> = {
      menu: [110, 164.81, 196, 246.94, 293.66],
      workshop: [98, 146.83, 174.61, 220, 261.63],
      victory: [130.81, 164.81, 196, 261.63, 329.63],
    };
    const notes = scales[track];
    const padGain = ctx.createGain();
    padGain.gain.value = 0.0001;
    padGain.gain.linearRampToValueAtTime(track === 'victory' ? 0.16 : 0.075, ctx.currentTime + 2);
    padGain.connect(this.musicBus);

    const oscs: OscillatorNode[] = [];
    // Sustained root + fifth pad.
    for (const f of [notes[0], notes[2]]) {
      const o = ctx.createOscillator();
      o.type = 'sine';
      o.frequency.value = f;
      const lfo = ctx.createOscillator();
      lfo.frequency.value = 0.08 + Math.random() * 0.05;
      const lfoGain = ctx.createGain();
      lfoGain.gain.value = 1.4;
      lfo.connect(lfoGain).connect(o.frequency);
      lfo.start();
      o.connect(padGain);
      o.start();
      oscs.push(o, lfo);
    }

    // Sparse arpeggio on a timer so it never feels looped.
    const interval = window.setInterval(
      () => {
        if (!this.ctx || this.currentTrack !== track) return;
        const f = notes[Math.floor(Math.random() * notes.length)] * (Math.random() < 0.4 ? 4 : 2);
        const t = ctx.currentTime;
        const o = ctx.createOscillator();
        const g = ctx.createGain();
        o.type = 'triangle';
        o.frequency.value = f;
        g.gain.setValueAtTime(0.0001, t);
        g.gain.exponentialRampToValueAtTime(0.05, t + 0.15);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 1.6);
        o.connect(g).connect(this.musicBus);
        o.start(t);
        o.stop(t + 1.7);
      },
      track === 'victory' ? 700 : 2400
    );

    this.musicNodes = {
      stop: () => {
        window.clearInterval(interval);
        const t = ctx.currentTime;
        padGain.gain.cancelScheduledValues(t);
        padGain.gain.linearRampToValueAtTime(0.0001, t + 0.8);
        setTimeout(() => {
          for (const o of oscs) {
            try {
              o.stop();
            } catch {
              /* already stopped */
            }
          }
        }, 900);
      },
    };
  }

  stopMusic(): void {
    this.musicNodes?.stop();
    this.musicNodes = null;
    this.currentTrack = null;
  }
}

export const audio = new AudioManagerImpl();
