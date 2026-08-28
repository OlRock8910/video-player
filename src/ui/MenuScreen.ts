import * as THREE from 'three';
import type { Screen, ScreenId, UIManager } from './UIManager';
import { button, el } from './dom';
import { game } from '../core/GameManager';
import { save } from '../core/SaveManager';
import { audio } from '../core/AudioManager';
import { settings } from '../core/Settings';
import { reputationLevel } from '../data/career';
import type { SceneRoot } from '../scene/SceneRoot';
import { BuildScene } from '../scene/BuildScene';
import { requireComponent } from '../data/catalog';
import { createBuild, defaultRgbProfile } from '../sim/Build';

/**
 * Animated main menu (§3). A finished machine sits on the bench with its fans
 * turning and lighting cycling while the camera makes a slow circuit.
 */
export class MenuScreen implements Screen {
  readonly id = 'menu' as const;

  private demo = new BuildScene();
  private detachFrame: (() => void) | null = null;
  private built = false;

  constructor(
    private scene: SceneRoot,
    private ui: UIManager
  ) {}

  /** A pre-assembled showpiece PC, built once and reused. */
  private buildDemo(): void {
    if (this.built) return;
    this.built = true;
    const demoBuild = createBuild('Showpiece');
    demoBuild.panelRemoved = false;
    demoBuild.rgb = { ...defaultRgbProfile(), mode: 'wave', speed: 0.35, brightness: 1 };

    const parts: [string, string][] = [
      ['case-prism-x', 'case'],
      ['mb-fb-z9', 'mobo-tray'],
      ['cpu-qc-x9', 'cpu-socket'],
      ['cool-cf-aio360', 'cooler-mount'],
      ['ram-gm-32-d5', 'ram-1'],
      ['ram-gm-32-d5', 'ram-3'],
      ['ssd-sv-2t', 'm2-0'],
      ['psu-am-1000', 'psu-bay'],
      ['gpu-nr-n9', 'pcie-0'],
      ['fan-cryo-140', 'fan-front-0'],
      ['fan-cryo-140', 'fan-front-1'],
      ['fan-cryo-140', 'fan-rear-0'],
    ];
    for (const [id, slot] of parts) {
      const comp = requireComponent(id);
      demoBuild.parts.push({
        componentId: comp.id,
        slot: slot as never,
        screwsDriven: 4,
        screwsRequired: 4,
      });
    }
    demoBuild.connectedCables = ['atx24', 'eps8', 'pcie8', 'cpu-fan', 'front-panel', 'pump-power', 'rgb-header'];
    demoBuild.routedCables = [...demoBuild.connectedCables];

    this.demo.restore(demoBuild);
    this.demo.setRgbProfile(demoBuild.rgb);
    this.demo.setPowered(true);
  }

  render(root: HTMLElement): void {
    const data = save.get();
    const level = reputationLevel(data.career.reputation);

    const buttons = el('div', { class: 'menu-buttons' });
    const add = (
      label: string,
      sub: string,
      icon: string,
      target: ScreenId | (() => void),
      opts: { primary?: boolean; disabled?: boolean } = {}
    ): void => {
      const b = button(
        label,
        () => {
          if (typeof target === 'function') target();
          else this.ui.show(target);
        },
        { icon, sub, class: opts.primary ? 'primary' : '' }
      );
      if (opts.disabled) b.disabled = true;
      b.style.animationDelay = `${buttons.childElementCount * 45}ms`;
      buttons.append(b);
    };

    const canContinue = game.hasBuildInProgress;
    if (canContinue) {
      add('Continue', game.current.name, '▶', () => this.ui.show('workshop'), { primary: true });
    }
    add('Career', 'Build PCs for paying customers', '💼', 'career', { primary: !canContinue });
    add('Free Build', 'Every part unlocked, no budget', '🔧', () => {
      game.newBuild('Free Build', 'free');
      this.ui.show('workshop');
    });
    add('Garage', `${data.savedPcs.length} saved build${data.savedPcs.length === 1 ? '' : 's'}`, '🏠', 'garage');
    add('Parts Shop', 'Buy components', '🛒', 'shop');
    add('Challenges', 'Special building challenges', '🏆', 'challenges');
    add('Settings', 'Graphics, audio, controls, haptics', '⚙', 'settings');

    root.append(
      el('div', { class: 'veil' }),
      el('div', { class: 'menu-brand' }, [
        el('div', { class: 'menu-title', text: 'PC BUILDER' }),
        el('div', { class: 'menu-sub', text: 'Build · Power · Perform' }),
      ]),
      buttons,
      el('div', { class: 'menu-footer' }, [
        el('span', { text: `${level.name} · ${data.career.reputation} rep` }),
        el('span', { class: 'gold', text: `$${Math.round(data.career.money).toLocaleString('en-US')}` }),
      ])
    );
  }

  onEnter(): void {
    this.buildDemo();
    // Added and removed rather than just hidden: an invisible object still
    // takes part in raycasting and matrix updates.
    this.scene.buildRoot.add(this.demo.root);
    this.scene.setWorkshopVisible(true);
    audio.playMusic('menu');
    this.scene.setMonitor(true, 0x081826);

    this.scene.cameraController.setLimits(4, 26);
    // Frame the whole machine, aimed a little low so it sits in the clear band
    // between the title and the button stack rather than behind them.
    const layout = this.demo.caseLayout;
    const size = layout ? Math.max(layout.height, layout.depth) : 5.2;
    this.scene.cameraController.frame(new THREE.Vector3(0, size * 0.06, 0), size, {
      phi: Math.PI * 0.46,
      margin: 1.5,
    });
    this.scene.cameraController.setLocked(true);

    this.detachFrame = this.scene.onFrame((dt, elapsed) => {
      this.demo.update(dt, elapsed);
      // Slow drift around the machine (§3).
      if (!settings.get().reduceMotion) this.scene.cameraController.orbitBy(dt * 0.085);
    });
  }

  onExit(): void {
    this.detachFrame?.();
    this.detachFrame = null;
    this.demo.root.removeFromParent();
    this.scene.cameraController.setLocked(false);
  }

  onBack(): boolean {
    // Nothing above the menu.
    return true;
  }
}
