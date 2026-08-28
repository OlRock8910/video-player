import { isFan } from '../data/types';
import {
  type Build,
  buildCase,
  buildCooler,
  buildCpu,
  buildGpu,
  partsOf,
} from './Build';

export interface AirflowReport {
  intakeCfm: number;
  exhaustCfm: number;
  /** 0-1 quality of the front-to-back airflow path. */
  score: number;
  balanced: boolean;
  notes: string[];
}

export interface ThermalReport {
  cpuTemp: number;
  gpuTemp: number;
  caseTemp: number;
  /** 0-100 headline number shown in the build report. */
  thermalScore: number;
  cpuThrottling: boolean;
  gpuThrottling: boolean;
  airflow: AirflowReport;
}

const AMBIENT = 22;

/**
 * Airflow quality (§17). Fans only help if some push in and some push out, and
 * a front-intake / rear-exhaust layout scores best.
 */
export function airflowReport(build: Build): AirflowReport {
  const pcCase = buildCase(build);
  const notes: string[] = [];
  let intakeCfm = 0;
  let exhaustCfm = 0;
  let directional = 0;

  for (const part of build.parts) {
    const comp = partsOf(build).find((c) => c.id === part.componentId);
    if (!comp || !isFan(comp)) continue;
    if (part.airflow === 'intake') {
      intakeCfm += comp.cfm;
      if (part.slot.startsWith('fan-front') || part.slot.startsWith('fan-bottom')) directional += 1;
      else directional -= 0.5;
    } else if (part.airflow === 'exhaust') {
      exhaustCfm += comp.cfm;
      if (part.slot.startsWith('fan-rear') || part.slot.startsWith('fan-top')) directional += 1;
      else directional -= 0.5;
    }
  }

  const fanCount = build.parts.filter((p) => p.slot.startsWith('fan-')).length;
  if (fanCount === 0) {
    notes.push('No case fans — the machine relies on the cooler alone.');
    return {
      intakeCfm: 0,
      exhaustCfm: 0,
      score: (pcCase?.airflowQuality ?? 0.4) * 0.5,
      balanced: false,
      notes,
    };
  }

  const balanced = intakeCfm > 0 && exhaustCfm > 0;
  if (!balanced) notes.push('All fans point the same way — air has nowhere to go.');

  const total = intakeCfm + exhaustCfm;
  // Best when intake slightly exceeds exhaust (positive pressure, less dust).
  const ratio = exhaustCfm > 0 ? intakeCfm / exhaustCfm : 0;
  const balanceScore = balanced ? 1 - Math.min(1, Math.abs(ratio - 1.15) / 1.5) : 0.25;
  const volumeScore = Math.min(1, total / 180);
  const layoutScore = Math.max(0, Math.min(1, directional / Math.max(1, fanCount)));
  const caseScore = pcCase?.airflowQuality ?? 0.5;

  if (balanced && ratio > 1.05 && ratio < 1.6) notes.push('Positive pressure — good for dust.');
  if (layoutScore < 0.6) notes.push('Fan directions fight the natural front-to-back path.');

  const score = clamp01(caseScore * 0.3 + balanceScore * 0.25 + volumeScore * 0.2 + layoutScore * 0.25);
  return { intakeCfm, exhaustCfm, score, balanced, notes };
}

/**
 * Believable, not scientific (§37). Heat in, cooling capacity out, airflow and
 * paste as multipliers.
 */
export function thermalReport(build: Build): ThermalReport {
  const cpu = buildCpu(build);
  const gpu = buildGpu(build);
  const cooler = buildCooler(build);
  const air = airflowReport(build);

  const pasteFactor =
    build.paste === 'good' ? 1 : build.paste === 'excessive' ? 0.95 : build.paste === 'sparse' ? 0.82 : 0.6;

  const caseTemp = AMBIENT + 3 + (1 - air.score) * 12 + ((cpu?.tdp ?? 0) + (gpu?.power ?? 0)) / 90;

  let cpuTemp = caseTemp;
  let cpuThrottling = false;
  if (cpu) {
    if (!cooler) {
      cpuTemp = 100;
      cpuThrottling = true;
    } else {
      const capacity = cooler.tdpRating * pasteFactor * (0.85 + air.score * 0.3);
      const ratio = cpu.tdp / Math.max(1, capacity);
      cpuTemp = caseTemp + 18 + ratio * 52;
      cpuThrottling = cpuTemp >= 95;
    }
  }

  let gpuTemp = caseTemp;
  let gpuThrottling = false;
  if (gpu) {
    const capacity = (gpu.coolingCapacity / 100) * 420 * (0.8 + air.score * 0.4);
    const ratio = gpu.power / Math.max(1, capacity);
    gpuTemp = caseTemp + 14 + ratio * 55;
    gpuThrottling = gpuTemp >= 90;
  }

  // 100 when everything sits cool; falls away as parts approach their limits.
  const cpuHealth = cpu ? clamp01((95 - cpuTemp) / 45) : 1;
  const gpuHealth = gpu ? clamp01((90 - gpuTemp) / 40) : 1;
  const thermalScore = Math.round(clamp01(cpuHealth * 0.5 + gpuHealth * 0.35 + air.score * 0.15) * 100);

  return {
    cpuTemp: round1(cpuTemp),
    gpuTemp: round1(gpuTemp),
    caseTemp: round1(caseTemp),
    thermalScore,
    cpuThrottling,
    gpuThrottling,
    airflow: air,
  };
}

/** Noise model (§36): fan count, quality, and how hard they have to work. */
export function noiseDb(build: Build): number {
  const pcCase = buildCase(build);
  const cooler = buildCooler(build);
  const gpu = buildGpu(build);
  const psu = partsOf(build).find((c) => c.category === 'psu');
  const fans = partsOf(build).filter(isFan);
  const thermal = thermalReport(build);

  // Load factor: hot builds spin fans up.
  const load = clamp01((thermal.cpuTemp - 45) / 45) * 0.6 + clamp01((thermal.gpuTemp - 45) / 45) * 0.4;

  const sources: number[] = [];
  for (const f of fans) sources.push(f.noise * (0.72 + load * 0.4));
  if (cooler) sources.push(cooler.noise * (0.72 + load * 0.45));
  if (gpu) sources.push(gpu.noise * (0.6 + load * 0.55));
  if (psu && psu.category === 'psu') sources.push(psu.noise * (0.55 + load * 0.4));
  if (partsOf(build).some((c) => c.category === 'storage' && 'kind' in c && c.kind === 'hdd')) {
    sources.push(28);
  }
  if (sources.length === 0) return 0;

  // Logarithmic summing, then case damping.
  const summed = 10 * Math.log10(sources.reduce((s, db) => s + Math.pow(10, db / 10), 0));
  const damping = (pcCase?.soundDamping ?? 0) * 7;
  return Math.max(16, round1(summed - damping));
}

export function noiseLabel(db: number): string {
  if (db < 24) return 'INAUDIBLE';
  if (db < 30) return 'WHISPER QUIET';
  if (db < 36) return 'QUIET';
  if (db < 43) return 'AUDIBLE';
  if (db < 50) return 'LOUD';
  return 'JET ENGINE';
}

const clamp01 = (v: number) => Math.max(0, Math.min(1, v));
const round1 = (v: number) => Math.round(v * 10) / 10;
