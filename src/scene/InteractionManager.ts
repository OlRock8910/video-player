import * as THREE from 'three';
import type { BuildScene } from './BuildScene';
import type { CameraController } from './CameraController';
import type { BuiltPart } from './PartBuilders';
import type { Component, Slot } from '../data/types';
import { audio } from '../core/AudioManager';
import { haptics } from '../core/HapticsManager';

export type HeldRotation = 0 | 90 | 180 | 270;

export interface HeldPart {
  component: Component;
  built: BuiltPart;
  /** Target slot chosen when the player picked the part up. */
  slot: Slot;
  /** Yaw applied by the rotate button, in degrees (§10, §13). */
  rotation: HeldRotation;
  /** True while the part is close enough to the anchor to drop in. */
  overTarget: boolean;
}

export interface InteractionCallbacks {
  /** Tapped an installed part or a case feature. */
  onSelect: (info: { componentId?: string; slot?: Slot; objectName?: string }) => void;
  /** Player released a held part over a valid target. */
  onDropOnTarget: (held: HeldPart) => void;
  /** Player released a held part away from the target. */
  onDropAway: (held: HeldPart) => void;
  /** A screw finished being driven. */
  onScrewDriven: (slot: Slot, index: number) => void;
  /** A panel screw came out. */
  onPanelScrewOut: (index: number) => void;
  /** Held part moved; used to update the tooltip and ghost. */
  onHeldMoved: (held: HeldPart) => void;
  /** Player tapped the case power button. */
  onPowerButton: () => void;
}

/**
 * Turns touches into building (§6, §7).
 *
 * Dragging maps the finger onto a plane through the target anchor and facing
 * the camera, so a part follows the finger naturally at any camera angle, and
 * snapping is generous — the player never needs pixel precision (§41).
 */
export class InteractionManager {
  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private dragPlane = new THREE.Plane();
  private dragOffset = new THREE.Vector3();
  private hit = new THREE.Vector3();

  private held: HeldPart | null = null;
  private screwTarget: { slot: Slot; index: number } | null = null;
  private panelScrewTarget: number | null = null;

  /** Slots the player is allowed to drop into right now. */
  private allowedSlots: Slot[] = [];

  constructor(
    private scene: BuildScene,
    private camera: CameraController,
    private root: THREE.Object3D,
    private cb: InteractionCallbacks
  ) {
    camera.onTap = this.handleTap;
    camera.onDoubleTap = this.handleDoubleTap;
    camera.onDragStart = this.handleDragStart;
    camera.onDragMove = this.handleDragMove;
    camera.onDragEnd = this.handleDragEnd;
    camera.onHoldStart = this.handleHoldStart;
    camera.onHoldEnd = this.handleHoldEnd;
  }

  get heldPart(): HeldPart | null {
    return this.held;
  }

  setAllowedSlots(slots: Slot[]): void {
    this.allowedSlots = slots;
  }

  /**
   * Pick a part up. It appears in front of the camera, roughly where a hand
   * would hold it, then the player drags it into place.
   */
  pickUp(component: Component, slot: Slot): HeldPart {
    this.drop();
    const anchor = this.scene.anchorFor(slot);
    const spawn = new THREE.Vector3();
    if (anchor) {
      this.scene.worldAnchor(slot, spawn);
      // Offset toward the camera so the part is not inside the case already.
      const toCam = this.camera.camera.position.clone().sub(spawn).normalize();
      spawn.addScaledVector(toCam, 1.6);
    } else {
      spawn.copy(this.camera.camera.position);
      spawn.y -= 1;
    }
    this.root.worldToLocal(spawn);

    const built = this.scene.spawnLoose(component, spawn);
    this.held = { component, built, slot, rotation: 0, overTarget: false };
    this.applyHeldRotation();
    audio.play('plastic');
    haptics.fire('light');
    this.scene.showGhost(component, slot, true);
    this.cb.onHeldMoved(this.held);
    return this.held;
  }

  /** Rotate the held part 90° (§10 CPU alignment, §13 RAM notch). */
  rotateHeld(): void {
    if (!this.held) return;
    this.held.rotation = (((this.held.rotation + 90) % 360) as HeldRotation);
    this.applyHeldRotation();
    audio.play('ui-tap');
    haptics.fire('tick');
    this.cb.onHeldMoved(this.held);
  }

  private applyHeldRotation(): void {
    if (!this.held) return;
    const anchor = this.scene.anchorFor(this.held.slot);
    const base = anchor ? new THREE.Euler().copy(anchor.rotation) : new THREE.Euler();
    const q = new THREE.Quaternion().setFromEuler(base);
    // Spin about the part's own facing axis so the keying marker moves.
    const spin = new THREE.Quaternion().setFromAxisAngle(
      new THREE.Vector3(0, 0, 1),
      THREE.MathUtils.degToRad(this.held.rotation)
    );
    this.held.built.group.quaternion.copy(q.multiply(spin));
  }

  /** True when the held part's keyed orientation matches the socket. */
  isHeldAligned(): boolean {
    if (!this.held) return true;
    const c = this.held.component;
    // Only keyed parts care about rotation; everything else drops in any way up.
    if (c.category === 'cpu') return this.held.rotation === 0;
    if (c.category === 'ram') return this.held.rotation === 0 || this.held.rotation === 180;
    return true;
  }

  drop(): void {
    if (!this.held) return;
    this.held.built.group.removeFromParent();
    this.held = null;
    this.scene.clearGhost();
  }

  /** Give up the held part without installing it (§50 — always recoverable). */
  cancelHeld(): void {
    if (!this.held) return;
    audio.play('ui-back');
    this.drop();
  }

  /* ---------------------------------------------------------------- */
  /* Gesture handling                                                  */
  /* ---------------------------------------------------------------- */

  private castAt(x: number, y: number): THREE.Intersection[] {
    this.camera.ndc(x, y, this.pointer);
    this.raycaster.setFromCamera(this.pointer, this.camera.camera);
    return this.raycaster.intersectObject(this.root, true);
  }

  /** Walk up the parent chain to the nearest object carrying useful metadata. */
  private resolve(object: THREE.Object3D): {
    componentId?: string;
    slot?: Slot;
    objectName?: string;
    screwFor?: Slot;
    screwIndex?: number;
    panelScrewIndex?: number;
  } {
    let o: THREE.Object3D | null = object;
    const info: ReturnType<InteractionManager['resolve']> = {};
    while (o) {
      if (o.name && !info.objectName) info.objectName = o.name;
      if (o.userData.screwFor !== undefined && info.screwFor === undefined) {
        info.screwFor = o.userData.screwFor as Slot;
        info.screwIndex = o.userData.screwIndex as number;
      }
      if (o.userData.panelScrewIndex !== undefined && info.panelScrewIndex === undefined) {
        info.panelScrewIndex = o.userData.panelScrewIndex as number;
      }
      if (o.userData.componentId && !info.componentId) {
        info.componentId = o.userData.componentId as string;
        info.slot = o.userData.slot as Slot | undefined;
      }
      o = o.parent;
    }
    return info;
  }

  private handleTap = (x: number, y: number): void => {
    audio.unlock();
    if (this.held) {
      // Tapping while holding tries to seat the part where it stands.
      this.trySeat();
      return;
    }
    const hits = this.castAt(x, y);
    if (hits.length === 0) {
      this.cb.onSelect({});
      return;
    }
    const info = this.resolve(hits[0].object);
    if (info.objectName === 'power-button') {
      this.cb.onPowerButton();
      return;
    }
    audio.play('ui-tap');
    this.cb.onSelect({
      ...(info.componentId ? { componentId: info.componentId } : {}),
      ...(info.slot ? { slot: info.slot } : {}),
      ...(info.objectName ? { objectName: info.objectName } : {}),
    });
  };

  private handleDoubleTap = (x: number, y: number): void => {
    const hits = this.castAt(x, y);
    if (hits.length === 0) return;
    const point = hits[0].point.clone();
    this.camera.focusOn(point, 3.2);
    haptics.fire('tick');
  };

  /**
   * A drag only belongs to this manager when it starts on the held part. Every
   * other drag is a camera orbit.
   */
  private handleDragStart = (x: number, y: number): boolean => {
    if (!this.held) return false;
    const hits = this.castAt(x, y);
    const onHeld = hits.some((h) => {
      let o: THREE.Object3D | null = h.object;
      while (o) {
        if (o === this.held!.built.group) return true;
        o = o.parent;
      }
      return false;
    });

    // Dragging anywhere is allowed once a part is in hand — requiring the
    // player to hit the part itself would be fiddly on a phone (§41).
    const group = this.held.built.group;
    const worldPos = new THREE.Vector3();
    group.getWorldPosition(worldPos);

    const normal = this.camera.camera.getWorldDirection(new THREE.Vector3()).negate();
    this.dragPlane.setFromNormalAndCoplanarPoint(normal, worldPos);

    this.camera.ndc(x, y, this.pointer);
    this.raycaster.setFromCamera(this.pointer, this.camera.camera);
    if (this.raycaster.ray.intersectPlane(this.dragPlane, this.hit)) {
      this.dragOffset.copy(worldPos).sub(this.hit);
    } else {
      this.dragOffset.set(0, 0, 0);
    }
    return onHeld || true;
  };

  private handleDragMove = (x: number, y: number): void => {
    if (!this.held) return;
    this.camera.ndc(x, y, this.pointer);
    this.raycaster.setFromCamera(this.pointer, this.camera.camera);
    if (!this.raycaster.ray.intersectPlane(this.dragPlane, this.hit)) return;

    const world = this.hit.clone().add(this.dragOffset);
    const local = this.root.worldToLocal(world.clone());
    this.held.built.group.position.copy(local);

    const wasOver = this.held.overTarget;
    this.held.overTarget = this.isOverTarget();
    if (this.held.overTarget !== wasOver) {
      // Magnetic feedback the moment the part lines up.
      if (this.held.overTarget) haptics.fire('tick');
      this.scene.showGhost(this.held.component, this.held.slot, this.isHeldAligned());
    }
    this.cb.onHeldMoved(this.held);
  };

  private handleDragEnd = (): void => {
    if (!this.held) return;
    this.trySeat();
  };

  private trySeat(): void {
    if (!this.held) return;
    if (this.isOverTarget()) {
      this.cb.onDropOnTarget(this.held);
    } else {
      this.cb.onDropAway(this.held);
    }
  }

  /** Distance check against the slot anchor, in world space. */
  private isOverTarget(): boolean {
    if (!this.held) return false;
    if (!this.allowedSlots.includes(this.held.slot)) return false;
    const anchor = this.scene.anchorFor(this.held.slot);
    if (!anchor) return false;
    const target = this.scene.worldAnchor(this.held.slot);
    const current = new THREE.Vector3();
    this.held.built.group.getWorldPosition(current);
    // Generous radius; snapping should feel magnetic, not precise.
    return current.distanceTo(target) <= Math.max(0.55, anchor.snapRadius * 1.35);
  }

  /* ---------------------------------------------------------------- */
  /* Press-and-hold: screws (§7)                                       */
  /* ---------------------------------------------------------------- */

  private handleHoldStart = (x: number, y: number): boolean => {
    if (this.held) return false;
    const hits = this.castAt(x, y);
    if (hits.length === 0) return false;
    const info = this.resolve(hits[0].object);

    if (info.panelScrewIndex !== undefined) {
      this.panelScrewTarget = info.panelScrewIndex;
      return true;
    }
    if (info.screwFor !== undefined && info.screwIndex !== undefined) {
      this.screwTarget = { slot: info.screwFor, index: info.screwIndex };
      return true;
    }
    return false;
  };

  private handleHoldEnd = (): void => {
    this.screwTarget = null;
    this.panelScrewTarget = null;
  };

  /** Called every frame while a screw is held. */
  update(dt: number): void {
    if (this.panelScrewTarget !== null) {
      const out = this.scene.loosenPanelScrew(this.panelScrewTarget, dt * 0.85);
      haptics.fire('screw', 55);
      audio.play('screw');
      if (out) {
        audio.play('metal');
        haptics.fire('medium');
        this.cb.onPanelScrewOut(this.panelScrewTarget);
        this.panelScrewTarget = null;
      }
    } else if (this.screwTarget) {
      const { slot, index } = this.screwTarget;
      const done = this.scene.driveScrew(slot, index, dt * 0.9);
      haptics.fire('screw', 55);
      audio.play('screw');
      if (done) {
        audio.play('screw-done');
        haptics.fire('medium');
        this.cb.onScrewDriven(slot, index);
        this.screwTarget = null;
      }
    }
  }

  get isScrewing(): boolean {
    return this.screwTarget !== null || this.panelScrewTarget !== null;
  }

  dispose(): void {
    this.drop();
    this.camera.onTap = null;
    this.camera.onDoubleTap = null;
    this.camera.onDragStart = null;
    this.camera.onDragMove = null;
    this.camera.onDragEnd = null;
    this.camera.onHoldStart = null;
    this.camera.onHoldEnd = null;
  }
}
