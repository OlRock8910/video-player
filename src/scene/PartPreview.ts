import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { buildComponent } from './PartBuilders';
import { getComponent } from '../data/catalog';

/**
 * Renders a component's actual 3D model to a small image, so the shop shows
 * what a part looks like rather than only what it costs (§29).
 *
 * One tiny offscreen renderer is shared by every thumbnail and results are
 * cached by component id — a scrolling shop must not build a WebGL context per
 * card. It renders on demand and stays idle otherwise.
 */
const SIZE = 256;

class PartPreviewRenderer {
  private renderer: THREE.WebGLRenderer | null = null;
  private scene: THREE.Scene | null = null;
  private camera: THREE.PerspectiveCamera | null = null;
  private cache = new Map<string, string>();
  /** Set once we know the device cannot give us a second WebGL context. */
  private unavailable = false;

  private init(): boolean {
    if (this.unavailable) return false;
    if (this.renderer) return true;
    try {
      const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      renderer.setSize(SIZE, SIZE, false);
      renderer.setClearColor(0x000000, 0);
      renderer.outputColorSpace = THREE.SRGBColorSpace;
      renderer.toneMapping = THREE.ACESFilmicToneMapping;
      renderer.toneMappingExposure = 1.25;

      const scene = new THREE.Scene();
      const pmrem = new THREE.PMREMGenerator(renderer);
      scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
      scene.environmentIntensity = 1.0;
      pmrem.dispose();

      scene.add(new THREE.AmbientLight(0x8fa4c0, 1.4));
      const key = new THREE.DirectionalLight(0xffffff, 2.6);
      key.position.set(3, 5, 4);
      scene.add(key);
      const rim = new THREE.DirectionalLight(0x6fb4ff, 1.5);
      rim.position.set(-4, 2, -3);
      scene.add(rim);

      this.renderer = renderer;
      this.scene = scene;
      this.camera = new THREE.PerspectiveCamera(38, 1, 0.01, 100);
      return true;
    } catch {
      // A second context is a luxury; the shop falls back to text-only cards.
      this.unavailable = true;
      return false;
    }
  }

  /**
   * A PNG data URL of the part, or null if previews are unavailable here.
   * Cached, so repeated shop visits cost nothing.
   */
  render(componentId: string): string | null {
    const hit = this.cache.get(componentId);
    if (hit) return hit;
    if (!this.init()) return null;

    const component = getComponent(componentId);
    if (!component) return null;

    const renderer = this.renderer!;
    const scene = this.scene!;
    const camera = this.camera!;

    // Build at low quality: a 256px thumbnail cannot show the difference, and
    // this keeps the cost of opening the shop down.
    const built = buildComponent(component, 'low');
    const group = built.group;
    scene.add(group);

    // Frame whatever was built, whatever its size.
    const box = new THREE.Box3().setFromObject(group);
    const size = box.getSize(new THREE.Vector3());
    const centre = box.getCenter(new THREE.Vector3());
    const radius = Math.max(0.001, size.length() * 0.5);
    const dist = (radius / Math.sin((camera.fov * Math.PI) / 360)) * 1.05;

    // A three-quarter view reads best for boxy hardware.
    camera.position.set(centre.x + dist * 0.62, centre.y + dist * 0.42, centre.z + dist * 0.66);
    camera.lookAt(centre);
    camera.updateProjectionMatrix();

    let url: string | null = null;
    try {
      renderer.render(scene, camera);
      url = renderer.domElement.toDataURL('image/png');
    } catch {
      url = null;
    }

    scene.remove(group);
    // The thumbnail is a throwaway: free anything it created that is not
    // shared with the main scene's libraries.
    group.traverse((o) => {
      const mesh = o as THREE.Mesh;
      if (!mesh.isMesh) return;
      if (mesh.geometry && mesh.geometry.userData.shared !== true) mesh.geometry.dispose();
      for (const m of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) {
        if (m && m.userData?.shared !== true) m.dispose();
      }
    });

    if (url) this.cache.set(componentId, url);
    return url;
  }

  dispose(): void {
    this.renderer?.dispose();
    this.renderer = null;
    this.scene = null;
    this.cache.clear();
  }
}

export const partPreview = new PartPreviewRenderer();
