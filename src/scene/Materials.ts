import * as THREE from 'three';

/**
 * Shared material library. Every material is created once and reused across all
 * parts so a full case costs a handful of draw-call state changes, not hundreds
 * (§40).
 */
class MaterialLibrary {
  private cache = new Map<string, THREE.Material>();

  private make<T extends THREE.Material>(key: string, factory: () => T): T {
    const hit = this.cache.get(key);
    if (hit) return hit as T;
    const mat = factory();
    // Marked so scene teardown never disposes a material other parts still use.
    mat.userData.shared = true;
    this.cache.set(key, mat);
    return mat;
  }

  /** Painted / powder-coated steel: case panels, brackets. */
  steel(color = 0x1c2026, roughness = 0.62): THREE.MeshStandardMaterial {
    return this.make(`steel-${color}-${roughness}`, () =>
      new THREE.MeshStandardMaterial({ color, roughness, metalness: 0.75 })
    );
  }

  /** Brushed aluminium: coolers, heatsinks, premium chassis. */
  aluminium(color = 0xb9c0cb): THREE.MeshStandardMaterial {
    return this.make(`alu-${color}`, () =>
      new THREE.MeshStandardMaterial({ color, roughness: 0.34, metalness: 0.92 })
    );
  }

  /** Copper heatpipes. */
  copper(): THREE.MeshStandardMaterial {
    return this.make('copper', () =>
      new THREE.MeshStandardMaterial({ color: 0xc87533, roughness: 0.28, metalness: 1 })
    );
  }

  /** PCB substrate — the dark green/black board everything sits on. */
  pcb(color = 0x0d1a14): THREE.MeshStandardMaterial {
    return this.make(`pcb-${color}`, () =>
      new THREE.MeshStandardMaterial({ color, roughness: 0.78, metalness: 0.12 })
    );
  }

  /** Matte plastic: fan frames, shrouds, connectors. */
  plastic(color = 0x14171c, roughness = 0.86): THREE.MeshStandardMaterial {
    return this.make(`plastic-${color}-${roughness}`, () =>
      new THREE.MeshStandardMaterial({ color, roughness, metalness: 0.02 })
    );
  }

  /** Tinted tempered glass side panel. */
  glass(): THREE.MeshPhysicalMaterial {
    return this.make(
      'glass',
      () =>
        new THREE.MeshPhysicalMaterial({
          color: 0x2a3038,
          roughness: 0.06,
          metalness: 0,
          transmission: 0.82,
          thickness: 0.4,
          transparent: true,
          opacity: 0.42,
          side: THREE.DoubleSide,
          ior: 1.5,
        })
    );
  }

  /** Cheap opaque stand-in for glass on low quality. */
  glassCheap(): THREE.MeshStandardMaterial {
    return this.make(
      'glass-cheap',
      () =>
        new THREE.MeshStandardMaterial({
          color: 0x2a3038,
          roughness: 0.1,
          metalness: 0.3,
          transparent: true,
          opacity: 0.34,
          side: THREE.DoubleSide,
        })
    );
  }

  /** Gold contacts and pins. */
  gold(): THREE.MeshStandardMaterial {
    return this.make('gold', () =>
      new THREE.MeshStandardMaterial({ color: 0xd9b45a, roughness: 0.3, metalness: 1 })
    );
  }

  /** Sleeved cable braid. */
  cable(color = 0x0d0f13): THREE.MeshStandardMaterial {
    return this.make(`cable-${color}`, () =>
      new THREE.MeshStandardMaterial({ color, roughness: 0.92, metalness: 0.05 })
    );
  }

  /** Silicon die / IHS. */
  ihs(): THREE.MeshStandardMaterial {
    return this.make('ihs', () =>
      new THREE.MeshStandardMaterial({ color: 0x9aa3ad, roughness: 0.22, metalness: 0.95 })
    );
  }

  /** Thermal paste blob. */
  paste(): THREE.MeshStandardMaterial {
    return this.make('paste', () =>
      new THREE.MeshStandardMaterial({ color: 0xd8d8d2, roughness: 0.45, metalness: 0.1 })
    );
  }

  /**
   * Emissive material for RGB and status LEDs. These are *not* cached by
   * colour because each zone animates its own colour independently.
   */
  emissive(color: number, intensity = 1.6): THREE.MeshStandardMaterial {
    return new THREE.MeshStandardMaterial({
      color: 0x05070a,
      emissive: color,
      emissiveIntensity: intensity,
      roughness: 0.4,
      metalness: 0,
    });
  }

  /** Ghost preview shown at a valid install target. */
  ghost(color = 0x00d0ff): THREE.MeshStandardMaterial {
    return this.make(`ghost-${color}`, () =>
      new THREE.MeshStandardMaterial({
        color,
        emissive: color,
        emissiveIntensity: 0.5,
        transparent: true,
        opacity: 0.22,
        depthWrite: false,
        side: THREE.DoubleSide,
      })
    );
  }

  /** Anti-static mat on the bench. */
  mat(): THREE.MeshStandardMaterial {
    return this.make('mat', () =>
      new THREE.MeshStandardMaterial({ color: 0x14243a, roughness: 0.95, metalness: 0 })
    );
  }

  /** Desk surface. */
  desk(): THREE.MeshStandardMaterial {
    return this.make('desk', () =>
      new THREE.MeshStandardMaterial({ color: 0x241d18, roughness: 0.72, metalness: 0.05 })
    );
  }

  dispose(): void {
    for (const m of this.cache.values()) m.dispose();
    this.cache.clear();
  }
}

export const mats = new MaterialLibrary();

/** Shared geometry for the many identical small objects (screws, fan blades). */
class GeometryLibrary {
  private cache = new Map<string, THREE.BufferGeometry>();

  box(w: number, h: number, d: number): THREE.BoxGeometry {
    const key = `box-${w.toFixed(4)}-${h.toFixed(4)}-${d.toFixed(4)}`;
    let g = this.cache.get(key) as THREE.BoxGeometry | undefined;
    if (!g) {
      g = new THREE.BoxGeometry(w, h, d);
      g.userData.shared = true;
      this.cache.set(key, g);
    }
    return g;
  }

  cylinder(rTop: number, rBottom: number, h: number, seg = 12): THREE.CylinderGeometry {
    const key = `cyl-${rTop.toFixed(4)}-${rBottom.toFixed(4)}-${h.toFixed(4)}-${seg}`;
    let g = this.cache.get(key) as THREE.CylinderGeometry | undefined;
    if (!g) {
      g = new THREE.CylinderGeometry(rTop, rBottom, h, seg);
      g.userData.shared = true;
      this.cache.set(key, g);
    }
    return g;
  }

  plane(w: number, h: number): THREE.PlaneGeometry {
    const key = `plane-${w.toFixed(4)}-${h.toFixed(4)}`;
    let g = this.cache.get(key) as THREE.PlaneGeometry | undefined;
    if (!g) {
      g = new THREE.PlaneGeometry(w, h);
      g.userData.shared = true;
      this.cache.set(key, g);
    }
    return g;
  }

  dispose(): void {
    for (const g of this.cache.values()) g.dispose();
    this.cache.clear();
  }
}

export const geo = new GeometryLibrary();

/** Millimetres to world units. 1 world unit = 10cm, so a 450mm case is 4.5. */
export const MM = 0.01;
export const mm = (v: number): number => v * MM;
