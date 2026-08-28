import type { Component, Slot } from '../data/types';
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
import {
  type Build,
  buildCase,
  buildCooler,
  buildCpu,
  buildGpu,
  buildMobo,
  buildPsu,
  buildRam,
  componentInSlot,
  partsOf,
  ramSlotsUsed,
} from './Build';
import { estimateLoadWatts, recommendedPsuWattage } from './PowerManager';

export type IssueSeverity = 'blocker' | 'warning' | 'info';

export interface CompatibilityIssue {
  severity: IssueSeverity;
  /** Short headline shown in the toast, e.g. "CPU socket mismatch". */
  title: string;
  /** One-line explanation with the actual numbers in it. */
  detail: string;
  /** Which slot/part the UI should highlight (§50). */
  focus?: Slot | 'cable-pcie8' | 'cable-eps8' | 'cable-atx24';
}

export interface CompatibilityResult {
  ok: boolean;
  issues: CompatibilityIssue[];
}

const blocker = (title: string, detail: string, focus?: CompatibilityIssue['focus']): CompatibilityIssue => ({
  severity: 'blocker',
  title,
  detail,
  focus,
});
const warn = (title: string, detail: string, focus?: CompatibilityIssue['focus']): CompatibilityIssue => ({
  severity: 'warning',
  title,
  detail,
  focus,
});
const info = (title: string, detail: string): CompatibilityIssue => ({
  severity: 'info',
  title,
  detail,
});

/**
 * Can this component go into this slot in this build right now?
 *
 * Blockers describe physically impossible builds and are refused (§19).
 * Warnings describe builds that work but are a bad idea, and are allowed —
 * the player gets to make their own mistakes and find out at benchmark time.
 */
export function canInstall(build: Build, component: Component, slot: Slot): CompatibilityResult {
  const issues: CompatibilityIssue[] = [];
  const pcCase = buildCase(build);
  const mobo = buildMobo(build);

  if (componentInSlot(build, slot)) {
    issues.push(blocker('Slot occupied', `Something is already installed in ${slotLabel(slot)}.`, slot));
    return { ok: false, issues };
  }

  /* ---- case ------------------------------------------------------- */
  if (isCase(component)) {
    // Cases are always installable; they define the build.
  }

  /* ---- motherboard ------------------------------------------------ */
  if (isMobo(component)) {
    if (!pcCase) {
      issues.push(blocker('No case', 'Place a case on the bench before mounting a motherboard.'));
    } else if (!pcCase.supportedFormFactors.includes(component.formFactor)) {
      issues.push(
        blocker(
          'Motherboard too large',
          `${component.formFactor} does not fit ${pcCase.name} (supports ${pcCase.supportedFormFactors.join(', ')}).`,
          'mobo-tray'
        )
      );
    }
    if (build.standoffsPlaced < standoffsNeeded(component.formFactor)) {
      issues.push(
        blocker(
          'Standoffs missing',
          `Install ${standoffsNeeded(component.formFactor)} standoffs first — ${build.standoffsPlaced} placed.`,
          'mobo-tray'
        )
      );
    }
  }

  /* ---- cpu -------------------------------------------------------- */
  if (isCpu(component)) {
    if (!mobo) {
      issues.push(blocker('No motherboard', 'Install the motherboard before the CPU.', 'mobo-tray'));
    } else {
      if (mobo.socket !== component.socket) {
        issues.push(
          blocker(
            'CPU socket mismatch',
            `${component.name} is ${component.socket}; ${mobo.name} has ${mobo.socket}.`,
            'cpu-socket'
          )
        );
      }
      if (component.tdp > mobo.vrmWattage) {
        issues.push(
          warn(
            'VRM under-specced',
            `${component.name} draws ${component.tdp}W but ${mobo.name} is rated for ${mobo.vrmWattage}W. Expect throttling.`,
            'cpu-socket'
          )
        );
      }
    }
  }

  /* ---- cooler ----------------------------------------------------- */
  if (isCooler(component)) {
    const cpu = buildCpu(build);
    if (!cpu) {
      issues.push(blocker('No CPU', 'Install and seat the CPU before mounting a cooler.', 'cpu-socket'));
    } else if (!component.sockets.includes(cpu.socket)) {
      issues.push(
        blocker(
          'Cooler bracket mismatch',
          `${component.name} has no ${cpu.socket} mounting hardware.`,
          'cooler-mount'
        )
      );
    }
    if (build.paste === 'none' && component.coolerType !== 'stock') {
      issues.push(blocker('No thermal paste', 'Apply thermal paste to the CPU before fitting the cooler.', 'cpu-socket'));
    }
    if (pcCase) {
      if (component.coolerType !== 'aio' && component.height > pcCase.coolerClearance) {
        issues.push(
          blocker(
            'Cooler is too tall',
            `${component.name} is ${component.height}mm; ${pcCase.name} clears ${pcCase.coolerClearance}mm.`,
            'cooler-mount'
          )
        );
      }
      if (component.coolerType === 'aio' && !pcCase.radiatorSupport.includes(component.radiatorSize)) {
        issues.push(
          blocker(
            'Radiator does not fit',
            `${pcCase.name} takes ${pcCase.radiatorSupport.join('/')}mm radiators, not ${component.radiatorSize}mm.`,
            'cooler-mount'
          )
        );
      }
    }
    if (cpu && component.tdpRating < cpu.tdp) {
      issues.push(
        warn(
          'Cooler under-specced',
          `${component.name} handles ${component.tdpRating}W; ${cpu.name} produces ${cpu.tdp}W. It will run hot.`,
          'cooler-mount'
        )
      );
    }
    // Tall memory under a big air tower.
    const tallRam = buildRam(build).find((r) => r.height > 40);
    if (tallRam && component.coolerType === 'tower' && component.height > 150) {
      issues.push(
        warn('Memory clearance tight', `${tallRam.name} stands ${tallRam.height}mm tall and will foul the cooler fan.`)
      );
    }
  }

  /* ---- ram -------------------------------------------------------- */
  if (isRam(component)) {
    if (!mobo) {
      issues.push(blocker('No motherboard', 'Install the motherboard before the memory.', 'mobo-tray'));
    } else {
      if (mobo.ramType !== component.ramType) {
        issues.push(
          blocker(
            'Memory type mismatch',
            `${component.name} is ${component.ramType}; ${mobo.name} takes ${mobo.ramType}.`,
            slot
          )
        );
      }
      const slotNumber = Number(slot.split('-')[1] ?? 0);
      if (slotNumber >= mobo.ramSlots) {
        issues.push(
          blocker('No such slot', `${mobo.name} only has ${mobo.ramSlots} DIMM slots.`, slot)
        );
      }
      if (component.speed > mobo.maxRamSpeed) {
        issues.push(
          warn(
            'RAM speed unsupported',
            `${component.name} runs at ${component.speed} MT/s; ${mobo.name} officially tops out at ${mobo.maxRamSpeed}. It will clock down.`,
            slot
          )
        );
      }
      // Dual-channel guidance — never blocks the install (§13).
      const used = ramSlotsUsed(build);
      if (mobo.ramSlots === 4 && used.length === 1) {
        const first = Number(used[0].split('-')[1]);
        const target = slotNumber;
        const sameChannel = first % 2 === target % 2;
        if (sameChannel) {
          issues.push(
            info(
              'Single-channel layout',
              'This configuration will work, but dual-channel performance may be reduced. Try slots 2 and 4.'
            )
          );
        }
      }
    }
  }

  /* ---- storage ---------------------------------------------------- */
  if (isStorage(component)) {
    if (!mobo) {
      issues.push(blocker('No motherboard', 'Install the motherboard before storage.', 'mobo-tray'));
    } else if (component.kind === 'm2') {
      const idx = Number(slot.split('-')[1] ?? 0);
      if (!slot.startsWith('m2-')) {
        issues.push(blocker('Wrong mount', 'M.2 drives go in an M.2 slot on the board.', slot));
      } else if (idx >= mobo.m2Slots) {
        issues.push(blocker('No M.2 slot', `${mobo.name} has ${mobo.m2Slots} M.2 slot(s).`, slot));
      }
    } else {
      if (!slot.startsWith('drive-bay-')) {
        issues.push(blocker('Wrong mount', 'SATA drives mount in a drive bay.', slot));
      } else if (pcCase) {
        const idx = Number(slot.split('-')[2] ?? 0);
        if (idx >= pcCase.driveBays) {
          issues.push(blocker('No drive bay', `${pcCase.name} has ${pcCase.driveBays} bay(s).`, slot));
        }
      }
      const psu = buildPsu(build);
      const sataDrives = partsOf(build).filter((c) => isStorage(c) && c.kind !== 'm2').length;
      if (psu && sataDrives + 1 > psu.connectors.sata) {
        issues.push(
          warn('Not enough SATA power', `${psu.name} provides ${psu.connectors.sata} SATA leads.`, 'psu-bay')
        );
      }
    }
  }

  /* ---- gpu -------------------------------------------------------- */
  if (isGpu(component)) {
    if (!mobo) {
      issues.push(blocker('No motherboard', 'Install the motherboard before the graphics card.', 'mobo-tray'));
    }
    if (pcCase && component.dimensions.length > pcCase.gpuClearance) {
      issues.push(
        blocker(
          'GPU is too long',
          `${component.name} is ${component.dimensions.length}mm; ${pcCase.name} clears ${pcCase.gpuClearance}mm.`,
          'pcie-0'
        )
      );
    }
    const psu = buildPsu(build);
    if (psu && component.powerConnectors > psu.connectors.pcie8) {
      issues.push(
        blocker(
          'Not enough PCIe power',
          `This GPU requires ${component.powerConnectors} 8-pin power connector(s); ${psu.name} provides ${psu.connectors.pcie8}.`,
          'cable-pcie8'
        )
      );
    }
    if (psu) {
      const load = estimateLoadWatts({ ...build, parts: [...build.parts, synthetic(component, slot)] });
      if (load > psu.wattage) {
        issues.push(
          blocker(
            'PSU does not provide enough power',
            `Estimated load ${Math.round(load)}W exceeds the ${psu.wattage}W supply.`,
            'psu-bay'
          )
        );
      } else if (psu.wattage < component.recommendedPsu) {
        issues.push(
          warn(
            'PSU below recommendation',
            `${component.name} recommends a ${component.recommendedPsu}W supply; you have ${psu.wattage}W.`,
            'psu-bay'
          )
        );
      }
    }
  }

  /* ---- psu -------------------------------------------------------- */
  if (isPsu(component)) {
    if (!pcCase) {
      issues.push(blocker('No case', 'Place a case before installing a power supply.'));
    } else if (component.depth > pcCase.psuClearance) {
      issues.push(
        blocker(
          'PSU does not fit',
          `${component.name} is ${component.depth}mm deep; ${pcCase.name} allows ${pcCase.psuClearance}mm.`,
          'psu-bay'
        )
      );
    }
    const load = estimateLoadWatts(build);
    if (load > 0 && component.wattage < load) {
      issues.push(
        warn(
          'PSU capacity may be insufficient',
          `Parts already installed draw about ${Math.round(load)}W. Recommended: ${recommendedPsuWattage(load)}W.`,
          'psu-bay'
        )
      );
    }
  }

  /* ---- fans ------------------------------------------------------- */
  if (isFan(component)) {
    if (!pcCase) {
      issues.push(blocker('No case', 'Fans mount to the case.'));
    } else {
      const mount = fanMountOf(slot);
      if (!mount) {
        issues.push(blocker('Not a fan mount', `${slotLabel(slot)} does not take a fan.`, slot));
      } else {
        const spec = pcCase.fanMounts[mount];
        if (spec.count === 0) {
          issues.push(blocker('No mount here', `${pcCase.name} has no ${mount} fan mount.`, slot));
        } else if (!spec.sizes.includes(component.size)) {
          issues.push(
            blocker(
              'Fan size unsupported',
              `The ${mount} mount takes ${spec.sizes.join('/')}mm fans, not ${component.size}mm.`,
              slot
            )
          );
        }
        const index = Number(slot.split('-').pop() ?? 0);
        if (index >= spec.count) {
          issues.push(blocker('No mount here', `${pcCase.name} fits ${spec.count} ${mount} fan(s).`, slot));
        }
      }
    }
  }

  return { ok: !issues.some((i) => i.severity === 'blocker'), issues };
}

/** A throwaway InstalledPart used for "what if I added this" power maths. */
function synthetic(component: Component, slot: Slot) {
  return { componentId: component.id, slot, screwsDriven: 0, screwsRequired: 0 };
}

export function standoffsNeeded(formFactor: string): number {
  return formFactor === 'ITX' ? 4 : formFactor === 'mATX' ? 6 : 9;
}

export function fanMountOf(slot: Slot): 'front' | 'rear' | 'top' | 'bottom' | undefined {
  if (slot.startsWith('fan-front')) return 'front';
  if (slot.startsWith('fan-rear')) return 'rear';
  if (slot.startsWith('fan-top')) return 'top';
  if (slot.startsWith('fan-bottom')) return 'bottom';
  return undefined;
}

export function slotLabel(slot: Slot): string {
  const labels: Partial<Record<Slot, string>> = {
    case: 'the bench',
    'mobo-tray': 'the motherboard tray',
    'cpu-socket': 'the CPU socket',
    'cooler-mount': 'the cooler mount',
    'pcie-0': 'the primary PCIe slot',
    'psu-bay': 'the PSU bay',
  };
  if (labels[slot]) return labels[slot] as string;
  if (slot.startsWith('ram-')) return `DIMM slot ${Number(slot.split('-')[1]) + 1}`;
  if (slot.startsWith('m2-')) return `M.2 slot ${Number(slot.split('-')[1]) + 1}`;
  if (slot.startsWith('drive-bay-')) return `drive bay ${Number(slot.split('-')[2]) + 1}`;
  if (slot.startsWith('fan-')) return `the ${fanMountOf(slot)} fan mount`;
  return slot;
}

/**
 * Whole-build audit used by the pre-power-on check and the shop's
 * "will this work with my build?" badges.
 */
export function auditBuild(build: Build): CompatibilityResult {
  const issues: CompatibilityIssue[] = [];
  const pcCase = buildCase(build);
  const mobo = buildMobo(build);
  const cpu = buildCpu(build);
  const cooler = buildCooler(build);
  const gpu = buildGpu(build);
  const psu = buildPsu(build);

  if (!pcCase) issues.push(blocker('No case', 'Every build needs a chassis.'));
  if (!mobo) issues.push(blocker('No motherboard', 'Nothing to plug into yet.'));
  if (!cpu) issues.push(blocker('No CPU', 'The system cannot POST without a processor.', 'cpu-socket'));
  if (!cooler) issues.push(blocker('No CPU cooler', 'The CPU will overheat immediately.', 'cooler-mount'));
  if (buildRam(build).length === 0) issues.push(blocker('No memory', 'Install at least one DIMM.', 'ram-0'));
  if (!psu) issues.push(blocker('No power supply', 'Nothing to power the board.', 'psu-bay'));

  if (cpu && !gpu && !cpu.integratedGraphics) {
    issues.push(
      blocker('No display output', `${cpu.name} has no integrated graphics — this build needs a GPU.`, 'pcie-0')
    );
  }

  if (psu) {
    const load = estimateLoadWatts(build);
    if (load > psu.wattage) {
      issues.push(
        blocker('PSU overloaded', `Estimated ${Math.round(load)}W load on a ${psu.wattage}W supply.`, 'psu-bay')
      );
    } else if (load > psu.wattage * 0.85) {
      issues.push(
        warn(
          'PSU capacity may be insufficient',
          `Load is ${Math.round(load)}W of ${psu.wattage}W. Recommended: ${recommendedPsuWattage(load)}W.`,
          'psu-bay'
        )
      );
    }
  }

  if (build.paste === 'none' && cooler && cooler.coolerType !== 'stock') {
    issues.push(warn('No thermal paste', 'Temperatures will be much higher than they should be.', 'cpu-socket'));
  }

  const intake = build.parts.filter((p) => p.airflow === 'intake').length;
  const exhaust = build.parts.filter((p) => p.airflow === 'exhaust').length;
  if (intake + exhaust > 0 && (intake === 0 || exhaust === 0)) {
    issues.push(warn('Airflow is one-way', 'Every fan pushes the same direction — add an opposing fan.'));
  }

  return { ok: !issues.some((i) => i.severity === 'blocker'), issues };
}
