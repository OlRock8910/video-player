import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { CameraController } from './CameraController';
import { geo, mats, mm } from './Materials';
import { settings, type QualityProfile } from '../core/Settings';

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
    this.renderer.toneMappingExposure = 1.25;

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
    const w = this.workshop;

    // Desk.
    const deskTop = new THREE.Mesh(geo.box(16, 0.22, 10), mats.desk());
    deskTop.position.y = -0.11;
    deskTop.receiveShadow = true;
    w.add(deskTop);

    for (const [lx, lz] of [
      [-7.2, -4.2],
      [7.2, -4.2],
      [-7.2, 4.2],
      [7.2, 4.2],
    ]) {
      const leg = new THREE.Mesh(geo.box(0.28, 7, 0.28), mats.steel(0x0d0f13, 0.7));
      leg.position.set(lx, -3.7, lz);
      w.add(leg);
    }

    // Anti-static mat under the build area.
    const mat = new THREE.Mesh(geo.box(9.5, 0.03, 6.4), mats.mat());
    mat.position.set(0, 0.015, 0.4);
    mat.receiveShadow = true;
    w.add(mat);
    // Mat edge stripe.
    const stripe = new THREE.Mesh(geo.box(9.5, 0.035, 0.14), mats.plastic(0x2f6fa8, 0.9));
    stripe.position.set(0, 0.018, 3.5);
    w.add(stripe);

    // Screw tray (§8) — screws visibly land here.
    const trayGroup = new THREE.Group();
    const trayFloor = new THREE.Mesh(geo.box(1.5, 0.05, 1.1), mats.plastic(0x1b1f26));
    trayGroup.add(trayFloor);
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
    trayGroup.position.set(3.9, 0.05, 2.1);
    trayGroup.name = 'screw-tray';
    w.add(trayGroup);

    // Screwdriver resting on the mat.
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
    driver.position.set(-3.6, 0.18, 2.3);
    driver.rotation.y = 0.4;
    driver.name = 'screwdriver';
    w.add(driver);

    // Thermal paste tube.
    const paste = new THREE.Group();
    const tube = new THREE.Mesh(geo.cylinder(0.11, 0.11, 0.7, 10), mats.plastic(0xc8ccd2, 0.5));
    tube.rotation.z = Math.PI / 2.2;
    const nozzle = new THREE.Mesh(new THREE.ConeGeometry(0.06, 0.2, 8), mats.plastic(0x2b3038));
    nozzle.rotation.z = -Math.PI / 2.2;
    nozzle.position.set(0.42, 0.16, 0);
    paste.add(tube, nozzle);
    paste.position.set(-3.6, 0.12, 1.3);
    paste.name = 'paste-tube';
    w.add(paste);

    // Component boxes stacked at the edge of the bench.
    for (let i = 0; i < 3; i++) {
      const box = new THREE.Mesh(
        geo.box(1.5, 0.5, 1.05),
        mats.plastic([0x1d2733, 0x2a1d33, 0x1d3329][i], 0.9)
      );
      box.position.set(-5.4 + (i % 2) * 0.2, 0.25 + i * 0.52, -2.4 + i * 0.12);
      box.rotation.y = 0.2 - i * 0.14;
      box.castShadow = true;
      this.workshop.add(box);
      const label = new THREE.Mesh(geo.box(0.9, 0.24, 0.02), mats.plastic(0xd8dde4, 0.95));
      label.position.set(box.position.x, box.position.y, box.position.z + 0.54);
      label.rotation.y = box.rotation.y;
      w.add(label);
    }

    // Manual and cable ties, small details that sell the bench (§4).
    const manual = new THREE.Mesh(geo.box(1.1, 0.05, 1.5), mats.plastic(0xdfe4ea, 0.95));
    manual.position.set(4.6, 0.04, -1.4);
    manual.rotation.y = -0.3;
    w.add(manual);
    for (let i = 0; i < 5; i++) {
      const tie = new THREE.Mesh(geo.box(0.5, 0.012, 0.04), mats.plastic(0x0b0d10, 0.9));
      tie.position.set(4.2 + Math.random() * 0.3, 0.03, 1.1 + i * 0.07);
      tie.rotation.y = Math.random() * 0.6;
      w.add(tie);
    }

    // Monitor behind the bench — used for POST and the benchmark (§21, §22).
    const monitor = new THREE.Group();
    const panel = new THREE.Mesh(geo.box(6.4, 3.7, 0.12), mats.plastic(0x0a0c10, 0.4));
    panel.position.y = 2.6;
    const screenMat = new THREE.MeshStandardMaterial({
      color: 0x05070a,
      emissive: 0x000000,
      emissiveIntensity: 1,
      roughness: 0.3,
    });
    const screen = new THREE.Mesh(geo.plane(6.1, 3.4), screenMat);
    screen.position.set(0, 2.6, 0.07);
    screen.name = 'monitor-screen';
    const stand = new THREE.Mesh(geo.box(0.3, 1.5, 0.3), mats.plastic(0x14171c));
    stand.position.y = 1.0;
    const base = new THREE.Mesh(geo.box(2.2, 0.1, 1.1), mats.plastic(0x14171c));
    base.position.y = 0.3;
    monitor.add(panel, screen, stand, base);
    monitor.position.set(0, 0, -4.3);
    monitor.name = 'monitor';
    w.add(monitor);
    this.monitorMaterial = screenMat;

    // Keyboard.
    const kb = new THREE.Mesh(geo.box(4.4, 0.16, 1.5), mats.plastic(0x0f1216));
    kb.position.set(0, 0.08, -2.4);
    kb.castShadow = true;
    w.add(kb);
    for (let r = 0; r < 4; r++) {
      for (let k = 0; k < 16; k++) {
        const key = new THREE.Mesh(geo.box(0.2, 0.05, 0.2), mats.plastic(0x1c2026, 0.8));
        key.position.set(-2 + k * 0.26, 0.17, -2.9 + r * 0.26);
        w.add(key);
      }
    }

    // Backdrop wall so the scene is not floating in void.
    const wall = new THREE.Mesh(geo.plane(48, 26), mats.plastic(0x0a0d12, 1));
    wall.position.set(0, 6, -12);
    w.add(wall);
  }

  /** Emissive material of the desk monitor, driven by the POST screen. */
  monitorMaterial!: THREE.MeshStandardMaterial;

  private buildLighting(): void {
    // A dark game still needs enough light to read shapes on a phone screen.
    const ambient = new THREE.AmbientLight(0x6c7f9c, 1.15);
    this.scene.add(ambient);

    const hemi = new THREE.HemisphereLight(0x8fb0d8, 0x141a22, 1.35);
    this.scene.add(hemi);

    this.keyLight = new THREE.DirectionalLight(0xe8f2ff, 3.2);
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
    const interior = new THREE.PointLight(0xbfd8ff, 26, 14, 2.1);
    interior.position.set(2.4, 5.2, 1.6);
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

    this.renderer.toneMappingExposure = p.bloom ? 1.32 : 1.2;
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
