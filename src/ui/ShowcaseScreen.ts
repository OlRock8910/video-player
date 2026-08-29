import * as THREE from 'three';
import type { Screen, UIManager } from './UIManager';
import { chip, el, iconButton } from './dom';
import { settings } from '../core/Settings';
import { game } from '../core/GameManager';
import { PC_STAND_X, PC_STAND_Z, type SceneRoot } from '../scene/SceneRoot';
import type { BuildScene } from '../scene/BuildScene';

type View = 'orbit' | 'front' | 'side' | 'internal' | 'top' | 'gpu' | 'cables';

const VIEWS: { id: View; label: string }[] = [
  { id: 'orbit', label: 'Orbit' },
  { id: 'front', label: 'Front' },
  { id: 'side', label: 'Side' },
  { id: 'internal', label: 'Internal' },
  { id: 'top', label: 'Top' },
  { id: 'gpu', label: 'GPU' },
  { id: 'cables', label: 'Cables' },
];

/** Showcase mode — UI out of the way, camera on rails (§33). */
export class ShowcaseScreen implements Screen {
  readonly id = 'showcase' as const;
  private view: View = 'orbit';
  private detachFrame: (() => void) | null = null;
  private bar!: HTMLElement;

  constructor(
    private scene: SceneRoot,
    private ui: UIManager,
    private getBuildScene: () => BuildScene
  ) {}

  render(root: HTMLElement): void {
    this.bar = el(
      'div',
      { class: 'showcase-bar' },
      VIEWS.map((v) => chip(v.label, this.view === v.id, () => this.setView(v.id)))
    );

    root.append(
      el(
        'div',
        {
          class: 'topbar',
          style: { background: 'transparent' },
        },
        [iconButton('‹', () => this.ui.back(), 'Back')]
      ),
      this.bar
    );
  }

  private setView(view: View): void {
    this.view = view;
    const bs = this.getBuildScene();
    const layout = bs.caseLayout;
    const size = layout ? Math.max(layout.height, layout.depth) : 5;
    const centre = new THREE.Vector3(PC_STAND_X, size * 0.46, PC_STAND_Z);
    const cam = this.scene.cameraController;

    switch (view) {
      case 'orbit':
        cam.frame(centre, size, { phi: Math.PI * 0.44, margin: 1.25 });
        break;
      case 'front':
        cam.frame(centre, size, { theta: 0, phi: Math.PI * 0.5, margin: 1.1 });
        break;
      case 'side':
        cam.frame(centre, size, { theta: Math.PI * 0.5, phi: Math.PI * 0.48, margin: 1.1 });
        break;
      case 'internal':
        cam.frame(bs.worldAnchor('mobo-tray'), 3.2, { theta: Math.PI * 0.5, phi: Math.PI * 0.5 });
        break;
      case 'top':
        cam.frame(centre, size, { phi: 0.22, margin: 1.15 });
        break;
      case 'gpu':
        cam.frame(bs.worldAnchor('pcie-0'), 2.6, { theta: Math.PI * 0.42, phi: Math.PI * 0.52 });
        break;
      case 'cables':
        cam.frame(bs.worldAnchor('psu-bay'), 3.0, { theta: Math.PI * 0.62, phi: Math.PI * 0.46 });
        break;
    }
    // Re-render the bar so the active chip updates.
    this.ui.refresh();
  }

  onEnter(): void {
    this.scene.setWorkshopVisible(true);
    this.scene.setDesk(game.deskId);
    const bs = this.getBuildScene();
    bs.setPowered(true);
    this.scene.cameraController.setLimits(1.2, 26);
    this.setView('orbit');

    this.detachFrame = this.scene.onFrame((dt) => {
      if (this.view === 'orbit' && !settings.get().reduceMotion) {
        this.scene.cameraController.orbitBy(dt * 0.16);
      }
    });
  }

  onExit(): void {
    this.detachFrame?.();
    this.detachFrame = null;
  }
}
