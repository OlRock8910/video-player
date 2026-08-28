import { settings } from './Settings';
import { save } from './SaveManager';
import { game } from './GameManager';
import { bus, toast } from './EventBus';
import type { SceneRoot } from '../scene/SceneRoot';
import type { UIManager } from '../ui/UIManager';
import { ALL_COMPONENTS, requireComponent } from '../data/catalog';
import { createBuild, recomputeState, screwsFor } from '../sim/Build';
import { runBenchmark } from '../sim/BenchmarkManager';
import { runPost } from '../sim/PostManager';
import type { Slot } from '../data/types';

/**
 * Developer mode (§55). Off for normal players; when enabled in Settings it
 * shows a performance readout and exposes tools on `window.pcb`.
 */
export function installDebugTools(
  scene: SceneRoot,
  ui: UIManager,
  buildSceneProbe: () => {
    panelScrewsRemoved: boolean;
    panelScrews: readonly { progress: number }[];
    remove: (slot: Slot) => void;
  } | null = () => null
): void {
  let overlay: HTMLDivElement | null = null;
  let detach: (() => void) | null = null;

  const enable = (): void => {
    if (overlay) return;
    overlay = document.createElement('div');
    overlay.id = 'debug';
    document.body.append(overlay);

    let accum = 0;
    detach = scene.onFrame((dt) => {
      accum += dt;
      if (accum < 0.25 || !overlay) return;
      accum = 0;
      const info = scene.renderer.info;
      overlay.textContent = [
        `fps   ${scene.fps.toFixed(0)}`,
        `res   ${(scene.resolution * 100).toFixed(0)}%`,
        `draws ${info.render.calls}`,
        `tris  ${info.render.triangles.toLocaleString('en-US')}`,
        `geo   ${info.memory.geometries}`,
        `state ${game.current.state}`,
        `parts ${game.current.parts.length}`,
      ].join('\n');
    });
  };

  const disable = (): void => {
    detach?.();
    detach = null;
    overlay?.remove();
    overlay = null;
  };

  const sync = (): void => {
    if (settings.get().debugMode) enable();
    else disable();
  };
  bus.on('settings:change', sync);
  sync();

  /** A complete, valid mid-range build used by the debug shortcuts. */
  const fillBuild = (): void => {
    const b = createBuild('Debug Build');
    b.panelRemoved = true;
    b.standoffsPlaced = 9;
    b.paste = 'good';
    const parts: [string, Slot][] = [
      ['case-lumen-flow', 'case'],
      ['mb-vb-b5', 'mobo-tray'],
      ['cpu-cf-c5', 'cpu-socket'],
      ['cool-cf-aio240', 'cooler-mount'],
      ['ram-gm-32-d5', 'ram-1'],
      ['ram-gm-32-d5', 'ram-3'],
      ['ssd-ff-1t', 'm2-0'],
      ['psu-am-850', 'psu-bay'],
      ['gpu-ps-s5', 'pcie-0'],
      ['fan-glow-120', 'fan-front-0'],
      ['fan-glow-120', 'fan-rear-0'],
    ];
    for (const [id, slot] of parts) {
      const c = requireComponent(id);
      const n = screwsFor(c);
      b.parts.push({
        componentId: id,
        slot,
        screwsDriven: n,
        screwsRequired: n,
        ...(c.category === 'fan'
          ? { airflow: slot.includes('front') ? ('intake' as const) : ('exhaust' as const) }
          : {}),
      });
    }
    b.connectedCables = ['atx24', 'eps8', 'pcie8', 'cpu-fan', 'front-panel', 'pump-power', 'rgb-header'];
    b.routedCables = [...b.connectedCables];
    b.cableTies = 5;
    b.state = recomputeState(b);
    game.current = b;
    game.persistBuild();
  };

  const api = {
    /** Give the player money. */
    addMoney: (n = 5000): void => {
      game.addMoney(n);
      toast(`+$${n}`, 'good');
    },
    /** Give reputation, which also unlocks parts and jobs. */
    addRep: (n = 200): void => game.addReputation(n),
    /** Put one of every component in the inventory. */
    unlockAll: (): void => {
      save.update((d) => {
        for (const c of ALL_COMPONENTS) d.inventory.push(c.id);
      });
      toast('All parts added to inventory', 'good');
    },
    /** Spawn a specific component into the inventory. */
    spawn: (id: string): void => {
      requireComponent(id);
      save.update((d) => d.inventory.push(id));
      toast(`Spawned ${id}`, 'good');
    },
    /** Remove a component from the current build. */
    deleteFrom: (slot: Slot): void => {
      game.current.parts = game.current.parts.filter((p) => p.slot !== slot);
      game.current.state = recomputeState(game.current);
      // The mesh has to go too, or the model and the scene disagree.
      buildSceneProbe()?.remove(slot);
      game.persistBuild();
      ui.refresh();
    },
    /** Jump straight to a finished, valid build. */
    completeBuild: (): void => {
      fillBuild();
      toast('Debug build loaded', 'good', 'Reopen the workshop to see it.');
    },
    /** Run the power-on check without the cinematic. */
    testPost: (): unknown => {
      const r = runPost(game.current);
      console.table(r.lines);
      toast(r.success ? 'POST OK' : `POST FAIL: ${r.failure}`, r.success ? 'good' : 'error');
      return r;
    },
    /** Run the benchmark on the current build. */
    testBenchmark: (): unknown => {
      const r = runBenchmark(game.current);
      console.log(r);
      toast(`${r.overall.toLocaleString()} — ${r.grade}`, 'info');
      return r;
    },
    /** Force a specific failure so the diagnostics can be checked. */
    forceError: (kind: 'no-power' | 'no-display' | 'overheating'): void => {
      const b = game.current;
      if (kind === 'no-power') b.connectedCables = b.connectedCables.filter((c) => c !== 'atx24');
      if (kind === 'no-display') b.connectedCables = b.connectedCables.filter((c) => c !== 'pcie8');
      if (kind === 'overheating') b.connectedCables = b.connectedCables.filter((c) => c !== 'cpu-fan');
      game.persistBuild();
      toast(`Forced ${kind}`, 'warn');
    },
    /** Wipe the current build without touching career progress. */
    resetBuild: (): void => {
      game.newBuild('Debug Build', game.mode);
      ui.refresh();
      toast('Build reset', 'info');
    },
    /** Every component id, for use with spawn(). */
    listComponents: (): string[] => ALL_COMPONENTS.map((c) => c.id),
    /**
     * Whether every side-panel thumbscrew has been backed out. Exposed so the
     * playthrough test can assert the case-opening sequence really happened
     * rather than inferring it from the step label.
     */
    panelScrewsRemoved: (): boolean | null => buildSceneProbe()?.panelScrewsRemoved ?? null,
    /** Whether one specific panel thumbscrew has been backed all the way out. */
    panelScrewOut: (index: number): boolean =>
      (buildSceneProbe()?.panelScrews?.[index]?.progress ?? 1) <= 0.02,
    scene,
    game,
    save,
  };

  (window as unknown as { pcb: typeof api }).pcb = api;
}
