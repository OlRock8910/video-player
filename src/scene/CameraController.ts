import * as THREE from 'three';
import { settings } from '../core/Settings';

/**
 * Mobile orbit camera (§5).
 *
 * One finger drags to orbit, two fingers pinch to zoom and drag to pan, and a
 * double tap focuses. Everything is critically damped toward a target so the
 * camera never snaps — including scripted moves, which simply retarget the same
 * springs the player's fingers do.
 */
export class CameraController {
  readonly camera: THREE.PerspectiveCamera;
  private dom: HTMLElement;

  /** Spherical coordinates around `target`. */
  private theta = Math.PI * 0.28;
  private phi = Math.PI * 0.42;
  private distance = 12;
  private target = new THREE.Vector3(0, 2.4, 0);

  private goalTheta = this.theta;
  private goalPhi = this.phi;
  private goalDistance = this.distance;
  private goalTarget = this.target.clone();

  private minDistance = 1.6;
  private maxDistance = 26;
  private minPhi = 0.12;
  private maxPhi = Math.PI * 0.86;

  /** Blocked while a cinematic owns the camera. */
  private locked = false;
  private enabled = true;

  private pointers = new Map<number, { x: number; y: number }>();
  private lastPinchDistance = 0;
  private lastTapTime = 0;
  private lastTapPos = { x: 0, y: 0 };
  private dragged = false;

  /** Called on a double-tap that did not land on a component. */
  onDoubleTap: ((x: number, y: number) => void) | null = null;
  /** Called on a tap that could be a selection. */
  onTap: ((x: number, y: number) => void) | null = null;
  /** Called when a drag starts/moves/ends, so the interaction layer can claim it. */
  onDragStart: ((x: number, y: number) => boolean) | null = null;
  onDragMove: ((x: number, y: number) => void) | null = null;
  onDragEnd: ((x: number, y: number) => void) | null = null;
  onHoldStart: ((x: number, y: number) => boolean) | null = null;
  onHoldEnd: (() => void) | null = null;

  /** True while the interaction layer has taken over the current gesture. */
  private claimed = false;
  private holdTimer: number | null = null;
  private holding = false;

  constructor(dom: HTMLElement, aspect: number) {
    this.dom = dom;
    this.camera = new THREE.PerspectiveCamera(52, aspect, 0.05, 400);
    this.updateCamera(1);
    this.attach();
  }

  private attach(): void {
    const opts = { passive: false } as AddEventListenerOptions;
    this.dom.addEventListener('pointerdown', this.onPointerDown, opts);
    this.dom.addEventListener('pointermove', this.onPointerMove, opts);
    this.dom.addEventListener('pointerup', this.onPointerUp, opts);
    this.dom.addEventListener('pointercancel', this.onPointerUp, opts);
    this.dom.addEventListener('wheel', this.onWheel, opts);
    this.dom.addEventListener('contextmenu', (e) => e.preventDefault());
  }

  dispose(): void {
    this.dom.removeEventListener('pointerdown', this.onPointerDown);
    this.dom.removeEventListener('pointermove', this.onPointerMove);
    this.dom.removeEventListener('pointerup', this.onPointerUp);
    this.dom.removeEventListener('pointercancel', this.onPointerUp);
    this.dom.removeEventListener('wheel', this.onWheel);
    this.clearHold();
  }

  setEnabled(v: boolean): void {
    this.enabled = v;
    if (!v) this.pointers.clear();
  }

  private clearHold(): void {
    if (this.holdTimer !== null) {
      window.clearTimeout(this.holdTimer);
      this.holdTimer = null;
    }
    if (this.holding) {
      this.holding = false;
      this.onHoldEnd?.();
    }
  }

  private onPointerDown = (e: PointerEvent): void => {
    if (!this.enabled) return;
    this.dom.setPointerCapture?.(e.pointerId);
    this.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    this.dragged = false;
    this.claimed = false;

    if (this.pointers.size === 1) {
      // Give the interaction layer first refusal on this gesture.
      this.claimed = this.onDragStart?.(e.clientX, e.clientY) ?? false;
      if (!this.claimed) {
        // Not a part drag — it might still be a press-and-hold on a screw.
        this.holdTimer = window.setTimeout(() => {
          this.holdTimer = null;
          if (this.dragged) return;
          this.holding = this.onHoldStart?.(e.clientX, e.clientY) ?? false;
        }, 180);
      }
    } else if (this.pointers.size === 2) {
      this.clearHold();
      this.claimed = false;
      this.lastPinchDistance = this.pinchDistance();
    }
  };

  private onPointerMove = (e: PointerEvent): void => {
    if (!this.enabled) return;
    const prev = this.pointers.get(e.pointerId);
    if (!prev) return;
    const dx = e.clientX - prev.x;
    const dy = e.clientY - prev.y;
    this.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (Math.abs(dx) > 1.5 || Math.abs(dy) > 1.5) this.dragged = true;

    if (this.holding) {
      // A hold that starts moving is still a hold (screwing tolerates wobble).
      return;
    }
    if (this.claimed) {
      this.onDragMove?.(e.clientX, e.clientY);
      return;
    }
    if (this.locked) return;

    if (this.pointers.size === 1) {
      const s = settings.get();
      const sens = 0.0055 * s.cameraSensitivity;
      this.goalTheta -= dx * sens * (s.invertCameraX ? -1 : 1);
      this.goalPhi -= dy * sens;
      this.goalPhi = THREE.MathUtils.clamp(this.goalPhi, this.minPhi, this.maxPhi);
    } else if (this.pointers.size === 2) {
      const dist = this.pinchDistance();
      if (this.lastPinchDistance > 0) {
        const scale = this.lastPinchDistance / Math.max(1, dist);
        this.goalDistance = THREE.MathUtils.clamp(
          this.goalDistance * scale,
          this.minDistance,
          this.maxDistance
        );
      }
      this.lastPinchDistance = dist;
      // Two-finger drag pans in the camera's own plane.
      this.pan(dx, dy);
    }
  };

  private onPointerUp = (e: PointerEvent): void => {
    const had = this.pointers.has(e.pointerId);
    this.pointers.delete(e.pointerId);
    this.dom.releasePointerCapture?.(e.pointerId);
    if (this.pointers.size < 2) this.lastPinchDistance = 0;
    if (!had || !this.enabled) return;

    if (this.holdTimer !== null) {
      window.clearTimeout(this.holdTimer);
      this.holdTimer = null;
    }
    if (this.holding) {
      this.holding = false;
      this.onHoldEnd?.();
      return;
    }
    if (this.claimed) {
      this.onDragEnd?.(e.clientX, e.clientY);
      this.claimed = false;
      return;
    }
    if (this.dragged) return;

    // A stationary release is a tap; two in quick succession is a double tap.
    const now = performance.now();
    const near =
      Math.abs(e.clientX - this.lastTapPos.x) < 34 && Math.abs(e.clientY - this.lastTapPos.y) < 34;
    if (now - this.lastTapTime < 290 && near) {
      this.lastTapTime = 0;
      this.onDoubleTap?.(e.clientX, e.clientY);
    } else {
      this.lastTapTime = now;
      this.lastTapPos = { x: e.clientX, y: e.clientY };
      this.onTap?.(e.clientX, e.clientY);
    }
  };

  private onWheel = (e: WheelEvent): void => {
    if (!this.enabled || this.locked) return;
    e.preventDefault();
    this.goalDistance = THREE.MathUtils.clamp(
      this.goalDistance * (1 + Math.sign(e.deltaY) * 0.12),
      this.minDistance,
      this.maxDistance
    );
  };

  private pinchDistance(): number {
    const [a, b] = [...this.pointers.values()];
    if (!a || !b) return 0;
    return Math.hypot(a.x - b.x, a.y - b.y);
  }

  private pan(dx: number, dy: number): void {
    const right = new THREE.Vector3();
    const up = new THREE.Vector3();
    this.camera.matrixWorld.extractBasis(right, up, new THREE.Vector3());
    const scale = this.distance * 0.0016;
    this.goalTarget.addScaledVector(right, -dx * scale);
    this.goalTarget.addScaledVector(up, dy * scale);
  }

  /**
   * Distance at which an object of the given size fits the viewport with a
   * little margin. Phones are portrait, so the horizontal field of view is the
   * binding constraint — framing by height alone crops the case off the sides.
   */
  distanceToFit(size: number, margin = 1.18): number {
    const vFov = THREE.MathUtils.degToRad(this.camera.fov);
    const byHeight = size / (2 * Math.tan(vFov / 2));
    const hFov = 2 * Math.atan(Math.tan(vFov / 2) * this.camera.aspect);
    const byWidth = size / (2 * Math.tan(hFov / 2));
    return Math.max(byHeight, byWidth) * margin;
  }

  /** Frame a point so an object of `size` units around it fits on screen. */
  frame(point: THREE.Vector3, size: number, opts: { theta?: number; phi?: number; margin?: number } = {}): void {
    this.focusOn(point, this.distanceToFit(size, opts.margin ?? 1.18), opts);
  }

  /** Scripted move used before an important installation (§5). */
  focusOn(point: THREE.Vector3, distance: number, opts: { theta?: number; phi?: number } = {}): void {
    this.goalTarget.copy(point);
    this.goalDistance = THREE.MathUtils.clamp(distance, this.minDistance, this.maxDistance);
    if (opts.theta !== undefined) this.goalTheta = opts.theta;
    if (opts.phi !== undefined) this.goalPhi = THREE.MathUtils.clamp(opts.phi, this.minPhi, this.maxPhi);
  }

  /** Lock input during a cinematic; the springs keep running. */
  setLocked(v: boolean): void {
    this.locked = v;
  }

  setLimits(minDistance: number, maxDistance: number): void {
    this.minDistance = minDistance;
    this.maxDistance = maxDistance;
    this.goalDistance = THREE.MathUtils.clamp(this.goalDistance, minDistance, maxDistance);
  }

  /** Slow turntable used by the menu and showcase (§33). */
  orbitBy(radians: number): void {
    this.goalTheta += radians;
  }

  get orbitAngle(): number {
    return this.theta;
  }

  update(dt: number): void {
    // Exponential smoothing, frame-rate independent.
    const reduce = settings.get().reduceMotion;
    const k = 1 - Math.exp(-(reduce ? 26 : 9) * dt);
    this.theta += (this.goalTheta - this.theta) * k;
    this.phi += (this.goalPhi - this.phi) * k;
    this.distance += (this.goalDistance - this.distance) * k;
    this.target.lerp(this.goalTarget, k);
    this.updateCamera(k);
  }

  private updateCamera(_k: number): void {
    const sinPhi = Math.sin(this.phi);
    this.camera.position.set(
      this.target.x + this.distance * sinPhi * Math.sin(this.theta),
      this.target.y + this.distance * Math.cos(this.phi),
      this.target.z + this.distance * sinPhi * Math.cos(this.theta)
    );
    this.camera.lookAt(this.target);
  }

  resize(aspect: number): void {
    this.camera.aspect = aspect;
    this.camera.updateProjectionMatrix();
  }

  /** Screen point to a normalised device coordinate for raycasting. */
  ndc(x: number, y: number, out = new THREE.Vector2()): THREE.Vector2 {
    const r = this.dom.getBoundingClientRect();
    out.set(((x - r.left) / r.width) * 2 - 1, -((y - r.top) / r.height) * 2 + 1);
    return out;
  }
}
