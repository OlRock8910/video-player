import * as THREE from 'three';
import type { RgbProfile, RgbZone } from '../sim/Build';

interface ZoneEntry {
  zone: RgbZone;
  material: THREE.MeshStandardMaterial;
  /** 0-1 position along the case, used for wave offsets. */
  phase: number;
}

/**
 * Drives every emissive material in the build from one profile (§32).
 * Materials register themselves by tagging `userData.rgbZone`, so a new part
 * with lighting needs no changes here.
 */
export class RGBManager {
  private entries: ZoneEntry[] = [];
  private profile: RgbProfile | null = null;
  private enabled = false;
  private time = 0;
  private tmp = new THREE.Color();

  /** Scan a subtree and pick up any tagged emissive materials. */
  register(root: THREE.Object3D): void {
    const bounds = new THREE.Box3().setFromObject(root);
    const span = Math.max(0.001, bounds.max.y - bounds.min.y);
    root.traverse((o) => {
      const mesh = o as THREE.Mesh;
      if (!mesh.isMesh) return;
      const list = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
      for (const m of list) {
        const mat = m as THREE.MeshStandardMaterial;
        const zone = mat?.userData?.rgbZone as RgbZone | undefined;
        if (!zone) continue;
        if (this.entries.some((e) => e.material === mat)) continue;
        const world = new THREE.Vector3();
        mesh.getWorldPosition(world);
        this.entries.push({
          zone,
          material: mat,
          phase: THREE.MathUtils.clamp((world.y - bounds.min.y) / span, 0, 1),
        });
      }
    });
  }

  unregisterUnder(root: THREE.Object3D): void {
    const doomed = new Set<THREE.Material>();
    root.traverse((o) => {
      const mesh = o as THREE.Mesh;
      if (!mesh.isMesh) return;
      for (const m of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) {
        if (m) doomed.add(m);
      }
    });
    this.entries = this.entries.filter((e) => !doomed.has(e.material));
  }

  clear(): void {
    this.entries = [];
  }

  setProfile(profile: RgbProfile): void {
    this.profile = profile;
  }

  /** Lighting is dark until the machine is powered on (§21). */
  setEnabled(on: boolean): void {
    this.enabled = on;
    if (!on) for (const e of this.entries) e.material.emissiveIntensity = 0;
  }

  /** How many distinct zones actually have lighting installed (§26 RGB jobs). */
  activeZoneCount(): number {
    return new Set(this.entries.map((e) => e.zone)).size;
  }

  update(dt: number): void {
    if (!this.enabled || !this.profile) return;
    this.time += dt * (0.25 + this.profile.speed * 1.5);
    const p = this.profile;

    for (const e of this.entries) {
      const base = p.zones[e.zone] ?? 0x00d0ff;
      let intensity = p.brightness * 1.8;

      switch (p.mode) {
        case 'static':
          this.tmp.setHex(base);
          break;
        case 'breathing': {
          this.tmp.setHex(base);
          intensity *= 0.25 + 0.75 * (0.5 + 0.5 * Math.sin(this.time * 1.6));
          break;
        }
        case 'rainbow': {
          this.tmp.setHSL((this.time * 0.12) % 1, 0.85, 0.55);
          break;
        }
        case 'wave': {
          this.tmp.setHSL((this.time * 0.14 + e.phase * 0.55) % 1, 0.85, 0.55);
          break;
        }
        case 'pulse': {
          this.tmp.setHex(base);
          const beat = (this.time * 1.1) % 1;
          intensity *= beat < 0.14 ? 1.9 : 0.42;
          break;
        }
        case 'reactive': {
          // Reactive tracks a slow travelling highlight per zone.
          this.tmp.setHex(base);
          const t = (this.time * 0.5 + e.phase) % 1;
          intensity *= 0.35 + 1.5 * Math.exp(-Math.pow((t - 0.5) * 4, 2));
          break;
        }
      }

      e.material.emissive.copy(this.tmp);
      e.material.emissiveIntensity = intensity;
    }
  }
}
