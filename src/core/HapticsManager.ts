import { settings } from './Settings';

export type HapticPattern =
  | 'tick'
  | 'light'
  | 'medium'
  | 'firm'
  | 'success'
  | 'error'
  | 'screw'
  | 'boot';

/**
 * Android haptics (§42) via the Vibration API, which is what a Capacitor
 * WebView exposes. Patterns are short by design — long buzzes feel cheap.
 */
const PATTERNS: Record<HapticPattern, number | number[]> = {
  tick: 8,
  light: 14,
  medium: 26,
  firm: 42,
  success: [18, 40, 26],
  error: [40, 60, 40, 60, 40],
  screw: 6,
  boot: [12, 30, 12, 30, 60],
};

class HapticsManagerImpl {
  private supported = typeof navigator !== 'undefined' && 'vibrate' in navigator;
  /** Rate-limit the continuous patterns so screwing does not buzz forever. */
  private lastFire = 0;

  get available(): boolean {
    return this.supported;
  }

  fire(pattern: HapticPattern, minGapMs = 0): void {
    if (!this.supported || !settings.get().haptics) return;
    const now = performance.now();
    if (minGapMs > 0 && now - this.lastFire < minGapMs) return;
    this.lastFire = now;
    try {
      navigator.vibrate(PATTERNS[pattern]);
    } catch {
      // Some WebViews throw when vibration is disabled at the OS level.
    }
  }

  stop(): void {
    if (!this.supported) return;
    try {
      navigator.vibrate(0);
    } catch {
      /* ignore */
    }
  }
}

export const haptics = new HapticsManagerImpl();
