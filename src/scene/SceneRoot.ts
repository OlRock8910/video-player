import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { CameraController } from './CameraController';
import { geo, mats, mm } from './Materials';
import { settings, type QualityProfile } from '../core/Settings';
import { DEFAULT_DESK_ID, getDesk, type Desk } from '../data/desks';

/**
 * Where the PC stands on the bench. Kept slightly left of centre and forward of
 * the monitors so the machine is the subject and the tools sit around it rather
 * than behind it.
 */
export const PC_STAND_X = -1.1;
export const PC_STAND_Z = 0.9;

/**
 * Owns the renderer, the workshop environment and the frame loop (§4, §40).
 *
 * Dynamic resolution: the loop watches a rolling frame time and scales the
 * render buffer between 55% and 100% to hold the target frame rate, which is
 * the cheapest way to keep a mid-range Android phone at 60.
 */
export class SceneRoot {
  readonly scene = new THREE.Scene();
  readonly renderer: THREE.WebGLRenderer;
  readonly cameraController: CameraController;
  readonly canvas: HTMLCanvasElement;

  /** Everything that belongs to the workbench environment. */
  readonly workshop = new THREE.Group();
  /** Parts and the case live here so a scene reset is one removal. */
  readonly buildRoot = new THREE.Group();

  private clock = new THREE.Clock();
  private frameCb: ((dt: number, elapsed: number) => void)[] = [];
  private raf = 0;
  private running = false;

  private resolutionScale = 1;
  private frameAccum = 0;
  private frameCount = 0;
  private lastFps = 60;

  private envMap: THREE.Texture | null = null;
  private deskGroup = new THREE.Group();
  private deskId = DEFAULT_DESK_ID;
  private keyLight!: THREE.DirectionalLight;
  private accentLights: THREE.PointLight[] = [];
  private profile: QualityProfile;

  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    this.profile = settings.profile();

    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: this.profile.antialias,
      powerPreference: 'high-performance',
      alpha: false,
      stencil: false,
    });
    this.renderer.setClearColor(0x05070a, 1);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.2;

    this.scene.fog = new THREE.FogExp2(0x05070a, 0.014);
    this.scene.add(this.workshop, this.buildRoot);

    // Metal and glass are almost black without something to reflect. A tiny
    // generated room gives every PBR material an environment to sample, which
    // is what makes the chassis read as brushed steel rather than a void.
    const pmrem = new THREE.PMREMGenerator(this.renderer);
    this.envMap = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    this.scene.environment = this.envMap;
    this.scene.environmentIntensity = 0.85;
    pmrem.dispose();

    this.cameraController = new CameraController(canvas, this.aspect());
    this.buildEnvironment();
    this.buildLighting();
    this.applyQuality();

    window.addEventListener('resize', this.handleResize);
    // Android WebViews fire this on rotation before `resize` settles.
    window.addEventListener('orientationchange', () => setTimeout(this.handleResize, 120));
  }

  private aspect(): number {
    const r = this.canvas.getBoundingClientRect();
    return Math.max(0.2, r.width / Math.max(1, r.height));
  }

  /* ---------------------------------------------------------------- */
  /* Environment (§4)                                                  */
  /* ---------------------------------------------------------------- */

  private buildEnvironment(): void {
    // Everything the desk owns lives under one group so swapping desks is a
    // single removal and rebuild, not a scene-wide edit.
    this.deskGroup = new THREE.Group();
    this.deskGroup.name = 'desk';
    this.workshop.add(this.deskGroup);
    this.buildDesk(getDesk(this.deskId));
  }

  /** Rebuild the bench for a given desk (§4). Safe to call at any time. */
  buildDesk(desk: Desk): void {
    this.deskId = desk.id;
    const w = this.deskGroup;
    disposeChildren(w);

    const halfW = desk.width / 2;
    const halfD = desk.depth / 2;
    // The PC stands on the mat; everything else is arranged around it, and the
    // build area sits forward of centre so tools have room behind.
    const buildZ = PC_STAND_Z;

    /* ---- desk top and legs ------------------------------------------ */
    const top = new THREE.Mesh(
      geo.box(desk.width, 0.22, desk.depth),
      new THREE.MeshStandardMaterial({
        color: desk.topColor,
        roughness: desk.gloss,
        metalness: desk.topColor === 0x23262b ? 0.35 : 0.05,
      })
    );
    top.position.y = -0.11;
    top.receiveShadow = true;
    w.add(top);

    // A thin front lip reads as a real edge rather than a floating slab.
    const lip = new THREE.Mesh(geo.box(desk.width, 0.06, 0.08), mats.plastic(0x0d1014, 0.8));
    lip.position.set(0, -0.2, halfD);
    w.add(lip);

    for (const [lx, lz] of [
      [-halfW + 0.8, -halfD + 0.8],
      [halfW - 0.8, -halfD + 0.8],
      [-halfW + 0.8, halfD - 0.8],
      [halfW - 0.8, halfD - 0.8],
    ]) {
      const leg = new THREE.Mesh(
        geo.box(0.28, desk.height * 2, 0.28),
        new THREE.MeshStandardMaterial({ color: desk.legColor, roughness: 0.5, metalness: 0.6 })
      );
      leg.position.set(lx, -desk.height, lz);
      w.add(leg);
    }

    // Under-desk lighting on the nicer benches.
    if (desk.ledStrip) {
      const stripMat = new THREE.MeshBasicMaterial({ color: desk.ledColor, transparent: true, opacity: 0.85 });
      const strip = new THREE.Mesh(geo.box(desk.width * 0.9, 0.05, 0.05), stripMat);
      strip.position.set(0, -0.3, halfD - 0.25);
      w.add(strip);
      const glow = new THREE.PointLight(desk.ledColor, 9, 9, 2);
      glow.position.set(0, -0.9, halfD - 0.6);
      w.add(glow);
    }

    /* ---- anti-static mat under the build area ----------------------- */
    const matW = 8.4 * desk.matScale;
    const matD = 5.6 * desk.matScale;
    const mat = new THREE.Mesh(
      geo.box(matW, 0.03, matD),
      new THREE.MeshStandardMaterial({ color: desk.matColor, roughness: 0.95 })
    );
    mat.position.set(PC_STAND_X, 0.015, buildZ);
    mat.receiveShadow = true;
    w.add(mat);
    const stripe = new THREE.Mesh(geo.box(matW, 0.035, 0.12), mats.plastic(0x2f6fa8, 0.9));
    stripe.position.set(PC_STAND_X, 0.018, buildZ + matD / 2 - 0.12);
    w.add(stripe);

    /* ---- bench props, arranged around the machine ------------------- */
    const trayGroup = new THREE.Group();
    trayGroup.add(new THREE.Mesh(geo.box(1.5, 0.05, 1.1), mats.plastic(0x1b1f26)));
    for (const [sx, sz, sw, sd] of [
      [0.75, 0, 0.06, 1.1],
      [-0.75, 0, 0.06, 1.1],
      [0, 0.55, 1.5, 0.06],
      [0, -0.55, 1.5, 0.06],
    ]) {
      const wall = new THREE.Mesh(geo.box(sw, 0.22, sd), mats.plastic(0x252a32));
      wall.position.set(sx, 0.11, sz);
      trayGroup.add(wall);
    }
    trayGroup.position.set(PC_STAND_X + 3.2, 0.05, buildZ + 1.5);
    trayGroup.name = 'screw-tray';
    w.add(trayGroup);

    const driver = new THREE.Group();
    const handle = new THREE.Mesh(geo.cylinder(0.13, 0.15, 0.9, 12), mats.plastic(0xd94f2b, 0.6));
    handle.rotation.z = Math.PI / 2;
    const shaft = new THREE.Mesh(geo.cylinder(0.035, 0.035, 1.1, 8), mats.aluminium(0xa8b0bb));
    shaft.rotation.z = Math.PI / 2;
    shaft.position.x = -1;
    const tip = new THREE.Mesh(new THREE.ConeGeometry(0.05, 0.14, 6), mats.aluminium(0x6d7580));
    tip.rotation.z = Math.PI / 2;
    tip.position.x = -1.6;
    driver.add(handle, shaft, tip);
    driver.position.set(PC_STAND_X - 2.6, 0.18, buildZ + 1.9);
    driver.rotation.y = 0.4;
    driver.name = 'screwdriver';
    w.add(driver);

    const paste = new THREE.Group();
    const tube = new THREE.Mesh(geo.cylinder(0.11, 0.11, 0.7, 10), mats.plastic(0xc8ccd2, 0.5));
    tube.rotation.z = Math.PI / 2.2;
    const nozzle = new THREE.Mesh(new THREE.ConeGeometry(0.06, 0.2, 8), mats.plastic(0x2b3038));
    nozzle.rotation.z = -Math.PI / 2.2;
    nozzle.position.set(0.42, 0.16, 0);
    paste.add(tube, nozzle);
    paste.position.set(PC_STAND_X - 2.9, 0.12, buildZ + 1.0);
    paste.name = 'paste-tube';
    w.add(paste);

    for (let i = 0; i < 3; i++) {
      const box = new THREE.Mesh(
        geo.box(1.5, 0.5, 1.05),
        mats.plastic([0x1d2733, 0x2a1d33, 0x1d3329][i], 0.9)
      );
      box.position.set(-halfW + 1.9 + (i % 2) * 0.2, 0.25 + i * 0.52, buildZ - 1.4 + i * 0.12);
      box.rotation.y = 0.2 - i * 0.14;
      box.castShadow = true;
      w.add(box);
      const label = new THREE.Mesh(geo.box(0.9, 0.24, 0.02), mats.plastic(0xd8dde4, 0.95));
      label.position.set(box.position.x, box.position.y, box.position.z + 0.54);
      label.rotation.y = box.rotation.y;
      w.add(label);
    }

    const manual = new THREE.Mesh(geo.box(1.1, 0.05, 1.5), mats.plastic(0xdfe4ea, 0.95));
    manual.position.set(PC_STAND_X + 3.4, 0.04, buildZ - 0.9);
    manual.rotation.y = -0.3;
    w.add(manual);
    for (let i = 0; i < 5; i++) {
      const tie = new THREE.Mesh(geo.box(0.5, 0.012, 0.04), mats.plastic(0x0b0d10, 0.9));
      tie.position.set(PC_STAND_X + 3.0 + Math.random() * 0.3, 0.03, buildZ + 0.6 + i * 0.07);
      tie.rotation.y = Math.random() * 0.6;
      w.add(tie);
    }

    /* ---- monitors and keyboard, pushed to the back ------------------ */
    const screenMat = new THREE.MeshStandardMaterial({
      color: 0x05070a,
      emissive: 0x000000,
      emissiveIntensity: 1,
      roughness: 0.3,
    });
    this.monitorMaterial = screenMat;

    for (let i = 0; i < desk.monitors; i++) {
      const monitor = new THREE.Group();
      const panel = new THREE.Mesh(geo.box(5.6, 3.3, 0.12), mats.plastic(0x0a0c10, 0.4));
      panel.position.y = 2.4;
      // Only the primary monitor shows POST; the second is a dim side panel.
      const thisMat = i === 0 ? screenMat : mats.plastic(0x0b1118, 0.4);
      const screen = new THREE.Mesh(geo.plane(5.3, 3.0), thisMat);
      screen.position.set(0, 2.4, 0.07);
      if (i === 0) screen.name = 'monitor-screen';
      const stand = new THREE.Mesh(geo.box(0.3, 1.4, 0.3), mats.plastic(0x14171c));
      stand.position.y = 0.9;
      const base = new THREE.Mesh(geo.box(2.0, 0.1, 1.0), mats.plastic(0x14171c));
      base.position.y = 0.3;
      monitor.add(panel, screen, stand, base);
      // Primary dead ahead; a second angles in from the right.
      monitor.position.set(i === 0 ? 0 : 5.6, 0, -halfD + 1.0);
      monitor.rotation.y = i === 0 ? 0 : -0.42;
      monitor.name = i === 0 ? 'monitor' : 'monitor-secondary';
      w.add(monitor);
    }

    const kb = new THREE.Mesh(geo.box(4.4, 0.16, 1.5), mats.plastic(0x0f1216));
    kb.position.set(0, 0.08, -halfD + 2.6);
    kb.castShadow = true;
    w.add(kb);
    for (let r = 0; r < 4; r++) {
      for (let k = 0; k < 16; k++) {
        const key = new THREE.Mesh(geo.box(0.2, 0.05, 0.2), mats.plastic(0x1c2026, 0.8));
        key.position.set(-2 + k * 0.26, 0.17, -halfD + 2.1 + r * 0.26);
        w.add(key);
      }
    }

    /* ---- pegboard of tools on the better benches -------------------- */
    if (desk.pegboard) {
      const board = new THREE.Mesh(geo.box(7.5, 3.4, 0.1), mats.plastic(0x1b2027, 0.9));
      board.position.set(-halfW + 4.6, 2.6, -halfD + 0.35);
      w.add(board);
      for (let i = 0; i < 7; i++) {
        const tool = new THREE.Mesh(
          geo.box(0.16, 1.0 + (i % 3) * 0.35, 0.16),
          mats.aluminium([0xb9c0cb, 0xd94f2b, 0x8c949e][i % 3])
        );
        tool.position.set(-halfW + 1.9 + i * 0.78, 2.9, -halfD + 0.48);
        w.add(tool);
      }
    }

    // Backdrop wall so the scene is not floating in void.
    const wall = new THREE.Mesh(geo.plane(48, 26), mats.plastic(0x0a0d12, 1));
    wall.position.set(0, 6, -halfD - 7);
    w.add(wall);
  }

  /** Emissive material of the desk monitor, driven by the POST screen. */
  monitorMaterial!: THREE.MeshStandardMaterial;

  private buildLighting(): void {
    // A dark game still needs enough light to read shapes on a phone screen.
    const ambient = new THREE.AmbientLight(0x6c7f9c, 1.0);
    this.scene.add(ambient);

    const hemi = new THREE.HemisphereLight(0x8fb0d8, 0x141a22, 1.15);
    this.scene.add(hemi);

    this.keyLight = new THREE.DirectionalLight(0xe8f2ff, 2.7);
    this.keyLight.position.set(6, 11, 6);
    this.keyLight.target.position.set(0, 1.5, 0);
    this.scene.add(this.keyLight, this.keyLight.target);

    // Cool rim from behind, warm fill from the side — the premium dark look.
    const rim = new THREE.DirectionalLight(0x5aa8ff, 1.5);
    rim.position.set(-7, 5, -8);
    this.scene.add(rim);

    const fill = new THREE.DirectionalLight(0xffb070, 0.85);
    fill.position.set(-6, 3, 7);
    this.scene.add(fill);

    // A soft overhead bounce so the inside of the case is never pitch black.
    // Sits just above the chassis with a short range, so it fills the inside of
    // the case without washing out the desk top underneath it.
    const interior = new THREE.PointLight(0xbfd8ff, 14, 6.5, 2.1);
    interior.position.set(PC_STAND_X + 0.9, 3.4, PC_STAND_Z + 0.5);
    this.scene.add(interior);

    // Accent point lights, dropped on lower quality.
    const accentColors = [0x00d0ff, 0xff2f8a, 0x9d7bff, 0x00e5a0, 0xffd23f, 0x3d8bff];
    for (let i = 0; i < accentColors.length; i++) {
      const p = new THREE.PointLight(accentColors[i], 0, 9, 2);
      const a = (i / accentColors.length) * Math.PI * 2;
      p.position.set(Math.cos(a) * 6, 1.2 + (i % 3) * 1.4, Math.sin(a) * 5);
      this.accentLights.push(p);
      this.scene.add(p);
    }
  }

  applyQuality(): void {
    this.profile = settings.profile();
    const p = this.profile;

    this.renderer.shadowMap.enabled = p.shadows;
    this.renderer.shadowMap.type = p.shadowMapSize >= 2048 ? THREE.PCFSoftShadowMap : THREE.PCFShadowMap;
    this.keyLight.castShadow = p.shadows;
    if (p.shadows) {
      this.keyLight.shadow.mapSize.set(p.shadowMapSize, p.shadowMapSize);
      this.keyLight.shadow.camera.near = 0.5;
      this.keyLight.shadow.camera.far = 40;
      const s = 12;
      const cam = this.keyLight.shadow.camera as THREE.OrthographicCamera;
      cam.left = -s;
      cam.right = s;
      cam.top = s;
      cam.bottom = -s;
      cam.updateProjectionMatrix();
      this.keyLight.shadow.bias = -0.0012;
      this.keyLight.shadow.normalBias = 0.02;
    }

    for (let i = 0; i < this.accentLights.length; i++) {
      this.accentLights[i].intensity = i < p.accentLights ? 2.4 : 0;
    }

    this.renderer.toneMappingExposure = p.bloom ? 1.26 : 1.14;
    this.handleResize();
  }

  private handleResize = (): void => {
    const r = this.canvas.getBoundingClientRect();
    const width = Math.max(1, Math.floor(r.width));
    const height = Math.max(1, Math.floor(r.height));
    const dpr = Math.min(window.devicePixelRatio || 1, this.profile.maxPixelRatio);
    this.renderer.setPixelRatio(dpr * this.resolutionScale);
    this.renderer.setSize(width, height, false);
    this.cameraController.resize(width / height);
  };

  onFrame(cb: (dt: number, elapsed: number) => void): () => void {
    this.frameCb.push(cb);
    return () => {
      const i = this.frameCb.indexOf(cb);
      if (i >= 0) this.frameCb.splice(i, 1);
    };
  }

  start(): void {
    if (this.running) return;
    this.running = true;
    this.clock.start();
    const loop = (): void => {
      if (!this.running) return;
      this.raf = requestAnimationFrame(loop);
      const dt = Math.min(0.05, this.clock.getDelta());
      const elapsed = this.clock.elapsedTime;

      this.cameraController.update(dt);
      for (const cb of this.frameCb) {
        try {
          cb(dt, elapsed);
        } catch (err) {
          console.error('[frame] callback threw', err);
        }
      }

      this.renderer.render(this.scene, this.cameraController.camera);
      this.trackPerformance(dt);
    };
    this.raf = requestAnimationFrame(loop);
  }

  stop(): void {
    this.running = false;
    cancelAnimationFrame(this.raf);
    this.clock.stop();
  }

  /** Dynamic resolution (§40). */
  private trackPerformance(dt: number): void {
    if (!settings.get().dynamicResolution) return;
    this.frameAccum += dt;
    this.frameCount += 1;
    if (this.frameAccum < 0.5) return;

    this.lastFps = this.frameCount / this.frameAccum;
    this.frameAccum = 0;
    this.frameCount = 0;

    const target = settings.get().targetFps;
    const prev = this.resolutionScale;
    if (this.lastFps < target * 0.88) {
      this.resolutionScale = Math.max(0.55, this.resolutionScale - 0.08);
    } else if (this.lastFps > target * 0.98 && this.resolutionScale < 1) {
      this.resolutionScale = Math.min(1, this.resolutionScale + 0.04);
    }
    if (Math.abs(prev - this.resolutionScale) > 0.001) this.handleResize();
  }

  get fps(): number {
    return this.lastFps;
  }

  get resolution(): number {
    return this.resolutionScale;
  }

  /** Swap the bench for a different one the player has bought. */
  setDesk(id: string): void {
    if (id === this.deskId) return;
    this.buildDesk(getDesk(id));
  }

  get activeDeskId(): string {
    return this.deskId;
  }

  /** Show the desk monitor as on/off with a given colour (§21). */
  setMonitor(on: boolean, color = 0x0a1a2a): void {
    this.monitorMaterial.emissive.setHex(on ? color : 0x000000);
    this.monitorMaterial.emissiveIntensity = on ? 1.4 : 0;
  }

  /** Remove every child of the build root and free its GPU memory. */
  clearBuild(): void {
    disposeChildren(this.buildRoot);
  }

  setWorkshopVisible(v: boolean): void {
    this.workshop.visible = v;
  }

  dispose(): void {
    this.stop();
    window.removeEventListener('resize', this.handleResize);
    this.cameraController.dispose();
    disposeChildren(this.scene);
    this.envMap?.dispose();
    mats.dispose();
    geo.dispose();
    this.renderer.dispose();
  }
}

/**
 * Recursively free geometry and any material not owned by the shared library.
 * Cached materials are reused, so only one-off ones (RGB emissives) are freed.
 */
export function disposeChildren(root: THREE.Object3D): void {
  for (const child of [...root.children]) {
    root.remove(child);
    child.traverse((o) => {
      const mesh = o as THREE.Mesh;
      if (!mesh.isMesh) return;
      if (mesh.geometry && mesh.geometry.userData.shared !== true) mesh.geometry.dispose();
      const m = mesh.material;
      for (const mat of Array.isArray(m) ? m : [m]) {
        if (mat && mat.userData?.shared !== true) mat.dispose();
      }
    });
  }
}

export { mm };
