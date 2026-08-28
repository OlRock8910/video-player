import * as THREE from 'three';
import { buildComponent, buildScrew, buildStandoff, type BuiltCase, type BuiltPart } from './PartBuilders';
import { computeLayout, type CaseLayout, type SlotAnchor } from './CaseLayout';
import { CableRenderer, CABLE_COLORS } from './CableRenderer';
import { RGBManager } from './RGBManager';
import { mats, mm } from './Materials';
import type { CableKind, Component, Slot } from '../data/types';
import { isCase } from '../data/types';
import { getComponent } from '../data/catalog';
import { type Build, screwsFor } from '../sim/Build';
import { settings } from '../core/Settings';

export interface PlacedPart {
  slot: Slot;
  componentId: string;
  built: BuiltPart;
  /** Screw meshes belonging to this part, with drive progress. */
  screws: ScrewInstance[];
}

export interface ScrewInstance {
  group: THREE.Group;
  /** 0 = proud, 1 = fully driven. */
  progress: number;
  restY: THREE.Vector3;
  axis: THREE.Vector3;
  slot: Slot;
  done: boolean;
  /** Set once a removed screw has been dropped into the desk tray. */
  popped?: boolean;
}

/** A short transform animation. Everything moves, nothing teleports (§9). */
interface Tween {
  object: THREE.Object3D;
  fromPos: THREE.Vector3;
  toPos: THREE.Vector3;
  fromQuat: THREE.Quaternion;
  toQuat: THREE.Quaternion;
  t: number;
  duration: number;
  onDone?: () => void;
}

const easeOutBack = (t: number): number => {
  const c1 = 1.2;
  return 1 + (c1 + 1) * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
};
const easeInOut = (t: number): number => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2);

/**
 * The 3D representation of one build. Owns the case, every installed part,
 * screws, standoffs, cables and the power-on state, and keeps them in sync with
 * the `Build` data model.
 */
export class BuildScene {
  readonly root = new THREE.Group();
  readonly rgb = new RGBManager();
  readonly cables = new CableRenderer();

  private caseBuilt: BuiltCase | null = null;
  private layout: CaseLayout | null = null;
  private placed = new Map<Slot, PlacedPart>();
  private standoffs: THREE.Group[] = [];
  private ghost: THREE.Object3D | null = null;
  private highlight: THREE.Mesh | null = null;
  private pasteBlobs = new THREE.Group();
  private tweens: Tween[] = [];

  /** Fan/pump spin speed, 0 when off. */
  private spinSpeed = 0;
  private targetSpin = 0;
  private panelOpen = false;

  constructor() {
    this.root.add(this.cables.group, this.pasteBlobs);
  }

  get caseLayout(): CaseLayout | null {
    return this.layout;
  }

  get builtCase(): BuiltCase | null {
    return this.caseBuilt;
  }

  /* ---------------------------------------------------------------- */
  /* Case                                                              */
  /* ---------------------------------------------------------------- */

  setCase(component: Component): void {
    if (!isCase(component)) return;
    this.reset();
    const quality = settings.get().quality === 'low' ? 'low' : 'high';
    const built = buildComponent(component, quality) as BuiltCase;
    this.caseBuilt = built;
    this.layout = computeLayout(component);
    built.group.name = 'pc-case';
    built.group.userData.slot = 'case';
    built.group.userData.componentId = component.id;
    this.root.add(built.group);
    this.cables.setRoutingPath(this.layout.routingPath);
    this.rgb.register(built.group);

    // Spill lights live inside the chassis; count follows the quality preset.
    const profile = settings.profile();
    const spillCount = profile.accentLights >= 4 ? 3 : profile.accentLights >= 2 ? 2 : 1;
    const bounds = new THREE.Box3(
      new THREE.Vector3(-this.layout.width / 2, 0, -this.layout.depth / 2),
      new THREE.Vector3(this.layout.width / 2, this.layout.height, this.layout.depth / 2)
    );
    this.rgb.attachSpill(built.interior, spillCount, bounds);

    // Panel screws start proud and in place (§8).
    for (let i = 0; i < built.panelScrewAnchors.length; i++) {
      const anchor = built.panelScrewAnchors[i];
      const screw = buildScrew();
      screw.position.copy(anchor);
      screw.rotation.y = Math.PI / 2;
      screw.userData.panelScrewIndex = i;
      screw.name = `panel-screw-${i}`;
      built.group.add(screw);
      this.panelScrews.push({
        group: screw,
        progress: 1,
        restY: anchor.clone(),
        axis: new THREE.Vector3(1, 0, 0),
        slot: 'case',
        done: true,
      });
    }
  }

  /** Screws holding the side panel on. Removed, not driven. */
  readonly panelScrews: ScrewInstance[] = [];

  /** True once all four panel screws are out. */
  get panelScrewsRemoved(): boolean {
    return this.panelScrews.every((s) => s.progress <= 0.02);
  }

  /** Back the given panel screw out a little. Returns true when it pops free. */
  loosenPanelScrew(index: number, amount: number): boolean {
    const s = this.panelScrews[index];
    if (!s || s.progress <= 0) return false;
    s.progress = Math.max(0, s.progress - amount);
    s.group.rotation.x -= amount * Math.PI * 5;
    s.group.position.x = s.restY.x + (1 - s.progress) * mm(9);
    if (s.progress <= 0.02 && !s.popped) {
      s.popped = true;
      this.dropScrewToTray(s.group);
      return true;
    }
    return false;
  }

  /** Animate a removed screw arcing into the desk tray (§8). */
  private dropScrewToTray(screw: THREE.Group): void {
    const world = new THREE.Vector3();
    screw.getWorldPosition(world);
    this.root.attach(screw);
    const target = new THREE.Vector3(3.9 + (Math.random() - 0.5) * 0.7, 0.14, 2.1 + (Math.random() - 0.5) * 0.5);
    this.root.worldToLocal(target);
    this.tween(screw, target, new THREE.Quaternion().setFromEuler(new THREE.Euler(Math.PI / 2, 0, Math.random() * 3)), 0.55);
  }

  /** Slide the side panel off (§8). */
  openPanel(onDone?: () => void): void {
    if (!this.caseBuilt || !this.layout || this.panelOpen) return;
    this.panelOpen = true;
    const panel = this.caseBuilt.sidePanel;
    const to = panel.position.clone().add(this.layout.panelOpenOffset);
    to.y -= 0.3;
    const quat = new THREE.Quaternion().setFromEuler(new THREE.Euler(0, 0, -0.34));
    this.tween(panel, to, quat, 0.9, onDone);
  }

  get isPanelOpen(): boolean {
    return this.panelOpen;
  }

  /* ---------------------------------------------------------------- */
  /* Standoffs (§9)                                                    */
  /* ---------------------------------------------------------------- */

  standoffPositions(count: number): THREE.Vector3[] {
    if (!this.layout) return [];
    const anchor = this.layout.anchors.get('mobo-tray');
    if (!anchor) return [];
    const out: THREE.Vector3[] = [];
    const cols = 3;
    const rows = Math.ceil(count / cols);
    for (let i = 0; i < count; i++) {
      const cx = i % cols;
      const cy = Math.floor(i / cols);
      out.push(
        new THREE.Vector3(
          anchor.position.x + mm(3),
          anchor.position.y + (cy / Math.max(1, rows - 1) - 0.5) * mm(210),
          anchor.position.z + (cx / (cols - 1) - 0.5) * mm(250)
        )
      );
    }
    return out;
  }

  placeStandoff(index: number, count: number): void {
    const positions = this.standoffPositions(count);
    const p = positions[index];
    if (!p) return;
    const s = buildStandoff();
    s.position.copy(p);
    s.rotation.y = Math.PI / 2;
    s.name = `standoff-${index}`;
    this.root.add(s);
    this.standoffs.push(s);
    // Drop in from above so it reads as being placed by hand.
    const from = p.clone();
    from.y += 0.5;
    s.position.copy(from);
    this.tween(s, p, s.quaternion.clone(), 0.28);
  }

  get standoffCount(): number {
    return this.standoffs.length;
  }

  /* ---------------------------------------------------------------- */
  /* Parts                                                             */
  /* ---------------------------------------------------------------- */

  anchorFor(slot: Slot): SlotAnchor | undefined {
    return this.layout?.anchors.get(slot);
  }

  /** World-space position of a slot, for camera focus and cable ends. */
  worldAnchor(slot: Slot, out = new THREE.Vector3()): THREE.Vector3 {
    const a = this.anchorFor(slot);
    if (!a) return out.set(0, 1, 0);
    out.copy(a.position);
    this.caseBuilt?.group.localToWorld(out);
    return out;
  }

  /**
   * Spawn a part loose in the world so the player can pick it up, rotate it and
   * carry it to the slot. Returns the group to be driven by the drag handler.
   */
  spawnLoose(component: Component, at: THREE.Vector3): BuiltPart {
    const built = buildComponent(component, settings.get().quality === 'low' ? 'low' : 'high');
    built.group.position.copy(at);
    built.group.userData.componentId = component.id;
    built.group.userData.loose = true;
    this.root.add(built.group);
    return built;
  }

  /**
   * Seat a part into its slot with a real animation from wherever it currently
   * is (§9 — never teleport). Returns the placed record.
   */
  install(component: Component, slot: Slot, built: BuiltPart, onSeated?: () => void): PlacedPart {
    const anchor = this.anchorFor(slot);
    const parent = this.caseBuilt?.interior ?? this.root;

    // Keep the world transform while re-parenting into the case.
    parent.attach(built.group);
    built.group.userData.loose = false;
    built.group.userData.slot = slot;
    built.group.userData.componentId = component.id;

    const record: PlacedPart = { slot, componentId: component.id, built, screws: [] };
    this.placed.set(slot, record);

    if (anchor) {
      const targetQuat = new THREE.Quaternion().setFromEuler(anchor.rotation);
      this.tween(built.group, anchor.position.clone(), targetQuat, 0.42, () => {
        this.spawnScrews(record, component, anchor);
        this.mountRadiator(built, anchor);
        this.rgb.register(built.group);
        onSeated?.();
      });
    } else {
      this.rgb.register(built.group);
      onSeated?.();
    }
    return record;
  }

  /**
   * An AIO's radiator is built as a child of the pump because that is how the
   * part arrives in the box, but it physically bolts to the case roof. The
   * chassis dimensions only exist here, so the final placement happens here
   * too: lay the radiator flat, length along the case depth, under the roof.
   */
  private mountRadiator(built: BuiltPart, anchor: SlotAnchor): void {
    const rad = built.group.getObjectByName('radiator');
    if (!rad || !this.layout) return;
    const { height, depth } = this.layout;

    // The cooler group is yawed 90°, so a local Z-roll lays the radiator flat
    // with its length running front to back.
    rad.rotation.set(0, 0, Math.PI / 2);

    // Convert the desired case-space position into the cooler's local space,
    // which is the inverse of that yaw.
    const target = new THREE.Vector3(0, height - mm(38), -depth * 0.04);
    const d = target.clone().sub(anchor.position);
    rad.position.set(d.z, d.y, -d.x);
  }

  /** Lay out the screws a part needs, proud and waiting to be driven (§7). */
  private spawnScrews(record: PlacedPart, component: Component, anchor: SlotAnchor): void {
    const count = screwsFor(component);
    if (count === 0) return;
    const spread = component.category === 'motherboard' ? mm(110) : mm(38);
    for (let i = 0; i < count; i++) {
      const screw = buildScrew(0xb9c0cb);
      const a = (i / count) * Math.PI * 2;
      const local = new THREE.Vector3(
        mm(10),
        Math.sin(a) * spread,
        Math.cos(a) * spread
      );
      const pos = anchor.position.clone().add(local);
      screw.position.copy(pos);
      screw.rotation.y = Math.PI / 2;
      screw.userData.screwFor = record.slot;
      screw.userData.screwIndex = i;
      screw.name = `screw-${record.slot}-${i}`;
      (this.caseBuilt?.interior ?? this.root).add(screw);
      record.screws.push({
        group: screw,
        progress: 0,
        restY: pos.clone(),
        axis: new THREE.Vector3(1, 0, 0),
        slot: record.slot,
        done: false,
      });
    }
  }

  /**
   * Drive a screw while the player holds it. Returns true on the frame it
   * finishes so the caller can fire the haptic and sound (§7).
   */
  driveScrew(slot: Slot, index: number, amount: number): boolean {
    const record = this.placed.get(slot);
    const screw = record?.screws[index];
    if (!screw || screw.done) return false;
    screw.progress = Math.min(1, screw.progress + amount);
    screw.group.rotation.x += amount * Math.PI * 6;
    screw.group.position.x = screw.restY.x - screw.progress * mm(4.5);
    if (screw.progress >= 1) {
      screw.done = true;
      return true;
    }
    return false;
  }

  screwsRemaining(slot: Slot): number {
    const r = this.placed.get(slot);
    if (!r) return 0;
    return r.screws.filter((s) => !s.done).length;
  }

  remove(slot: Slot): void {
    const record = this.placed.get(slot);
    if (!record) return;
    this.rgb.unregisterUnder(record.built.group);
    record.built.group.removeFromParent();
    for (const s of record.screws) s.group.removeFromParent();
    this.placed.delete(slot);
  }

  placedAt(slot: Slot): PlacedPart | undefined {
    return this.placed.get(slot);
  }

  allPlaced(): PlacedPart[] {
    return [...this.placed.values()];
  }

  /* ---------------------------------------------------------------- */
  /* Thermal paste (§11)                                               */
  /* ---------------------------------------------------------------- */

  addPasteBlob(u: number, v: number, size: number): void {
    const anchor = this.anchorFor('cpu-socket');
    if (!anchor) return;
    const blob = new THREE.Mesh(
      new THREE.SphereGeometry(mm(2 + size * 3), 8, 6),
      mats.paste()
    );
    blob.scale.z = 0.4;
    blob.position.copy(anchor.position);
    blob.position.x += mm(4);
    blob.position.y += (v - 0.5) * mm(24);
    blob.position.z += (u - 0.5) * mm(24);
    (this.caseBuilt?.interior ?? this.root).add(blob);
    this.pasteBlobs.add(blob);
  }

  clearPaste(): void {
    for (const c of [...this.pasteBlobs.children]) {
      this.pasteBlobs.remove(c);
      (c as THREE.Mesh).geometry.dispose();
    }
  }

  get pasteBlobCount(): number {
    return this.pasteBlobs.children.length;
  }

  /* ---------------------------------------------------------------- */
  /* Cables (§15, §18)                                                 */
  /* ---------------------------------------------------------------- */

  connectCable(kind: CableKind, routed: boolean): void {
    const from = this.cableSource(kind);
    const to = this.cableTarget(kind);
    this.cables.add({ kind, from, to, routed, color: CABLE_COLORS[kind] });
  }

  setCableRouted(kind: CableKind, routed: boolean): void {
    if (!this.cables.has(kind)) return;
    this.cables.reroute({
      kind,
      from: this.cableSource(kind),
      to: this.cableTarget(kind),
      routed,
      color: CABLE_COLORS[kind],
    });
  }

  /** Cables originate at the PSU, except the header runs. */
  private cableSource(kind: CableKind): THREE.Vector3 {
    const psu = this.anchorFor('psu-bay');
    const base = psu ? psu.position.clone() : new THREE.Vector3(0, mm(60), 0);
    if (kind === 'cpu-fan' || kind === 'rgb-header') {
      const cooler = this.anchorFor('cooler-mount');
      return cooler ? cooler.position.clone() : base;
    }
    if (kind === 'front-panel') {
      const l = this.layout;
      return new THREE.Vector3(0, mm(30), l ? l.depth / 2 - mm(30) : mm(100));
    }
    return base.add(new THREE.Vector3(mm(20), mm(30), 0));
  }

  private cableTarget(kind: CableKind): THREE.Vector3 {
    const board = this.anchorFor('mobo-tray');
    const boardPos = board ? board.position.clone() : new THREE.Vector3(0, mm(200), 0);
    switch (kind) {
      case 'atx24':
        return boardPos.clone().add(new THREE.Vector3(mm(10), -mm(30), mm(110)));
      case 'eps8':
        return boardPos.clone().add(new THREE.Vector3(mm(10), mm(110), mm(40)));
      case 'pcie8': {
        const gpu = this.anchorFor('pcie-0');
        return (gpu ? gpu.position.clone() : boardPos.clone()).add(new THREE.Vector3(mm(40), mm(50), 0));
      }
      case 'sata-power':
      case 'sata-data': {
        const bay = this.anchorFor('drive-bay-0');
        return bay ? bay.position.clone() : boardPos.clone();
      }
      case 'cpu-fan':
      case 'pump-power':
        return boardPos.clone().add(new THREE.Vector3(mm(10), mm(120), -mm(20)));
      case 'rgb-header':
        return boardPos.clone().add(new THREE.Vector3(mm(10), -mm(90), mm(60)));
      case 'front-panel':
        return boardPos.clone().add(new THREE.Vector3(mm(10), -mm(100), mm(100)));
    }
  }

  /* ---------------------------------------------------------------- */
  /* Highlight and ghost (§6)                                          */
  /* ---------------------------------------------------------------- */

  showGhost(component: Component, slot: Slot, valid: boolean): void {
    this.clearGhost();
    const anchor = this.anchorFor(slot);
    if (!anchor) return;
    const built = buildComponent(component, 'low');
    const mat = mats.ghost(valid ? 0x00e5a0 : 0xff5566);
    built.group.traverse((o) => {
      const mesh = o as THREE.Mesh;
      if (mesh.isMesh) mesh.material = mat;
    });
    built.group.position.copy(anchor.position);
    built.group.quaternion.setFromEuler(anchor.rotation);
    (this.caseBuilt?.interior ?? this.root).add(built.group);
    this.ghost = built.group;
  }

  clearGhost(): void {
    if (!this.ghost) return;
    this.ghost.removeFromParent();
    this.ghost.traverse((o) => {
      const mesh = o as THREE.Mesh;
      if (mesh.isMesh && mesh.geometry.userData.shared !== true) mesh.geometry.dispose();
    });
    this.ghost = null;
  }

  /** A pulsing ring at a slot, used by the tutorial and diagnostics (§23, §46). */
  highlightSlot(slot: Slot | null): void {
    if (this.highlight) {
      this.highlight.removeFromParent();
      this.highlight.geometry.dispose();
      this.highlight = null;
    }
    if (!slot) return;
    const anchor = this.anchorFor(slot);
    if (!anchor) return;
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(anchor.snapRadius * 0.75, 0.012, 8, 32),
      mats.ghost(0x00d0ff)
    );
    ring.position.copy(anchor.position);
    ring.quaternion.setFromEuler(anchor.rotation);
    ring.name = 'slot-highlight';
    (this.caseBuilt?.interior ?? this.root).add(ring);
    this.highlight = ring;
  }

  /* ---------------------------------------------------------------- */
  /* Power (§21)                                                       */
  /* ---------------------------------------------------------------- */

  setPowered(on: boolean): void {
    this.targetSpin = on ? 1 : 0;
    this.rgb.setEnabled(on);
    if (this.caseBuilt) {
      this.caseBuilt.powerLed.emissive.setHex(on ? 0x00d0ff : 0x000000);
      this.caseBuilt.powerLed.emissiveIntensity = on ? 2.4 : 0;
    }
  }

  /** Fans twitch before they spin up, which is the detail that sells it. */
  twitchFans(): void {
    for (const p of this.placed.values()) {
      for (const s of p.built.spinners) s.rotation.z += 0.35;
    }
  }

  setRgbProfile(profile: Build['rgb']): void {
    this.rgb.setProfile(profile);
  }

  /* ---------------------------------------------------------------- */
  /* Frame                                                             */
  /* ---------------------------------------------------------------- */

  update(dt: number, elapsed: number): void {
    // Tweens.
    for (let i = this.tweens.length - 1; i >= 0; i--) {
      const tw = this.tweens[i];
      tw.t += dt / tw.duration;
      const k = Math.min(1, tw.t);
      const eased = tw.duration > 0.6 ? easeInOut(k) : easeOutBack(k);
      tw.object.position.lerpVectors(tw.fromPos, tw.toPos, Math.min(1, eased));
      tw.object.quaternion.slerpQuaternions(tw.fromQuat, tw.toQuat, easeInOut(k));
      if (k >= 1) {
        this.tweens.splice(i, 1);
        tw.onDone?.();
      }
    }

    // Fan spin-up ramps rather than snapping on.
    this.spinSpeed += (this.targetSpin - this.spinSpeed) * Math.min(1, dt * 1.1);
    if (this.spinSpeed > 0.001) {
      const w = this.spinSpeed * 14 * dt;
      for (const p of this.placed.values()) {
        for (const s of p.built.spinners) s.rotation.z += w;
      }
    }

    // Slot highlight pulse.
    if (this.highlight) {
      const s = 1 + Math.sin(elapsed * 3.4) * 0.09;
      this.highlight.scale.setScalar(s);
    }

    this.rgb.update(dt);
  }

  private tween(
    object: THREE.Object3D,
    toPos: THREE.Vector3,
    toQuat: THREE.Quaternion,
    duration: number,
    onDone?: () => void
  ): void {
    // Replace any in-flight tween for the same object.
    this.tweens = this.tweens.filter((t) => t.object !== object);
    this.tweens.push({
      object,
      fromPos: object.position.clone(),
      toPos,
      fromQuat: object.quaternion.clone(),
      toQuat,
      t: 0,
      duration: settings.get().reduceMotion ? Math.min(duration, 0.12) : duration,
      ...(onDone ? { onDone } : {}),
    });
  }

  /** Rebuild the whole 3D scene from saved build data (§48 restore). */
  restore(build: Build): void {
    const pcCase = build.parts.find((p) => p.slot === 'case');
    const caseComp = pcCase ? getComponent(pcCase.componentId) : undefined;
    if (!caseComp) return;
    this.setCase(caseComp);

    if (build.panelRemoved) {
      // Skip the animation on restore; the panel is simply already off.
      for (const s of this.panelScrews) {
        s.progress = 0;
        s.popped = true;
        s.group.removeFromParent();
      }
      this.panelOpen = true;
      if (this.caseBuilt && this.layout) {
        this.caseBuilt.sidePanel.position.add(this.layout.panelOpenOffset);
        this.caseBuilt.sidePanel.position.y -= 0.3;
        this.caseBuilt.sidePanel.rotation.z = -0.34;
      }
    }

    for (let i = 0; i < build.standoffsPlaced; i++) this.placeStandoff(i, build.standoffsPlaced);

    for (const part of build.parts) {
      if (part.slot === 'case') continue;
      const comp = getComponent(part.componentId);
      if (!comp) continue;
      const anchor = this.anchorFor(part.slot);
      const built = buildComponent(comp, settings.get().quality === 'low' ? 'low' : 'high');
      built.group.userData.componentId = comp.id;
      built.group.userData.slot = part.slot;
      if (anchor) {
        built.group.position.copy(anchor.position);
        built.group.quaternion.setFromEuler(anchor.rotation);
      }
      (this.caseBuilt?.interior ?? this.root).add(built.group);
      const record: PlacedPart = { slot: part.slot, componentId: comp.id, built, screws: [] };
      this.placed.set(part.slot, record);
      if (anchor) {
        this.spawnScrews(record, comp, anchor);
        this.mountRadiator(built, anchor);
      }
      // Restore how far the screws were driven.
      for (let i = 0; i < record.screws.length; i++) {
        if (i < part.screwsDriven) {
          const s = record.screws[i];
          s.progress = 1;
          s.done = true;
          s.group.position.x = s.restY.x - mm(4.5);
        }
      }
      this.rgb.register(built.group);
    }

    for (const kind of build.connectedCables) {
      this.connectCable(kind, build.routedCables.includes(kind));
    }
    for (let i = 0; i < Math.min(6, this.pasteQualityToBlobs(build.paste)); i++) {
      this.addPasteBlob(0.5 + (Math.random() - 0.5) * 0.3, 0.5 + (Math.random() - 0.5) * 0.3, 0.5);
    }
    this.rgb.setProfile(build.rgb);
  }

  private pasteQualityToBlobs(q: Build['paste']): number {
    return q === 'none' ? 0 : q === 'sparse' ? 1 : q === 'good' ? 3 : 6;
  }

  reset(): void {
    this.cables.clear();
    this.rgb.clear();
    this.clearGhost();
    this.highlightSlot(null);
    this.clearPaste();
    this.placed.clear();
    this.standoffs = [];
    this.panelScrews.length = 0;
    this.tweens = [];
    this.panelOpen = false;
    this.spinSpeed = 0;
    this.targetSpin = 0;
    this.caseBuilt = null;
    this.layout = null;
    for (const child of [...this.root.children]) {
      if (child === this.cables.group || child === this.pasteBlobs) continue;
      this.root.remove(child);
      child.traverse((o) => {
        const mesh = o as THREE.Mesh;
        if (mesh.isMesh && mesh.geometry.userData.shared !== true) mesh.geometry.dispose();
      });
    }
  }
}
