import {
  type Build,
  buildCooler,
  buildCpu,
  buildGpu,
  buildRam,
  buildStorage,
  missingCables,
  totalRam,
  totalStorage,
} from './Build';
import { powerStability } from './PowerManager';
import { thermalReport } from './ThermalManager';

export type PostLineStatus = 'OK' | 'FAIL' | 'WARN';

export interface PostLine {
  label: string;
  value: string;
  status: PostLineStatus;
}

export type FailureKind = 'none' | 'no-power' | 'no-display' | 'no-boot-device' | 'overheating' | 'unstable';

export interface Diagnostic {
  kind: FailureKind;
  title: string;
  /** Checklist the player is given when it fails (§23). */
  checks: string[];
  /** Slot to highlight in the diagnostic view. */
  focus?: string;
}

export interface PostResult {
  success: boolean;
  lines: PostLine[];
  failure: FailureKind;
  diagnostic?: Diagnostic;
}

/**
 * Runs the fake POST (§22). Failures are always explainable — the checklist
 * points at the thing the player actually got wrong.
 */
export function runPost(build: Build): PostResult {
  const cpu = buildCpu(build);
  const gpu = buildGpu(build);
  const cooler = buildCooler(build);
  const ram = buildRam(build);
  const storage = buildStorage(build);
  const missing = missingCables(build);
  const thermal = thermalReport(build);
  const stability = powerStability(build);

  const lines: PostLine[] = [];
  let failure: FailureKind = 'none';

  /* ---- no power --------------------------------------------------- */
  const noPower =
    !build.parts.some((p) => p.slot === 'psu-bay') ||
    missing.includes('atx24') ||
    missing.includes('eps8') ||
    missing.includes('front-panel');

  if (noPower) {
    return {
      success: false,
      lines: [{ label: 'POWER', value: 'NO RESPONSE', status: 'FAIL' }],
      failure: 'no-power',
      diagnostic: {
        kind: 'no-power',
        title: 'Nothing happens when you press the button',
        checks: [
          'Is the PSU switch on the back set to I?',
          'Is the 24-pin ATX connector fully seated in the board?',
          'Is the CPU EPS (8-pin) cable connected at the top of the board?',
          'Is the front-panel power switch header wired to the right pins?',
        ],
        focus: missing.includes('atx24') ? 'cable-atx24' : missing.includes('eps8') ? 'cable-eps8' : 'psu-bay',
      },
    };
  }

  lines.push({ label: 'CPU', value: cpu ? cpu.name : 'NOT DETECTED', status: cpu ? 'OK' : 'FAIL' });
  if (!cpu) failure = 'no-power';

  const ramGb = totalRam(build);
  lines.push({
    label: 'MEMORY',
    value: ram.length ? `${ramGb} GB @ ${ram[0].speed} MT/s` : 'NOT DETECTED',
    status: ram.length ? 'OK' : 'FAIL',
  });
  if (!ram.length) failure = 'no-display';

  /* ---- no display ------------------------------------------------- */
  const gpuNeedsPower = gpu && (gpu.powerConnectors ?? 0) > 0 && missing.includes('pcie8');
  const hasOutput = !!gpu || !!cpu?.integratedGraphics;
  if (gpuNeedsPower || !hasOutput) {
    lines.push({ label: 'GPU', value: gpu ? 'NO POWER' : 'NOT DETECTED', status: 'FAIL' });
    return {
      success: false,
      lines,
      failure: 'no-display',
      diagnostic: {
        kind: 'no-display',
        title: 'Fans spin, but the monitor stays black',
        checks: [
          gpu
            ? `Is the ${gpu.powerConnectors}x 8-pin PCIe power cable plugged into the card?`
            : 'Is a graphics card installed, or does the CPU have integrated graphics?',
          'Is the card fully seated — did the PCIe latch click?',
          'Is the memory pushed all the way down on both ends?',
          'Is the display cable in the GPU, not the motherboard I/O?',
        ],
        focus: gpuNeedsPower ? 'cable-pcie8' : 'pcie-0',
      },
    };
  }
  lines.push({ label: 'GPU', value: gpu ? gpu.name : 'INTEGRATED', status: 'OK' });

  /* ---- storage ---------------------------------------------------- */
  const storageLive = storage.filter((s) => {
    if (s.kind === 'm2') return true;
    return !missing.includes('sata-power') && !missing.includes('sata-data');
  });
  lines.push({
    label: 'STORAGE',
    value: storageLive.length ? formatCapacity(totalStorage(build)) : 'NO BOOT DEVICE',
    status: storageLive.length ? 'OK' : 'FAIL',
  });
  if (!storageLive.length) {
    return {
      success: false,
      lines,
      failure: 'no-boot-device',
      diagnostic: {
        kind: 'no-boot-device',
        title: 'POST passes, then "No boot device found"',
        checks: [
          'Is a drive installed at all?',
          'For a SATA drive: are both the data and power cables connected?',
          'Is the M.2 drive screwed down flat, not sitting at an angle?',
        ],
        focus: 'm2-0',
      },
    };
  }

  /* ---- cooling ---------------------------------------------------- */
  const coolingFail = !cooler || missing.includes('cpu-fan') || thermal.cpuTemp >= 100;
  lines.push({
    label: 'COOLING',
    value: coolingFail ? 'CPU FAN ERROR' : `${thermal.cpuTemp}°C`,
    status: coolingFail ? 'FAIL' : thermal.cpuTemp > 85 ? 'WARN' : 'OK',
  });
  if (coolingFail) {
    return {
      success: false,
      lines,
      failure: 'overheating',
      diagnostic: {
        kind: 'overheating',
        title: 'The system shuts down seconds after POST',
        checks: [
          'Is the CPU cooler mounted and evenly tightened?',
          'Was thermal paste applied before the cooler went on?',
          'Is the CPU fan cable plugged into the CPU_FAN header?',
          'Do the case fans move air through the case, not against each other?',
        ],
        focus: 'cooler-mount',
      },
    };
  }

  /* ---- power ------------------------------------------------------ */
  const powerOk = stability >= 0.6;
  lines.push({
    label: 'POWER',
    value: powerOk ? (stability >= 0.9 ? 'STABLE' : 'MARGINAL') : 'UNSTABLE',
    status: powerOk ? (stability >= 0.9 ? 'OK' : 'WARN') : 'FAIL',
  });
  if (!powerOk) {
    return {
      success: false,
      lines,
      failure: 'unstable',
      diagnostic: {
        kind: 'unstable',
        title: 'The machine resets under load',
        checks: [
          'Does the PSU wattage cover the estimated system draw?',
          'Are the GPU power leads on separate cables rather than daisy-chained?',
          'Is the EPS connector fully seated?',
        ],
        focus: 'psu-bay',
      },
    };
  }

  if (failure !== 'none') return { success: false, lines, failure };
  return { success: true, lines, failure: 'none' };
}

export function formatCapacity(gb: number): string {
  if (gb >= 1000) return `${(gb / 1000).toFixed(gb % 1000 === 0 ? 0 : 1)} TB`;
  return `${gb} GB`;
}
