import { bus } from './EventBus';
import { settings, type GameSettings, DEFAULT_SETTINGS } from './Settings';
import { type Build, type RgbProfile, createBuild, defaultRgbProfile } from '../sim/Build';
import type { BenchmarkResult } from '../sim/BenchmarkManager';
import { getComponent } from '../data/catalog';
import { DEFAULT_DESK_ID, DESKS } from '../data/desks';

const SAVE_KEY = 'pcbuilder.save.v1';
const SAVE_VERSION = 1;

/** A finished machine parked in the garage (§34). */
export interface SavedPc {
  id: string;
  name: string;
  build: Build;
  result: BenchmarkResult;
  cost: number;
  builtAt: number;
}

export interface CareerState {
  day: number;
  money: number;
  reputation: number;
  /** Job ids already completed. */
  completedJobs: string[];
  /** Job ids currently offered. */
  availableJobs: string[];
  activeJobId: string | null;
  /** Challenge ids completed. */
  completedChallenges: string[];
}

export interface SaveData {
  version: number;
  settings: GameSettings;
  career: CareerState;
  /** Component ids the player owns but has not installed. */
  inventory: string[];
  /** Component ids unlocked in the shop beyond the default set. */
  unlocked: string[];
  /** Workbench the player is using, and the ones they own (cosmetic). */
  deskId: string;
  ownedDesks: string[];
  currentBuild: Build | null;
  savedPcs: SavedPc[];
  achievements: string[];
  rgbProfiles: Record<string, RgbProfile>;
  tutorialComplete: boolean;
  stats: {
    buildsCompleted: number;
    totalEarned: number;
    bestScore: number;
    fastestBuildMs: number | null;
  };
}

export function freshSave(): SaveData {
  return {
    version: SAVE_VERSION,
    settings: { ...DEFAULT_SETTINGS },
    career: {
      day: 1,
      money: 500,
      reputation: 0,
      completedJobs: [],
      availableJobs: [],
      activeJobId: null,
      completedChallenges: [],
    },
    inventory: [],
    unlocked: [],
    deskId: DEFAULT_DESK_ID,
    ownedDesks: [DEFAULT_DESK_ID],
    currentBuild: null,
    savedPcs: [],
    achievements: [],
    rgbProfiles: { Default: defaultRgbProfile() },
    tutorialComplete: false,
    stats: { buildsCompleted: 0, totalEarned: 0, bestScore: 0, fastestBuildMs: null },
  };
}

/**
 * Autosaving store (§48). Writes are debounced so a burst of installs costs one
 * serialisation, and every read is defensive — a corrupt or partial save must
 * never stop the game from starting (§50).
 */
class SaveManagerImpl {
  private data: SaveData = freshSave();
  private timer: number | null = null;
  private available = true;

  load(): SaveData {
    try {
      const raw = localStorage.getItem(SAVE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as Partial<SaveData>;
        this.data = this.migrate(parsed);
      }
    } catch (err) {
      console.warn('[save] could not read save, starting fresh', err);
      this.data = freshSave();
    }
    settings.replace(this.data.settings);
    return this.data;
  }

  /** Merge an unknown save shape onto the current defaults, dropping junk. */
  private migrate(parsed: Partial<SaveData>): SaveData {
    const base = freshSave();
    const out: SaveData = {
      ...base,
      ...parsed,
      version: SAVE_VERSION,
      settings: { ...base.settings, ...(parsed.settings ?? {}) },
      career: { ...base.career, ...(parsed.career ?? {}) },
      stats: { ...base.stats, ...(parsed.stats ?? {}) },
      rgbProfiles: { ...base.rgbProfiles, ...(parsed.rgbProfiles ?? {}) },
    };

    // Drop references to components that no longer exist in the catalog, so an
    // updated build of the game cannot crash on an old save.
    out.inventory = (out.inventory ?? []).filter((id) => !!getComponent(id));
    out.unlocked = (out.unlocked ?? []).filter((id) => !!getComponent(id));
    out.savedPcs = (out.savedPcs ?? []).filter((pc) => pc && pc.build && pc.result);
    for (const pc of out.savedPcs) pc.build = this.sanitiseBuild(pc.build);
    out.currentBuild = out.currentBuild ? this.sanitiseBuild(out.currentBuild) : null;
    out.achievements = (out.achievements ?? []).filter((a) => typeof a === 'string');

    // Drop desks that no longer exist, and never leave the player benchless.
    const deskIds = new Set(DESKS.map((d) => d.id));
    out.ownedDesks = (out.ownedDesks ?? []).filter((id) => deskIds.has(id));
    if (!out.ownedDesks.includes(DEFAULT_DESK_ID)) out.ownedDesks.push(DEFAULT_DESK_ID);
    if (!out.ownedDesks.includes(out.deskId)) out.deskId = DEFAULT_DESK_ID;
    return out;
  }

  private sanitiseBuild(build: Build): Build {
    const fresh = createBuild();
    const merged: Build = { ...fresh, ...build };
    merged.parts = (merged.parts ?? []).filter((p) => p && !!getComponent(p.componentId));
    merged.connectedCables = merged.connectedCables ?? [];
    merged.routedCables = merged.routedCables ?? [];
    merged.rgb = { ...defaultRgbProfile(), ...(merged.rgb ?? {}) };
    merged.rgb.zones = { ...defaultRgbProfile().zones, ...(merged.rgb.zones ?? {}) };
    return merged;
  }

  get(): SaveData {
    return this.data;
  }

  /** Mutate then autosave. */
  update(fn: (data: SaveData) => void): void {
    fn(this.data);
    this.queue();
  }

  queue(delay = 400): void {
    if (this.timer !== null) window.clearTimeout(this.timer);
    this.timer = window.setTimeout(() => this.flush(), delay);
  }

  flush(): void {
    if (this.timer !== null) {
      window.clearTimeout(this.timer);
      this.timer = null;
    }
    if (!this.available) return;
    try {
      this.data.settings = { ...settings.get() };
      localStorage.setItem(SAVE_KEY, JSON.stringify(this.data));
      bus.emit('save:written', {});
    } catch (err) {
      // Quota or a private-mode WebView. Stop retrying rather than spamming.
      console.warn('[save] write failed; progress will not persist', err);
      this.available = false;
    }
  }

  reset(): void {
    this.data = freshSave();
    try {
      localStorage.removeItem(SAVE_KEY);
    } catch {
      /* ignore */
    }
    settings.replace(this.data.settings);
  }
}

export const save = new SaveManagerImpl();
