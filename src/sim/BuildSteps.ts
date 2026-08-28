import type { Category, Slot } from '../data/types';
import {
  type Build,
  buildCase,
  buildCooler,
  buildCpu,
  buildGpu,
  buildMobo,
  buildPsu,
  buildRam,
  buildStorage,
  missingCables,
  requiredCables,
} from './Build';
import { standoffsNeeded } from './CompatibilityManager';

export type StepId =
  | 'choose-case'
  | 'open-panel'
  | 'standoffs'
  | 'motherboard'
  | 'cpu'
  | 'paste'
  | 'cooler'
  | 'ram'
  | 'storage'
  | 'psu'
  | 'gpu'
  | 'fans'
  | 'cables'
  | 'cable-management'
  | 'power-on';

export interface BuildStep {
  id: StepId;
  /** Short label shown above the instruction. */
  label: string;
  /** One instruction at a time (§46). */
  instruction: string;
  /** Which shop category the parts tray should offer, if any. */
  category?: Category;
  /** Slots the player may drop into during this step. */
  slots: (build: Build) => Slot[];
  /** Has the player finished this step? */
  isComplete: (build: Build) => boolean;
  /** Steps the player may skip (optional hardware). */
  optional?: boolean;
  /** Camera focus target when the step begins. */
  focus?: Slot;
}

const ramSlots = (build: Build): Slot[] => {
  const mobo = buildMobo(build);
  const count = mobo?.ramSlots ?? 4;
  // Recommend the dual-channel pair first (§13).
  const order = count === 4 ? [1, 3, 0, 2] : [0, 1];
  return order.slice(0, count).map((i) => `ram-${i}` as Slot);
};

const fanSlots = (build: Build): Slot[] => {
  const pcCase = buildCase(build);
  if (!pcCase) return [];
  const out: Slot[] = [];
  for (const [mount, spec] of Object.entries(pcCase.fanMounts)) {
    for (let i = 0; i < spec.count; i++) out.push(`fan-${mount}-${i}` as Slot);
  }
  return out;
};

const storageSlots = (build: Build): Slot[] => {
  const mobo = buildMobo(build);
  const pcCase = buildCase(build);
  const out: Slot[] = [];
  for (let i = 0; i < (mobo?.m2Slots ?? 1); i++) out.push(`m2-${i}` as Slot);
  for (let i = 0; i < (pcCase?.driveBays ?? 0); i++) out.push(`drive-bay-${i}` as Slot);
  return out;
};

/**
 * The build pipeline. The workshop walks these in order, showing exactly one
 * instruction at a time, and the tutorial is simply the first run through it.
 */
export const BUILD_STEPS: BuildStep[] = [
  {
    id: 'choose-case',
    label: 'STEP 1 — CHASSIS',
    instruction: 'Pick a case and set it on the bench.',
    category: 'case',
    slots: () => ['case'],
    isComplete: (b) => !!buildCase(b),
  },
  {
    id: 'open-panel',
    label: 'STEP 2 — OPEN IT UP',
    instruction: 'Hold each thumbscrew to back it out, then slide the side panel off.',
    slots: () => [],
    isComplete: (b) => b.panelRemoved,
  },
  {
    id: 'standoffs',
    label: 'STEP 3 — STANDOFFS',
    instruction: 'Screw the brass standoffs into the tray. The board sits on these, never on bare metal.',
    slots: () => ['mobo-tray'],
    isComplete: (b) => {
      const mobo = buildMobo(b);
      const needed = mobo ? standoffsNeeded(mobo.formFactor) : 9;
      return b.standoffsPlaced >= needed;
    },
    focus: 'mobo-tray',
  },
  {
    id: 'motherboard',
    label: 'STEP 4 — MOTHERBOARD',
    instruction: 'Line the rear I/O up with the cut-out, lower the board onto the standoffs, then drive the screws.',
    category: 'motherboard',
    slots: () => ['mobo-tray'],
    isComplete: (b) => !!buildMobo(b),
    focus: 'mobo-tray',
  },
  {
    id: 'cpu',
    label: 'STEP 5 — PROCESSOR',
    instruction: 'Open the retention arm, match the triangle to the socket corner, and lower the CPU in flat.',
    category: 'cpu',
    slots: () => ['cpu-socket'],
    isComplete: (b) => !!buildCpu(b),
    focus: 'cpu-socket',
  },
  {
    id: 'paste',
    label: 'STEP 6 — THERMAL PASTE',
    instruction: 'Squeeze a pea-sized blob onto the middle of the heatspreader. Close enough is good enough.',
    slots: () => ['cpu-socket'],
    isComplete: (b) => b.paste !== 'none',
    focus: 'cpu-socket',
  },
  {
    id: 'cooler',
    label: 'STEP 7 — COOLING',
    instruction: 'Seat the cooler on the CPU and tighten the screws in a cross pattern.',
    category: 'cooler',
    slots: () => ['cooler-mount'],
    isComplete: (b) => !!buildCooler(b),
    focus: 'cooler-mount',
  },
  {
    id: 'ram',
    label: 'STEP 8 — MEMORY',
    instruction: 'Match the notch, then push down until both clips click.',
    category: 'ram',
    slots: ramSlots,
    isComplete: (b) => buildRam(b).length > 0,
    focus: 'ram-1',
  },
  {
    id: 'storage',
    label: 'STEP 9 — STORAGE',
    instruction: 'Slide the drive in at an angle, press it flat, and fit the retaining screw.',
    category: 'storage',
    slots: storageSlots,
    isComplete: (b) => buildStorage(b).length > 0,
    focus: 'm2-0',
  },
  {
    id: 'psu',
    label: 'STEP 10 — POWER SUPPLY',
    instruction: 'Drop the PSU into the shroud with the fan facing the vent, then screw it to the back panel.',
    category: 'psu',
    slots: () => ['psu-bay'],
    isComplete: (b) => !!buildPsu(b),
    focus: 'psu-bay',
  },
  {
    id: 'gpu',
    label: 'STEP 11 — GRAPHICS',
    instruction: 'Remove the slot covers, line the card up with the PCIe slot, and press until the latch clicks.',
    category: 'gpu',
    slots: () => ['pcie-0'],
    isComplete: (b) => !!buildGpu(b),
    optional: true,
    focus: 'pcie-0',
  },
  {
    id: 'fans',
    label: 'STEP 12 — CASE FANS',
    instruction: 'Mount your fans and set each one to intake or exhaust. Air should flow front to back.',
    category: 'fan',
    slots: fanSlots,
    isComplete: (b) => b.parts.some((p) => p.slot.startsWith('fan-')),
    optional: true,
  },
  {
    id: 'cables',
    label: 'STEP 13 — CABLES',
    instruction: 'Connect every cable the build needs. Each one clicks home when it is fully seated.',
    slots: () => [],
    isComplete: (b) => requiredCables(b).length > 0 && missingCables(b).length === 0,
  },
  {
    id: 'cable-management',
    label: 'STEP 14 — CABLE MANAGEMENT',
    instruction: 'Route the cables behind the tray and add ties. This is what separates a good build from a great one.',
    slots: () => [],
    isComplete: (b) => b.routedCables.length >= requiredCables(b).length,
    optional: true,
  },
  {
    id: 'power-on',
    label: 'FINAL — POWER ON',
    instruction: 'Everything is in. Press the power button.',
    slots: () => [],
    isComplete: (b) => b.state === 'POST' || b.state === 'BENCHMARK',
  },
];

export function stepById(id: StepId): BuildStep | undefined {
  return BUILD_STEPS.find((s) => s.id === id);
}

/** The first step that is not yet finished. */
export function currentStep(build: Build): BuildStep {
  return BUILD_STEPS.find((s) => !s.isComplete(build)) ?? BUILD_STEPS[BUILD_STEPS.length - 1];
}

export function stepProgress(build: Build): number {
  const done = BUILD_STEPS.filter((s) => s.isComplete(build)).length;
  return done / BUILD_STEPS.length;
}
