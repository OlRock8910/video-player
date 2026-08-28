import * as THREE from 'three';
import { mats, mm } from './Materials';
import type { CableKind } from '../data/types';

export interface CableRoute {
  kind: CableKind;
  from: THREE.Vector3;
  to: THREE.Vector3;
  /** Routed cables detour behind the tray; loose ones sag across the board. */
  routed: boolean;
  color: number;
}

interface LiveCable {
  kind: CableKind;
  mesh: THREE.Mesh;
  connector: THREE.Mesh;
}

/**
 * Draws each connected cable as a smooth tube (§18). A routed cable takes a
 * detour through the tray cut-out and hugs the chassis; a loose one sags in a
 * lazy catenary right across the glass, which is exactly what the score
 * punishes.
 */
export class CableRenderer {
  readonly group = new THREE.Group();
  private cables = new Map<CableKind, LiveCable>();
  /** Waypoints behind the motherboard tray. */
  private routingPath: THREE.Vector3[] = [];

  setRoutingPath(path: THREE.Vector3[]): void {
    this.routingPath = path.map((p) => p.clone());
  }

  has(kind: CableKind): boolean {
    return this.cables.has(kind);
  }

  add(route: CableRoute): void {
    this.remove(route.kind);
    const curve = this.buildCurve(route);
    const geometry = new THREE.TubeGeometry(curve, route.routed ? 26 : 18, mm(4.2), 7, false);
    const mesh = new THREE.Mesh(geometry, mats.cable(route.color));
    mesh.castShadow = true;
    mesh.name = `cable-${route.kind}`;
    mesh.userData.cableKind = route.kind;

    // A blocky connector shell at the board end sells the "snapped in" moment.
    const connector = new THREE.Mesh(
      new THREE.BoxGeometry(mm(12), mm(26), mm(12)),
      mats.plastic(0x1b1f26)
    );
    connector.position.copy(route.to);
    connector.lookAt(curve.getPoint(0.9));

    this.group.add(mesh, connector);
    this.cables.set(route.kind, { kind: route.kind, mesh, connector });
  }

  /** Re-lay an existing cable, e.g. when the player routes it behind the tray. */
  reroute(route: CableRoute): void {
    this.add(route);
  }

  remove(kind: CableKind): void {
    const live = this.cables.get(kind);
    if (!live) return;
    this.group.remove(live.mesh, live.connector);
    live.mesh.geometry.dispose();
    live.connector.geometry.dispose();
    this.cables.delete(kind);
  }

  clear(): void {
    for (const kind of [...this.cables.keys()]) this.remove(kind);
  }

  private buildCurve(route: CableRoute): THREE.CatmullRomCurve3 {
    const { from, to, routed } = route;
    if (routed && this.routingPath.length > 0) {
      // Dive behind the tray, follow the chassis, then come back out at the
      // nearest cut-out to the destination.
      const entry = this.nearest(from);
      const exit = this.nearest(to);
      const mid = this.routingPath.filter(
        (p) => p !== entry && p !== exit
      );
      const points = [from.clone(), entry.clone(), ...mid.map((p) => p.clone()), exit.clone(), to.clone()];
      // Nudge the hidden section further behind the tray so it reads as hidden.
      for (let i = 1; i < points.length - 1; i++) points[i].x -= mm(6);
      return new THREE.CatmullRomCurve3(points, false, 'catmullrom', 0.4);
    }

    // Loose: a sagging arc that bows out toward the viewer.
    const mid = from.clone().lerp(to, 0.5);
    mid.y -= from.distanceTo(to) * 0.22;
    mid.x += from.distanceTo(to) * 0.3;
    return new THREE.CatmullRomCurve3([from.clone(), mid, to.clone()], false, 'catmullrom', 0.5);
  }

  private nearest(p: THREE.Vector3): THREE.Vector3 {
    let best = this.routingPath[0];
    let bestD = Infinity;
    for (const q of this.routingPath) {
      const d = q.distanceToSquared(p);
      if (d < bestD) {
        bestD = d;
        best = q;
      }
    }
    return best;
  }
}

/** Cable colours, so the player can tell the runs apart at a glance. */
export const CABLE_COLORS: Record<CableKind, number> = {
  atx24: 0x0d0f13,
  eps8: 0x1a1420,
  pcie8: 0x201414,
  'sata-power': 0x14201a,
  'sata-data': 0x8c1f2f,
  'cpu-fan': 0x14171c,
  'front-panel': 0x2a2318,
  'rgb-header': 0x141d2a,
  'pump-power': 0x1a1a24,
};
