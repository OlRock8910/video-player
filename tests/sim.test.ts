import { describe, expect, it } from 'vitest';
import { requireComponent } from '../src/data/catalog';
import type { Slot } from '../src/data/types';
import {
  type Build,
  createBuild,
  isBuildComplete,
  recomputeState,
  screwsFor,
  totalRam,
} from '../src/sim/Build';
import { auditBuild, canInstall } from '../src/sim/CompatibilityManager';
import { powerBreakdown, recommendedPsuWattage } from '../src/sim/PowerManager';
import { thermalReport, noiseDb } from '../src/sim/ThermalManager';
import { cableReport } from '../src/sim/CableManager';
import { runBenchmark } from '../src/sim/BenchmarkManager';
import { runPost } from '../src/sim/PostManager';

/** Force a part into a build, bypassing validation — test fixture only. */
function place(build: Build, id: string, slot: Slot, airflow?: 'intake' | 'exhaust'): Build {
  const comp = requireComponent(id);
  const screws = screwsFor(comp);
  build.parts.push({
    componentId: id,
    slot,
    ...(airflow ? { airflow } : {}),
    screwsDriven: screws,
    screwsRequired: screws,
  });
  return build;
}

/** A complete, sensible mid-range machine used as the happy-path fixture. */
function goodBuild(): Build {
  const b = createBuild('Test Rig');
  b.panelRemoved = true;
  b.standoffsPlaced = 9;
  place(b, 'case-lumen-flow', 'case');
  place(b, 'mb-vb-b5', 'mobo-tray');
  place(b, 'cpu-cf-c5', 'cpu-socket');
  b.paste = 'good';
  place(b, 'cool-zd-tower', 'cooler-mount');
  place(b, 'ram-gm-32-d5', 'ram-0');
  place(b, 'ram-gm-32-d5', 'ram-1');
  place(b, 'ssd-ff-1t', 'm2-0');
  place(b, 'psu-am-850', 'psu-bay');
  place(b, 'gpu-ps-s5', 'pcie-0');
  place(b, 'fan-glow-120', 'fan-front-0', 'intake');
  place(b, 'fan-glow-120', 'fan-rear-0', 'exhaust');
  b.connectedCables = ['atx24', 'eps8', 'pcie8', 'cpu-fan', 'front-panel'];
  b.routedCables = ['atx24', 'eps8', 'pcie8', 'cpu-fan', 'front-panel'];
  b.cableTies = 4;
  return b;
}

describe('catalog', () => {
  it('resolves every component id it advertises', () => {
    expect(() => requireComponent('gpu-ps-t9')).not.toThrow();
    expect(() => requireComponent('nope')).toThrow();
  });
});

describe('compatibility engine', () => {
  it('blocks a CPU whose socket does not match the board', () => {
    const b = createBuild();
    b.standoffsPlaced = 9;
    place(b, 'case-vault-40', 'case');
    place(b, 'mb-vb-b5', 'mobo-tray'); // FG-1700
    const res = canInstall(b, requireComponent('cpu-nx-r7'), 'cpu-socket'); // NX-AM5
    expect(res.ok).toBe(false);
    expect(res.issues[0].title).toBe('CPU socket mismatch');
  });

  it('blocks a GPU that is longer than the case allows', () => {
    const b = createBuild();
    b.standoffsPlaced = 9;
    place(b, 'case-vault-40', 'case'); // 330mm clearance
    place(b, 'mb-vb-b5', 'mobo-tray');
    const res = canInstall(b, requireComponent('gpu-ps-t9'), 'pcie-0'); // 412mm
    expect(res.ok).toBe(false);
    expect(res.issues.some((i) => i.title === 'GPU is too long')).toBe(true);
  });

  it('blocks a cooler that is taller than the case allows', () => {
    const b = createBuild();
    b.standoffsPlaced = 4;
    b.paste = 'good';
    place(b, 'case-shard-mini', 'case'); // 92mm clearance
    place(b, 'mb-vb-i3', 'mobo-tray');
    place(b, 'cpu-cf-c5', 'cpu-socket');
    const res = canInstall(b, requireComponent('cool-zd-tower'), 'cooler-mount'); // 162mm
    expect(res.ok).toBe(false);
    expect(res.issues.some((i) => i.title === 'Cooler is too tall')).toBe(true);
  });

  it('blocks an ATX board in an ITX case', () => {
    const b = createBuild();
    b.standoffsPlaced = 9;
    place(b, 'case-shard-mini', 'case');
    const res = canInstall(b, requireComponent('mb-vb-b5'), 'mobo-tray');
    expect(res.ok).toBe(false);
    expect(res.issues.some((i) => i.title === 'Motherboard too large')).toBe(true);
  });

  it('blocks a GPU needing more PCIe leads than the PSU has', () => {
    const b = createBuild();
    b.standoffsPlaced = 9;
    place(b, 'case-prism-x', 'case');
    place(b, 'mb-vb-b5', 'mobo-tray');
    place(b, 'psu-iv-450', 'psu-bay'); // 1x pcie8
    const res = canInstall(b, requireComponent('gpu-hf-f7'), 'pcie-0'); // needs 2
    expect(res.ok).toBe(false);
    expect(res.issues.some((i) => i.title === 'Not enough PCIe power')).toBe(true);
  });

  it('refuses to mount a cooler before paste is applied', () => {
    const b = createBuild();
    b.standoffsPlaced = 9;
    place(b, 'case-lumen-flow', 'case'); // 168mm clears the 162mm tower
    place(b, 'mb-vb-b5', 'mobo-tray');
    place(b, 'cpu-cf-c5', 'cpu-socket');
    expect(canInstall(b, requireComponent('cool-zd-tower'), 'cooler-mount').ok).toBe(false);
    b.paste = 'good';
    expect(canInstall(b, requireComponent('cool-zd-tower'), 'cooler-mount').ok).toBe(true);
  });

  it('requires standoffs before the motherboard', () => {
    const b = createBuild();
    place(b, 'case-vault-40', 'case');
    expect(canInstall(b, requireComponent('mb-vb-b5'), 'mobo-tray').ok).toBe(false);
    b.standoffsPlaced = 9;
    expect(canInstall(b, requireComponent('mb-vb-b5'), 'mobo-tray').ok).toBe(true);
  });

  it('allows a sub-optimal RAM layout but says so', () => {
    const b = createBuild();
    b.standoffsPlaced = 9;
    place(b, 'case-vault-40', 'case');
    place(b, 'mb-vb-b5', 'mobo-tray');
    place(b, 'ram-pr-16-d5', 'ram-0');
    const res = canInstall(b, requireComponent('ram-pr-16-d5'), 'ram-2');
    expect(res.ok).toBe(true);
    expect(res.issues.some((i) => i.severity === 'info')).toBe(true);
  });

  it('warns rather than blocks on unsupported RAM speed', () => {
    const b = createBuild();
    b.standoffsPlaced = 6;
    place(b, 'case-vault-40', 'case');
    place(b, 'mb-fb-h1', 'mobo-tray'); // DDR4-3200 max
    const res = canInstall(b, requireComponent('ram-dc-16-d4'), 'ram-0');
    expect(res.ok).toBe(true);
  });

  it('passes a whole sensible build', () => {
    expect(auditBuild(goodBuild()).ok).toBe(true);
  });

  it('fails the audit when a dedicated GPU is missing and the CPU has no iGPU', () => {
    const b = createBuild();
    b.standoffsPlaced = 9;
    b.paste = 'good';
    place(b, 'case-prism-x', 'case');
    place(b, 'mb-nx-x6', 'mobo-tray');
    place(b, 'cpu-nx-r7', 'cpu-socket'); // no iGPU
    place(b, 'cool-zd-tower', 'cooler-mount');
    place(b, 'ram-gm-32-d5', 'ram-0');
    place(b, 'ssd-ff-1t', 'm2-0');
    place(b, 'psu-am-850', 'psu-bay');
    const res = auditBuild(b);
    expect(res.ok).toBe(false);
    expect(res.issues.some((i) => i.title === 'No display output')).toBe(true);
  });
});

describe('power', () => {
  it('adds up the parts and recommends headroom', () => {
    const p = powerBreakdown(goodBuild());
    expect(p.cpu).toBe(88);
    expect(p.gpu).toBe(170);
    expect(p.total).toBe(p.cpu + p.gpu + p.motherboard + p.memory + p.storage + p.fans);
    expect(p.recommended).toBeGreaterThan(p.total);
  });

  it('rounds recommendations to real PSU sizes', () => {
    expect(recommendedPsuWattage(520)).toBe(650);
    expect(recommendedPsuWattage(100)).toBe(350);
  });
});

describe('thermals and noise', () => {
  it('runs cooler with good paste than with none', () => {
    const good = goodBuild();
    const dry = { ...goodBuild(), paste: 'none' as const };
    expect(thermalReport(good).cpuTemp).toBeLessThan(thermalReport(dry).cpuTemp);
  });

  it('penalises fans that all blow the same way', () => {
    const balanced = goodBuild();
    const oneWay = goodBuild();
    for (const p of oneWay.parts) if (p.airflow) p.airflow = 'intake';
    expect(thermalReport(oneWay).airflow.score).toBeLessThan(thermalReport(balanced).airflow.score);
  });

  it('is quieter in a sound-damped case with premium fans', () => {
    const loud = goodBuild();
    const quiet = createBuild();
    quiet.paste = 'good';
    place(quiet, 'case-hush-700', 'case');
    place(quiet, 'mb-vb-b5', 'mobo-tray');
    place(quiet, 'cpu-cf-c5', 'cpu-socket');
    place(quiet, 'cool-zd-tower', 'cooler-mount');
    place(quiet, 'ram-pr-16-d5', 'ram-0');
    place(quiet, 'ssd-ff-1t', 'm2-0');
    place(quiet, 'psu-am-1000', 'psu-bay');
    place(quiet, 'fan-silent-140', 'fan-front-0', 'intake');
    place(quiet, 'fan-silent-140', 'fan-rear-0', 'exhaust');
    expect(noiseDb(quiet)).toBeLessThan(noiseDb(loud));
  });

  it('reports a hot build when the cooler is far too small', () => {
    const b = goodBuild();
    b.parts = b.parts.filter((p) => p.slot !== 'cooler-mount' && p.slot !== 'cpu-socket');
    place(b, 'cpu-vc-t16', 'cpu-socket'); // 230W
    place(b, 'cool-zd-breeze', 'cooler-mount'); // 95W rating
    const t = thermalReport(b);
    expect(t.cpuTemp).toBeGreaterThan(90);
  });
});

describe('cable management', () => {
  it('scores a fully routed modular build highly', () => {
    expect(cableReport(goodBuild()).score).toBeGreaterThanOrEqual(85);
  });

  it('scores an unrouted build as a nightmare', () => {
    const b = goodBuild();
    b.routedCables = [];
    b.cableTies = 0;
    const r = cableReport(b);
    expect(r.score).toBeLessThan(55);
    expect(r.notes.length).toBeGreaterThan(0);
  });
});

describe('POST', () => {
  it('boots a correct build', () => {
    const res = runPost(goodBuild());
    expect(res.success).toBe(true);
    expect(res.lines.every((l) => l.status !== 'FAIL')).toBe(true);
  });

  it('reports no power when the 24-pin is missing', () => {
    const b = goodBuild();
    b.connectedCables = b.connectedCables.filter((c) => c !== 'atx24');
    const res = runPost(b);
    expect(res.success).toBe(false);
    expect(res.failure).toBe('no-power');
    expect(res.diagnostic?.checks.length).toBeGreaterThan(2);
  });

  it('reports no display when GPU power is missing', () => {
    const b = goodBuild();
    b.connectedCables = b.connectedCables.filter((c) => c !== 'pcie8');
    const res = runPost(b);
    expect(res.failure).toBe('no-display');
  });

  it('reports a cooling fault when the CPU fan is unplugged', () => {
    const b = goodBuild();
    b.connectedCables = b.connectedCables.filter((c) => c !== 'cpu-fan');
    expect(runPost(b).failure).toBe('overheating');
  });

  it('reports no boot device when the only drive has no cables', () => {
    const b = goodBuild();
    b.parts = b.parts.filter((p) => p.slot !== 'm2-0');
    place(b, 'ssd-dc-sata-1t', 'drive-bay-0');
    const res = runPost(b);
    expect(res.failure).toBe('no-boot-device');
  });
});

describe('benchmark', () => {
  it('produces higher scores for stronger hardware', () => {
    const mid = runBenchmark(goodBuild());
    const strong = goodBuild();
    strong.parts = strong.parts.filter((p) => p.slot !== 'pcie-0' && p.slot !== 'cpu-socket');
    place(strong, 'cpu-qc-x9', 'cpu-socket');
    place(strong, 'gpu-nr-n9', 'pcie-0');
    const high = runBenchmark(strong);
    expect(high.gpuScore).toBeGreaterThan(mid.gpuScore);
    expect(high.overall).toBeGreaterThan(mid.overall);
  });

  it('reports playable frame rates for every test title', () => {
    const r = runBenchmark(goodBuild());
    expect(r.games).toHaveLength(4);
    for (const g of r.games) expect(g.fps).toBeGreaterThan(0);
  });

  it('penalises a single-channel memory layout', () => {
    const dual = runBenchmark(goodBuild());
    const single = goodBuild();
    single.parts = single.parts.filter((p) => p.slot !== 'ram-1');
    expect(runBenchmark(single).ramScore).toBeLessThan(dual.ramScore);
  });

  it('grades a legendary build above a budget one', () => {
    const budget = createBuild();
    budget.paste = 'good';
    place(budget, 'case-vault-40', 'case');
    place(budget, 'mb-fb-h1', 'mobo-tray');
    place(budget, 'cpu-cf-c3', 'cpu-socket');
    place(budget, 'cool-zd-breeze', 'cooler-mount');
    place(budget, 'ram-dc-16-d4', 'ram-0');
    place(budget, 'ssd-dc-500', 'm2-0');
    place(budget, 'psu-iv-450', 'psu-bay');
    expect(runBenchmark(budget).overall).toBeLessThan(runBenchmark(goodBuild()).overall);
  });
});

describe('build state machine', () => {
  it('never moves backwards', () => {
    const b = goodBuild();
    b.state = 'BUILD_COMPLETE';
    b.parts = b.parts.filter((p) => p.slot !== 'pcie-0');
    // Removing a part re-derives an earlier state, but a cinematic already
    // reached is never rewound.
    expect(recomputeState(b)).not.toBe('CASE_CLOSED');
  });

  it('reports completeness only when cables and screws are done', () => {
    const b = goodBuild();
    expect(isBuildComplete(b)).toBe(true);
    b.parts[1].screwsDriven = 0;
    expect(isBuildComplete(b)).toBe(false);
  });

  it('totals memory across sticks and slots', () => {
    expect(totalRam(goodBuild())).toBe(64); // 2 kits x 2 sticks x 16GB
  });
});
