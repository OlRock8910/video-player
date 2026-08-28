import type { Airflow, CableKind, Component, Slot } from '../data/types';
import { getComponent } from '../data/catalog';
import {
  isCase,
  isCooler,
  isCpu,
  isFan,
  isGpu,
  isMobo,
  isPsu,
  isRam,
  isStorage,
} from '../data/types';

/** Build lifecycle (§53). Later stages must never invalidate earlier ones. */
export const BUILD_STATES = [
  'CASE_CLOSED',
  'CASE_OPEN',
  'MOTHERBOARD_INSTALLED',
  'CPU_INSTALLED',
  'RAM_INSTALLED',
  'STORAGE_INSTALLED',
  'COOLER_INSTALLED',
  'PSU_INSTALLED',
  'GPU_INSTALLED',
  'CABLES_CONNECTED',
  'BUILD_COMPLETE',
  'POWERING_ON',
  'POST',
  'BENCHMARK',
] as const;

export type BuildState = (typeof BUILD_STATES)[number];

export const stateIndex = (s: BuildState): number => BUILD_STATES.indexOf(s);

/** A component physically placed in the build. */
export interface InstalledPart {
  componentId: string;
  slot: Slot;
  /** Fan mounts only: which way the blades push air (§17). */
  airflow?: Airflow;
  /** Screws driven home for this part, out of `screwsRequired`. */
  screwsDriven: number;
  screwsRequired: number;
}

export type PasteQuality = 'none' | 'sparse' | 'good' | 'excessive';

export interface RgbProfile {
  mode: 'static' | 'breathing' | 'rainbow' | 'wave' | 'pulse' | 'reactive';
  /** Hex colours per zone. */
  zones: Record<RgbZone, number>;
  speed: number;
  brightness: number;
}

export type RgbZone = 'ram' | 'gpu' | 'fans' | 'motherboard' | 'case' | 'aio';
export const RGB_ZONES: RgbZone[] = ['ram', 'gpu', 'fans', 'motherboard', 'case', 'aio'];

export function defaultRgbProfile(): RgbProfile {
  return {
    mode: 'rainbow',
    zones: {
      ram: 0x00d0ff,
      gpu: 0x00d0ff,
      fans: 0x00d0ff,
      motherboard: 0x9d7bff,
      case: 0x00d0ff,
      aio: 0x00d0ff,
    },
    speed: 0.5,
    brightness: 0.85,
  };
}

/** Everything that makes up one PC. Serialised wholesale by the save system. */
export interface Build {
  id: string;
  name: string;
  state: BuildState;
  /** Side panel physically removed. */
  panelRemoved: boolean;
  /** Standoffs placed before the motherboard can go in (§9). */
  standoffsPlaced: number;
  parts: InstalledPart[];
  paste: PasteQuality;
  /** Cables the player has physically connected (§15). */
  connectedCables: CableKind[];
  /** Cables routed behind the tray rather than draped across the board (§18). */
  routedCables: CableKind[];
  cableTies: number;
  rgb: RgbProfile;
  /** Wall-clock ms spent building, for speed challenges. */
  elapsedMs: number;
  /** Incorrect install attempts, for the NO MISTAKES achievement. */
  mistakes: number;
  createdAt: number;
  /** Career job this build is fulfilling, if any. */
  jobId?: string;
}

export function createBuild(name = 'Untitled Build'): Build {
  return {
    id: `build-${Date.now().toString(36)}-${Math.floor(Math.random() * 1e4).toString(36)}`,
    name,
    state: 'CASE_CLOSED',
    panelRemoved: false,
    standoffsPlaced: 0,
    parts: [],
    paste: 'none',
    connectedCables: [],
    routedCables: [],
    cableTies: 0,
    rgb: defaultRgbProfile(),
    elapsedMs: 0,
    mistakes: 0,
    createdAt: Date.now(),
  };
}

/* ------------------------------------------------------------------ */
/* Read helpers — every system queries the build through these.        */
/* ------------------------------------------------------------------ */

export function partsOf(build: Build): Component[] {
  return build.parts
    .map((p) => getComponent(p.componentId))
    .filter((c): c is Component => c !== undefined);
}

export function partInSlot(build: Build, slot: Slot): InstalledPart | undefined {
  return build.parts.find((p) => p.slot === slot);
}

export function componentInSlot(build: Build, slot: Slot): Component | undefined {
  const p = partInSlot(build, slot);
  return p ? getComponent(p.componentId) : undefined;
}

export const buildCase = (b: Build) => {
  const c = componentInSlot(b, 'case');
  return c && isCase(c) ? c : undefined;
};
export const buildMobo = (b: Build) => {
  const c = componentInSlot(b, 'mobo-tray');
  return c && isMobo(c) ? c : undefined;
};
export const buildCpu = (b: Build) => {
  const c = componentInSlot(b, 'cpu-socket');
  return c && isCpu(c) ? c : undefined;
};
export const buildCooler = (b: Build) => {
  const c = componentInSlot(b, 'cooler-mount');
  return c && isCooler(c) ? c : undefined;
};
export const buildGpu = (b: Build) => {
  const c = componentInSlot(b, 'pcie-0');
  return c && isGpu(c) ? c : undefined;
};
export const buildPsu = (b: Build) => {
  const c = componentInSlot(b, 'psu-bay');
  return c && isPsu(c) ? c : undefined;
};

export const buildRam = (b: Build) =>
  partsOf(b).filter(isRam);
export const buildStorage = (b: Build) =>
  partsOf(b).filter(isStorage);
export const buildFans = (b: Build) =>
  partsOf(b).filter(isFan);

export function ramSlotsUsed(b: Build): Slot[] {
  return b.parts
    .filter((p) => p.slot.startsWith('ram-'))
    .map((p) => p.slot);
}

/** Total GB of memory installed. */
export function totalRam(b: Build): number {
  return buildRam(b).reduce((sum, r) => sum + r.capacity * r.sticks, 0);
}

/** Total GB of storage installed. */
export function totalStorage(b: Build): number {
  return buildStorage(b).reduce((sum, s) => sum + s.capacity, 0);
}

/** What the player has spent on parts currently in the machine. */
export function buildCost(b: Build): number {
  return partsOf(b).reduce((sum, c) => sum + c.price, 0);
}

/** Every cable this build's parts demand, de-duplicated. */
export function requiredCables(b: Build): CableKind[] {
  const set = new Set<CableKind>();
  for (const c of partsOf(b)) {
    for (const cable of c.requiredCables ?? []) set.add(cable);
  }
  // A GPU needs one lead per connector, but the player routes them as one
  // logical "GPU POWER" run, so one pcie8 entry covers the card.
  return [...set];
}

export function missingCables(b: Build): CableKind[] {
  return requiredCables(b).filter((c) => !b.connectedCables.includes(c));
}

/** True once every part is in and every required cable is plugged in. */
export function isBuildComplete(b: Build): boolean {
  return (
    !!buildCase(b) &&
    !!buildMobo(b) &&
    !!buildCpu(b) &&
    !!buildCooler(b) &&
    buildRam(b).length > 0 &&
    buildStorage(b).length > 0 &&
    !!buildPsu(b) &&
    missingCables(b).length === 0 &&
    b.parts.every((p) => p.screwsDriven >= p.screwsRequired)
  );
}

/** Advance the state machine without ever moving backwards (§53). */
export function advanceState(b: Build, next: BuildState): void {
  if (stateIndex(next) > stateIndex(b.state)) b.state = next;
}

/**
 * Recompute the furthest state the build has legitimately reached. Called after
 * any install so the HUD and tutorial always agree with the actual hardware.
 */
export function recomputeState(b: Build): BuildState {
  let s: BuildState = 'CASE_CLOSED';
  if (b.panelRemoved) s = 'CASE_OPEN';
  if (buildMobo(b)) s = 'MOTHERBOARD_INSTALLED';
  if (buildCpu(b)) s = 'CPU_INSTALLED';
  if (buildRam(b).length > 0) s = 'RAM_INSTALLED';
  if (buildStorage(b).length > 0) s = 'STORAGE_INSTALLED';
  if (buildCooler(b)) s = 'COOLER_INSTALLED';
  if (buildPsu(b)) s = 'PSU_INSTALLED';
  if (buildGpu(b)) s = 'GPU_INSTALLED';
  if (missingCables(b).length === 0 && requiredCables(b).length > 0) s = 'CABLES_CONNECTED';
  if (isBuildComplete(b)) s = 'BUILD_COMPLETE';
  // Never regress past a cinematic the player already triggered.
  return stateIndex(b.state) > stateIndex(s) && stateIndex(b.state) >= stateIndex('POWERING_ON')
    ? b.state
    : s;
}

/** How many screws a part needs before it counts as installed (§7). */
export function screwsFor(component: Component): number {
  if (isMobo(component)) return component.formFactor === 'ITX' ? 4 : 6;
  if (isGpu(component)) return component.slotWidth >= 3 ? 2 : 1;
  if (isPsu(component)) return 4;
  if (isFan(component)) return 4;
  if (isStorage(component)) return component.kind === 'm2' ? 1 : 4;
  if (isCooler(component)) return component.coolerType === 'stock' ? 0 : 4;
  return 0;
}
