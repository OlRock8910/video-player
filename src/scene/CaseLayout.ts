import * as THREE from 'three';
import { mm } from './Materials';
import type { CaseComponent, Slot } from '../data/types';

/**
 * Where each slot physically sits inside a given case, in the case's local
 * space. Derived from the case dimensions so a bigger chassis really does have
 * more room, and every install animation lands somewhere plausible.
 */
export interface SlotAnchor {
  slot: Slot;
  position: THREE.Vector3;
  /** Euler rotation the part must end up at. */
  rotation: THREE.Euler;
  /** Radius used for snap detection while dragging. */
  snapRadius: number;
  label: string;
}

export interface CaseLayout {
  anchors: Map<Slot, SlotAnchor>;
  /** Where the side panel slides to when removed. */
  panelOpenOffset: THREE.Vector3;
  /** Camera focus point for the interior. */
  interiorFocus: THREE.Vector3;
  /** Points along the routing path behind the tray, for cable rendering. */
  routingPath: THREE.Vector3[];
  width: number;
  height: number;
  depth: number;
}

export function computeLayout(c: CaseComponent): CaseLayout {
  const W = mm(c.dimensions.thickness);
  const H = mm(c.dimensions.height);
  const D = mm(c.dimensions.length);

  // The motherboard tray face — everything board-mounted is relative to this.
  const trayX = -W / 2 + mm(30);
  // Boards hang from the top of the case with the rear I/O at the back.
  const boardCenterY = H * 0.56;
  const boardCenterZ = -mm(10);

  const anchors = new Map<Slot, SlotAnchor>();
  const add = (
    slot: Slot,
    position: THREE.Vector3,
    rotation: THREE.Euler,
    snapRadius: number,
    label: string
  ): void => {
    anchors.set(slot, { slot, position, rotation, snapRadius, label });
  };

  // Motherboard lies flat against the tray, facing +X (into the case).
  const boardRot = new THREE.Euler(0, Math.PI / 2, 0);
  add('mobo-tray', new THREE.Vector3(trayX, boardCenterY, boardCenterZ), boardRot, 0.9, 'Motherboard tray');

  // Board-relative offsets. The motherboard model is built in its own XY plane
  // with +Z out of the board, so after the Y-rotation, board +Z becomes world +X.
  const boardLocal = (bx: number, by: number, bz: number): THREE.Vector3 =>
    new THREE.Vector3(trayX + bz, boardCenterY + by, boardCenterZ - bx);

  const boardH = mm(c.supportedFormFactors.includes('ATX') ? 244 : 170);

  add('cpu-socket', boardLocal(0, boardH * 0.22, mm(6)), boardRot, 0.42, 'CPU socket');
  add('cooler-mount', boardLocal(0, boardH * 0.22, mm(10)), boardRot, 0.55, 'Cooler mount');

  for (let i = 0; i < 4; i++) {
    add(
      `ram-${i}` as Slot,
      boardLocal(mm(38) + i * mm(9), boardH * 0.14, mm(12)),
      new THREE.Euler(0, Math.PI / 2, Math.PI / 2),
      0.3,
      `DIMM ${i + 1}`
    );
  }

  for (let i = 0; i < 2; i++) {
    add(
      `m2-${i}` as Slot,
      boardLocal(-mm(10), -boardH * 0.02 - i * mm(58), mm(4)),
      boardRot,
      0.28,
      `M.2 ${i + 1}`
    );
  }

  add('pcie-0', boardLocal(-mm(6), -boardH * 0.12, mm(14)), boardRot, 0.75, 'PCIe x16');

  // PSU sits in a shroud at the bottom rear.
  add(
    'psu-bay',
    new THREE.Vector3(-W / 2 + mm(95), mm(55), -D / 2 + mm(c.psuClearance / 2)),
    new THREE.Euler(0, 0, 0),
    0.7,
    'PSU bay'
  );

  // Drive bays behind the tray.
  for (let i = 0; i < c.driveBays; i++) {
    add(
      `drive-bay-${i}` as Slot,
      new THREE.Vector3(-W / 2 + mm(12), mm(120) + i * mm(80), -D / 2 + mm(60)),
      new THREE.Euler(0, Math.PI / 2, 0),
      0.35,
      `Drive bay ${i + 1}`
    );
  }

  // Fan mounts, spaced across each face.
  const mountSpec: [string, number, (i: number, n: number) => THREE.Vector3, THREE.Euler][] = [
    [
      'front',
      c.fanMounts.front.count,
      (i, n) => new THREE.Vector3(0, spread(H, n, i, 0.16), D / 2 - mm(20)),
      new THREE.Euler(0, 0, 0),
    ],
    [
      'rear',
      c.fanMounts.rear.count,
      () => new THREE.Vector3(0, H * 0.7, -D / 2 + mm(18)),
      new THREE.Euler(0, 0, 0),
    ],
    [
      'top',
      c.fanMounts.top.count,
      (i, n) => new THREE.Vector3(0, H - mm(20), spread(D, n, i, 0.2) - D / 2),
      new THREE.Euler(Math.PI / 2, 0, 0),
    ],
    [
      'bottom',
      c.fanMounts.bottom.count,
      (i, n) => new THREE.Vector3(0, mm(16), spread(D, n, i, 0.2) - D / 2),
      new THREE.Euler(Math.PI / 2, 0, 0),
    ],
  ];

  for (const [name, count, pos, rot] of mountSpec) {
    for (let i = 0; i < count; i++) {
      add(`fan-${name}-${i}` as Slot, pos(i, count), rot.clone(), 0.42, `${name} fan ${i + 1}`);
    }
  }

  return {
    anchors,
    panelOpenOffset: new THREE.Vector3(W * 0.9, 0, 0),
    interiorFocus: new THREE.Vector3(0, H * 0.5, 0),
    routingPath: [
      new THREE.Vector3(trayX - mm(14), mm(70), -D / 2 + mm(40)),
      new THREE.Vector3(trayX - mm(14), H * 0.4, -D / 2 + mm(30)),
      new THREE.Vector3(trayX - mm(14), H * 0.75, mm(0)),
    ],
    width: W,
    height: H,
    depth: D,
  };
}

/** Evenly place `n` items along `total`, inset from both ends. */
function spread(total: number, n: number, i: number, inset: number): number {
  if (n <= 1) return total * 0.5;
  const usable = total * (1 - inset * 2);
  return total * inset + (usable / (n - 1)) * i;
}
