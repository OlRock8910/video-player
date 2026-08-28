import { type Build, buildCpu, buildGpu, buildMobo, partsOf } from './Build';
import { isFan, isPsu, isRam, isStorage } from '../data/types';

export interface PowerBreakdown {
  cpu: number;
  gpu: number;
  motherboard: number;
  memory: number;
  storage: number;
  fans: number;
  total: number;
  recommended: number;
  /** Installed PSU wattage, or 0. */
  supply: number;
  headroomPct: number;
}

/**
 * Estimated whole-system load (§20). Deliberately simple and readable — the
 * player should be able to do this maths in their head from the shop numbers.
 */
export function estimateLoadWatts(build: Build): number {
  return powerBreakdown(build).total;
}

export function powerBreakdown(build: Build): PowerBreakdown {
  const cpu = buildCpu(build);
  const gpu = buildGpu(build);
  const mobo = buildMobo(build);
  const parts = partsOf(build);

  const cpuW = cpu?.power ?? 0;
  const gpuW = gpu?.power ?? 0;
  const moboW = mobo?.power ?? 0;
  const memW = parts.filter(isRam).reduce((s, r) => s + r.power, 0);
  const storeW = parts.filter(isStorage).reduce((s, d) => s + d.power, 0);
  // Case fans plus whatever the cooler's own fans/pump draw.
  const fanW =
    parts.filter(isFan).reduce((s, f) => s + f.power, 0) +
    parts.filter((c) => c.category === 'cooler').reduce((s, c) => s + c.power, 0);

  const total = cpuW + gpuW + moboW + memW + storeW + fanW;
  const psu = parts.find(isPsu);
  const supply = psu?.wattage ?? 0;

  return {
    cpu: cpuW,
    gpu: gpuW,
    motherboard: moboW,
    memory: memW,
    storage: storeW,
    fans: fanW,
    total,
    recommended: recommendedPsuWattage(total),
    supply,
    headroomPct: supply > 0 ? Math.round(((supply - total) / supply) * 100) : 0,
  };
}

/** ~25% headroom, rounded up to the next common PSU size. */
export function recommendedPsuWattage(loadWatts: number): number {
  const target = loadWatts * 1.25;
  const sizes = [350, 450, 550, 650, 750, 850, 1000, 1200, 1300, 1600];
  return sizes.find((s) => s >= target) ?? 1600;
}

/**
 * How stable the machine is under load, 0-1. Below 1 the build can brown out
 * during the benchmark if the player ignored the warnings (§20).
 */
export function powerStability(build: Build): number {
  const b = powerBreakdown(build);
  if (b.supply === 0) return 0;
  const ratio = b.total / b.supply;
  if (ratio <= 0.8) return 1;
  if (ratio <= 0.95) return 0.9;
  if (ratio <= 1.0) return 0.6;
  return Math.max(0, 0.6 - (ratio - 1) * 2);
}
