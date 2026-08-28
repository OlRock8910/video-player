import { bus } from './EventBus';

export type QualityPreset = 'low' | 'medium' | 'high' | 'ultra';

export interface GameSettings {
  quality: QualityPreset;
  /** 0 = auto-detect from device performance. */
  autoQuality: boolean;
  targetFps: 30 | 60;
  dynamicResolution: boolean;
  masterVolume: number;
  musicVolume: number;
  sfxVolume: number;
  haptics: boolean;
  /** Accessibility (§49). */
  textScale: number;
  colorBlindSafe: boolean;
  leftHanded: boolean;
  reduceMotion: boolean;
  invertCameraX: boolean;
  cameraSensitivity: number;
  showTutorial: boolean;
  debugMode: boolean;
}

export const DEFAULT_SETTINGS: GameSettings = {
  quality: 'high',
  autoQuality: true,
  targetFps: 60,
  dynamicResolution: true,
  masterVolume: 0.8,
  musicVolume: 0.45,
  sfxVolume: 0.9,
  haptics: true,
  textScale: 1,
  colorBlindSafe: false,
  leftHanded: false,
  reduceMotion: false,
  invertCameraX: false,
  cameraSensitivity: 1,
  showTutorial: true,
  debugMode: false,
};

/** Per-preset renderer knobs consumed by SceneRoot (§39, §40). */
export interface QualityProfile {
  shadows: boolean;
  shadowMapSize: number;
  /** Cap on devicePixelRatio. */
  maxPixelRatio: number;
  antialias: boolean;
  bloom: boolean;
  reflections: boolean;
  particles: number;
  /** Extra point lights beyond the key set. */
  accentLights: number;
  anisotropy: number;
}

export const QUALITY_PROFILES: Record<QualityPreset, QualityProfile> = {
  low: {
    shadows: false,
    shadowMapSize: 512,
    maxPixelRatio: 1,
    antialias: false,
    bloom: false,
    reflections: false,
    particles: 0,
    accentLights: 0,
    anisotropy: 1,
  },
  medium: {
    shadows: true,
    shadowMapSize: 1024,
    maxPixelRatio: 1.4,
    antialias: false,
    bloom: false,
    reflections: false,
    particles: 12,
    accentLights: 2,
    anisotropy: 2,
  },
  high: {
    shadows: true,
    shadowMapSize: 2048,
    maxPixelRatio: 2,
    antialias: true,
    bloom: true,
    reflections: true,
    particles: 24,
    accentLights: 4,
    anisotropy: 4,
  },
  ultra: {
    shadows: true,
    shadowMapSize: 4096,
    maxPixelRatio: 3,
    antialias: true,
    bloom: true,
    reflections: true,
    particles: 48,
    accentLights: 6,
    anisotropy: 8,
  },
};

/**
 * Recommend a preset from what the device tells us (§39). Deliberately
 * conservative — dropping frames feels worse than slightly flat lighting.
 */
export function recommendQuality(): QualityPreset {
  const nav = navigator as Navigator & { deviceMemory?: number; hardwareConcurrency?: number };
  const mem = nav.deviceMemory ?? 4;
  const cores = nav.hardwareConcurrency ?? 4;
  const dpr = window.devicePixelRatio || 1;
  const px = window.screen.width * dpr * window.screen.height * dpr;

  let score = 0;
  score += mem >= 8 ? 3 : mem >= 6 ? 2 : mem >= 4 ? 1 : 0;
  score += cores >= 8 ? 3 : cores >= 6 ? 2 : cores >= 4 ? 1 : 0;
  // A very high resolution panel costs fill rate without adding capability.
  score -= px > 4_000_000 ? 1 : 0;

  if (score >= 6) return 'ultra';
  if (score >= 4) return 'high';
  if (score >= 2) return 'medium';
  return 'low';
}

class SettingsStore {
  private data: GameSettings = { ...DEFAULT_SETTINGS };

  get(): Readonly<GameSettings> {
    return this.data;
  }

  profile(): QualityProfile {
    return QUALITY_PROFILES[this.data.quality];
  }

  set<K extends keyof GameSettings>(key: K, value: GameSettings[K]): void {
    if (this.data[key] === value) return;
    this.data[key] = value;
    this.apply();
    bus.emit('settings:change', {});
  }

  replace(next: Partial<GameSettings>): void {
    this.data = { ...DEFAULT_SETTINGS, ...this.data, ...next };
    this.apply();
    bus.emit('settings:change', {});
  }

  /** Push the accessibility settings that live in CSS onto the document. */
  apply(): void {
    const root = document.documentElement;
    root.style.setProperty('--text-scale', String(this.data.textScale));
    root.classList.toggle('cb-safe', this.data.colorBlindSafe);
    root.classList.toggle('left-handed', this.data.leftHanded);
    root.classList.toggle('reduce-motion', this.data.reduceMotion);
  }
}

export const settings = new SettingsStore();
