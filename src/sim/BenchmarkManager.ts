import {
  type Build,
  buildCpu,
  buildGpu,
  buildMobo,
  buildRam,
  buildStorage,
  totalRam,
  totalStorage,
} from './Build';
import { powerStability } from './PowerManager';
import { noiseDb, noiseLabel, thermalReport } from './ThermalManager';
import { cableReport } from './CableManager';

export type Grade = 'S+' | 'S' | 'A' | 'B' | 'C' | 'D';

export interface GameResult {
  id: string;
  name: string;
  fps: number;
  /** Preset the FPS figure was measured at. */
  preset: string;
}

export interface BenchmarkResult {
  cpuScore: number;
  gpuScore: number;
  ramScore: number;
  ssdScore: number;
  thermalScore: number;
  powerScore: number;
  overall: number;
  grade: Grade;
  cpuTemp: number;
  gpuTemp: number;
  caseTemp: number;
  noise: number;
  noiseLabel: string;
  cableScore: number;
  cableLabel: string;
  games: GameResult[];
  totalRamGb: number;
  totalStorageGb: number;
}

/** Fictional test titles (§38), each weighted differently CPU vs GPU. */
const GAMES = [
  { id: 'cyber-arena', name: 'Cyber Arena', gpuWeight: 0.62, cpuWeight: 0.38, base: 210 },
  { id: 'galactic-warfare', name: 'Galactic Warfare', gpuWeight: 0.78, cpuWeight: 0.22, base: 155 },
  { id: 'blockworld', name: 'BlockWorld', gpuWeight: 0.3, cpuWeight: 0.7, base: 420 },
  { id: 'racing-x', name: 'Racing X', gpuWeight: 0.55, cpuWeight: 0.45, base: 240 },
];

export function runBenchmark(build: Build): BenchmarkResult {
  const cpu = buildCpu(build);
  const gpu = buildGpu(build);
  const mobo = buildMobo(build);
  const ram = buildRam(build);
  const storage = buildStorage(build);
  const thermal = thermalReport(build);
  const cables = cableReport(build);
  const stability = powerStability(build);

  // Thermal throttling scales the whole result — this is where ignoring the
  // cooler warning finally costs the player.
  const cpuThermalFactor = thermal.cpuThrottling ? 0.72 : 1 - Math.max(0, (thermal.cpuTemp - 75) / 100);
  const gpuThermalFactor = thermal.gpuThrottling ? 0.75 : 1 - Math.max(0, (thermal.gpuTemp - 72) / 100);

  const cpuScore = cpu
    ? Math.round(
        (cpu.singleCore * 55 + cpu.multiCore * 65 + cpu.boostClock * 260) *
          cpuThermalFactor *
          stability
      )
    : 0;

  const gpuScore = gpu
    ? Math.round((gpu.performance * 165 + gpu.vram * 90) * gpuThermalFactor * stability)
    : cpu?.integratedGraphics
      ? Math.round(cpu.singleCore * 22)
      : 0;

  // Memory: capacity, speed, latency, and whether dual-channel is actually live.
  const dualChannel = build.parts.filter((p) => p.slot.startsWith('ram-')).length >= 2;
  const ramSpeed = ram[0]?.speed ?? 0;
  const ramLatency = ram[0]?.latency ?? 40;
  const effectiveSpeed = mobo ? Math.min(ramSpeed, mobo.maxRamSpeed) : ramSpeed;
  const ramScore = ram.length
    ? Math.round(
        (effectiveSpeed * 0.85 + totalRam(build) * 45 + (50 - ramLatency) * 30) *
          (dualChannel ? 1 : 0.72)
      )
    : 0;

  const fastest = storage.reduce((best, s) => Math.max(best, s.readSpeed), 0);
  const ssdScore = storage.length
    ? Math.round(fastest * 0.72 + Math.min(totalStorage(build), 8000) * 0.4)
    : 0;

  const powerScore = Math.round(stability * 100);

  // Weighted blend, normalised into a readable four-to-five-digit number.
  const overall = Math.round(
    cpuScore * 0.3 +
      gpuScore * 0.42 +
      ramScore * 0.1 +
      ssdScore * 0.08 +
      thermal.thermalScore * 22 +
      cables.score * 6
  );

  const games: GameResult[] = GAMES.map((g) => {
    const gpuPart = gpu ? gpu.performance : cpu?.integratedGraphics ? 12 : 0;
    const cpuPart = cpu ? (cpu.singleCore * 0.7 + cpu.multiCore * 0.3) : 0;
    const strength = (gpuPart * g.gpuWeight + cpuPart * g.cpuWeight) / 100;
    const fps = Math.max(
      0,
      Math.round(g.base * Math.pow(strength, 1.15) * gpuThermalFactor * stability)
    );
    return { id: g.id, name: g.name, fps, preset: presetFor(fps) };
  });

  const db = noiseDb(build);

  return {
    cpuScore,
    gpuScore,
    ramScore,
    ssdScore,
    thermalScore: thermal.thermalScore,
    powerScore,
    overall,
    grade: gradeFor(overall),
    cpuTemp: thermal.cpuTemp,
    gpuTemp: thermal.gpuTemp,
    caseTemp: thermal.caseTemp,
    noise: db,
    noiseLabel: noiseLabel(db),
    cableScore: cables.score,
    cableLabel: cables.label,
    games,
    totalRamGb: totalRam(build),
    totalStorageGb: totalStorage(build),
  };
}

function presetFor(fps: number): string {
  if (fps >= 240) return 'ULTRA 1440p';
  if (fps >= 144) return 'HIGH 1440p';
  if (fps >= 90) return 'HIGH 1080p';
  if (fps >= 55) return 'MEDIUM 1080p';
  if (fps >= 30) return 'LOW 1080p';
  return 'UNPLAYABLE';
}

export function gradeFor(overall: number): Grade {
  if (overall >= 26000) return 'S+';
  if (overall >= 20000) return 'S';
  if (overall >= 14000) return 'A';
  if (overall >= 9000) return 'B';
  if (overall >= 5000) return 'C';
  return 'D';
}

export const GRADE_COLORS: Record<Grade, string> = {
  'S+': '#ffd23f',
  S: '#00e5a0',
  A: '#4fd1ff',
  B: '#8b9bb4',
  C: '#c98a3f',
  D: '#ff5566',
};
