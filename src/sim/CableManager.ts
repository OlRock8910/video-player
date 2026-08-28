import type { CableKind } from '../data/types';
import { type Build, buildCase, buildPsu, requiredCables } from './Build';

export interface CableReport {
  score: number;
  label: string;
  routedCount: number;
  totalCount: number;
  notes: string[];
}

export const CABLE_LABELS: Record<CableKind, string> = {
  atx24: '24-PIN ATX',
  eps8: 'CPU EPS',
  pcie8: 'GPU POWER',
  'sata-power': 'SATA POWER',
  'sata-data': 'SATA DATA',
  'cpu-fan': 'CPU FAN',
  'front-panel': 'FRONT PANEL',
  'rgb-header': 'ARGB HEADER',
  'pump-power': 'PUMP POWER',
};

/**
 * Cable management score (§18). Rewards routing behind the tray, using ties,
 * and picking a case with room to do it; a modular PSU means fewer spare leads
 * to hide.
 */
export function cableReport(build: Build): CableReport {
  const required = requiredCables(build);
  const total = required.length;
  const notes: string[] = [];

  if (total === 0) {
    return { score: 0, label: 'NOTHING TO ROUTE', routedCount: 0, totalCount: 0, notes };
  }

  const routed = required.filter((c) => build.routedCables.includes(c));
  const routedRatio = routed.length / total;

  const pcCase = buildCase(build);
  const psu = buildPsu(build);
  const routingRoom = pcCase?.cableRouting ?? 0.4;
  const modularBonus = psu ? (psu.modular === 'full' ? 1 : psu.modular === 'semi' ? 0.7 : 0.35) : 0.35;

  // Ties help, with diminishing returns past one per two cables.
  const tieTarget = Math.max(2, Math.ceil(total / 2));
  const tieRatio = Math.min(1, build.cableTies / tieTarget);

  const raw =
    routedRatio * 0.55 + routingRoom * 0.15 + modularBonus * 0.15 + tieRatio * 0.15;

  const score = Math.round(Math.max(0, Math.min(1, raw)) * 100);

  const loose = required.filter((c) => !build.routedCables.includes(c));
  if (loose.length > 0) {
    notes.push(`${loose.map((c) => CABLE_LABELS[c]).join(', ')} still draped across the board.`);
  }
  if (psu && psu.modular === 'none') notes.push('A non-modular PSU leaves unused leads to hide.');
  if (build.cableTies < tieTarget) notes.push(`Add ${tieTarget - build.cableTies} more cable tie(s).`);
  if (routingRoom < 0.4) notes.push('This case has very little room behind the tray.');
  if (score >= 95) notes.push('Not a single cable visible through the glass.');

  return { score, label: cableLabel(score), routedCount: routed.length, totalCount: total, notes };
}

export function cableLabel(score: number): string {
  if (score >= 95) return 'BEAUTIFUL';
  if (score >= 85) return 'EXCELLENT';
  if (score >= 70) return 'TIDY';
  if (score >= 55) return 'ACCEPTABLE';
  if (score >= 40) return 'MESSY';
  return 'CABLE NIGHTMARE';
}
