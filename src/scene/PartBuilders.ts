import * as THREE from 'three';
import { geo, mats, mm } from './Materials';
import { brandAccent } from '../data/brands';
import type {
  CaseComponent,
  Component,
  CoolerComponent,
  FanComponent,
  GpuComponent,
  MotherboardComponent,
  PsuComponent,
  RamComponent,
  StorageComponent,
} from '../data/types';
import { isCase, isCooler, isCpu, isFan, isGpu, isMobo, isPsu, isRam, isStorage } from '../data/types';

/**
 * Procedural geometry for every component (§51). Each builder returns a Group
 * whose origin sits at the part's natural mounting point, so installing is a
 * matter of matching the group's transform to the slot anchor.
 *
 * Meshes that light up are tagged `userData.rgbZone` and picked up by RGBManager.
 */

export interface BuiltPart {
  group: THREE.Group;
  /** Meshes that spin when the machine is running. */
  spinners: THREE.Object3D[];
  /** Materials driven by the RGB system, keyed by zone. */
  leds: THREE.MeshStandardMaterial[];
}

const empty = (): BuiltPart => ({ group: new THREE.Group(), spinners: [], leds: [] });

/** Small helper: an M3-ish screw with a visible cross head. */
export function buildScrew(color = 0x8c949e): THREE.Group {
  const g = new THREE.Group();
  const head = new THREE.Mesh(geo.cylinder(mm(3.2), mm(3.2), mm(1.4), 10), mats.aluminium(color));
  head.rotation.x = Math.PI / 2;
  const shaft = new THREE.Mesh(geo.cylinder(mm(1.6), mm(1.6), mm(5), 8), mats.aluminium(color));
  shaft.rotation.x = Math.PI / 2;
  shaft.position.z = -mm(3.2);
  // Cross slot, so the player can see it turn.
  const slotA = new THREE.Mesh(geo.box(mm(4.4), mm(0.7), mm(0.5)), mats.plastic(0x05070a));
  slotA.position.z = mm(0.75);
  const slotB = slotA.clone();
  slotB.rotation.z = Math.PI / 2;
  g.add(head, shaft, slotA, slotB);
  return g;
}

/** Standoff pillar the motherboard screws into. */
export function buildStandoff(): THREE.Group {
  const g = new THREE.Group();
  const body = new THREE.Mesh(geo.cylinder(mm(2.6), mm(3), mm(6), 6), mats.aluminium(0xc9a227));
  body.rotation.x = Math.PI / 2;
  body.position.z = mm(3);
  g.add(body);
  return g;
}

/* ------------------------------------------------------------------ */
/* Case                                                                */
/* ------------------------------------------------------------------ */

export interface BuiltCase extends BuiltPart {
  /** The removable side panel, animated during the opening sequence (§8). */
  sidePanel: THREE.Group;
  /** Where the panel's four screws live. */
  panelScrewAnchors: THREE.Vector3[];
  /** Interior anchor group — all installed parts are parented here. */
  interior: THREE.Group;
  /** Front power button mesh. */
  powerButton: THREE.Mesh;
  powerLed: THREE.MeshStandardMaterial;
}

export function buildCase(c: CaseComponent, quality: 'low' | 'high'): BuiltCase {
  const g = new THREE.Group();
  const W = mm(c.dimensions.thickness); // width  (x)
  const H = mm(c.dimensions.height); // height (y)
  const D = mm(c.dimensions.length); // depth  (z)
  const t = mm(1.2); // panel thickness

  const shell = mats.steel(c.tier === 'legendary' ? 0x2a2f38 : 0x15181d, 0.55);

  // Floor, ceiling, back, front, and the far side — the near side is removable.
  const floor = new THREE.Mesh(geo.box(W, t, D), shell);
  floor.position.set(0, t / 2, 0);
  const roof = new THREE.Mesh(geo.box(W, t, D), shell);
  roof.position.set(0, H - t / 2, 0);
  const back = new THREE.Mesh(geo.box(W, H, t), shell);
  back.position.set(0, H / 2, -D / 2 + t / 2);
  const farSide = new THREE.Mesh(geo.box(t, H, D), shell);
  farSide.position.set(-W / 2 + t / 2, H / 2, 0);
  for (const m of [floor, roof, back, farSide]) {
    m.castShadow = true;
    m.receiveShadow = true;
  }
  g.add(floor, roof, back, farSide);

  // Front: mesh grille for airflow cases, solid for the quiet one.
  const frontMat = c.airflowQuality > 0.7 ? mats.plastic(0x0c0e12, 0.95) : mats.steel(0x121519, 0.7);
  const front = new THREE.Mesh(geo.box(W, H, t), frontMat);
  front.position.set(0, H / 2, D / 2 - t / 2);
  front.castShadow = true;
  g.add(front);

  // Grille slots so the front reads as mesh rather than a slab.
  if (c.airflowQuality > 0.7) {
    for (let i = 0; i < 14; i++) {
      const slot = new THREE.Mesh(geo.box(W * 0.72, mm(3), mm(0.6)), mats.plastic(0x05070a));
      slot.position.set(0, H * 0.12 + (i / 14) * H * 0.78, D / 2 + mm(0.2));
      g.add(slot);
    }
  }

  // Motherboard tray with routing cut-outs.
  const tray = new THREE.Mesh(geo.box(t, H * 0.82, D * 0.78), mats.steel(0x1a1e24, 0.6));
  tray.position.set(-W / 2 + mm(28), H * 0.5, -mm(8));
  tray.receiveShadow = true;
  g.add(tray);

  const interior = new THREE.Group();
  interior.name = 'interior';
  g.add(interior);

  /* ---- removable side panel (§8) ---------------------------------- */
  const sidePanel = new THREE.Group();
  const panelMat = c.temperedGlass ? (quality === 'high' ? mats.glass() : mats.glassCheap()) : shell;
  const panel = new THREE.Mesh(geo.box(t, H - t * 2, D - t * 2), panelMat);
  panel.castShadow = !c.temperedGlass;
  sidePanel.add(panel);
  if (c.temperedGlass) {
    // A thin metal frame reads as a real glass panel rather than a floating pane.
    const frameTop = new THREE.Mesh(geo.box(t * 1.4, mm(6), D - t * 2), mats.aluminium(0x3a4048));
    frameTop.position.y = (H - t * 2) / 2 - mm(3);
    const frameBottom = frameTop.clone();
    frameBottom.position.y = -((H - t * 2) / 2) + mm(3);
    sidePanel.add(frameTop, frameBottom);
  }
  sidePanel.position.set(W / 2 - t / 2, H / 2, 0);
  sidePanel.name = 'side-panel';
  g.add(sidePanel);

  const panelScrewAnchors = [
    new THREE.Vector3(W / 2, H - mm(14), -D / 2 + mm(14)),
    new THREE.Vector3(W / 2, mm(14), -D / 2 + mm(14)),
    new THREE.Vector3(W / 2, H - mm(14), D / 2 - mm(14)),
    new THREE.Vector3(W / 2, mm(14), D / 2 - mm(14)),
  ];

  /* ---- front I/O and power button --------------------------------- */
  const powerButton = new THREE.Mesh(geo.cylinder(mm(5), mm(5), mm(2), 16), mats.aluminium(0x6d7580));
  powerButton.rotation.x = Math.PI / 2;
  powerButton.position.set(0, H - mm(26), D / 2 + mm(0.5));
  powerButton.name = 'power-button';
  g.add(powerButton);

  const powerLed = mats.emissive(0x00d0ff, 0);
  const led = new THREE.Mesh(geo.cylinder(mm(1.6), mm(1.6), mm(1), 8), powerLed);
  led.rotation.x = Math.PI / 2;
  led.position.set(mm(14), H - mm(26), D / 2 + mm(0.6));
  g.add(led);

  // Premium cases get a lighting spine in the front edge.
  const leds: THREE.MeshStandardMaterial[] = [];
  if (c.rgb) {
    const spineMat = mats.emissive(brandAccent(c.brand), 1.2);
    spineMat.userData.rgbZone = 'case';
    const spine = new THREE.Mesh(geo.box(mm(4), H * 0.7, mm(2)), spineMat);
    spine.position.set(W / 2 - mm(6), H * 0.5, D / 2 + mm(0.8));
    g.add(spine);
    leds.push(spineMat);
  }

  // Rubber feet.
  for (const [fx, fz] of [
    [-1, -1],
    [1, -1],
    [-1, 1],
    [1, 1],
  ]) {
    const foot = new THREE.Mesh(geo.cylinder(mm(8), mm(9), mm(6), 8), mats.plastic(0x08090c));
    foot.position.set((fx * W) / 2.6, -mm(3), (fz * D) / 2.6);
    g.add(foot);
  }

  return { group: g, spinners: [], leds, sidePanel, panelScrewAnchors, interior, powerButton, powerLed };
}

/* ------------------------------------------------------------------ */
/* Motherboard                                                         */
/* ------------------------------------------------------------------ */

export function buildMotherboard(m: MotherboardComponent): BuiltPart {
  const g = new THREE.Group();
  const leds: THREE.MeshStandardMaterial[] = [];

  const sizes: Record<string, [number, number]> = {
    ITX: [170, 170],
    mATX: [244, 244],
    ATX: [305, 244],
    EATX: [330, 272],
  };
  const [wmm, hmm] = sizes[m.formFactor] ?? sizes.ATX;
  const W = mm(wmm);
  const H = mm(hmm);

  const board = new THREE.Mesh(geo.box(W, H, mm(1.6)), mats.pcb(m.tier === 'budget' ? 0x0d1a14 : 0x0a0d12));
  board.castShadow = true;
  board.receiveShadow = true;
  g.add(board);

  // CPU socket with a retention frame and arm.
  const socket = new THREE.Mesh(geo.box(mm(42), mm(42), mm(3)), mats.plastic(0x1e2229));
  socket.position.set(0, H * 0.22, mm(2.3));
  socket.name = 'cpu-socket';
  g.add(socket);
  const socketWell = new THREE.Mesh(geo.box(mm(37), mm(37), mm(1)), mats.gold());
  socketWell.position.set(0, H * 0.22, mm(3.4));
  g.add(socketWell);

  const retentionArm = new THREE.Mesh(geo.box(mm(3), mm(46), mm(2.4)), mats.aluminium(0x9aa3ad));
  retentionArm.position.set(mm(24), H * 0.22, mm(4));
  retentionArm.name = 'retention-arm';
  g.add(retentionArm);

  // DIMM slots.
  for (let i = 0; i < m.ramSlots; i++) {
    const slot = new THREE.Mesh(geo.box(mm(6), mm(133), mm(4)), mats.plastic(i % 2 === 0 ? 0x1b1f26 : 0x2a2f38));
    slot.position.set(mm(38) + i * mm(9), H * 0.14, mm(2.6));
    slot.name = `ram-slot-${i}`;
    g.add(slot);
    // Retention clips at both ends (§13).
    for (const dir of [1, -1]) {
      const clip = new THREE.Mesh(geo.box(mm(5), mm(7), mm(6)), mats.plastic(0x3a4048));
      clip.position.set(mm(38) + i * mm(9), H * 0.14 + dir * mm(70), mm(3.5));
      clip.name = `ram-clip-${i}-${dir > 0 ? 'top' : 'bottom'}`;
      g.add(clip);
    }
  }

  // PCIe x16 slot with latch.
  const pcie = new THREE.Mesh(geo.box(mm(89), mm(8), mm(6)), mats.plastic(0x2a2f38));
  pcie.position.set(-mm(6), -H * 0.12, mm(3.4));
  pcie.name = 'pcie-slot';
  g.add(pcie);
  const latch = new THREE.Mesh(geo.box(mm(5), mm(7), mm(7)), mats.plastic(0x4a515c));
  latch.position.set(-mm(52), -H * 0.12, mm(3.8));
  latch.name = 'pcie-latch';
  g.add(latch);

  // Secondary PCIe slots.
  for (let i = 1; i < m.pcieSlots; i++) {
    const s = new THREE.Mesh(geo.box(mm(60), mm(7), mm(5)), mats.plastic(0x22262d));
    s.position.set(-mm(20), -H * 0.12 - i * mm(42), mm(3));
    g.add(s);
  }

  // M.2 slots under little heatsinks (§14).
  for (let i = 0; i < m.m2Slots; i++) {
    const hs = new THREE.Mesh(geo.box(mm(80), mm(24), mm(4)), mats.aluminium(0x4a515c));
    hs.position.set(-mm(10), -H * 0.02 - i * mm(58), mm(3.5));
    hs.name = `m2-heatsink-${i}`;
    g.add(hs);
  }

  // VRM heatsinks around the socket.
  for (const [hx, hy, hw, hh] of [
    [-mm(58), H * 0.22, mm(18), mm(72)],
    [0, H * 0.38, mm(96), mm(16)],
  ]) {
    const hs = new THREE.Mesh(geo.box(hw, hh, mm(9)), mats.aluminium(0x353b44));
    hs.position.set(hx, hy, mm(5));
    hs.castShadow = true;
    g.add(hs);
  }

  // Rear I/O shroud — the part that must line up with the case cut-out (§9).
  const shroud = new THREE.Mesh(geo.box(mm(28), mm(150), mm(16)), mats.plastic(0x16191f));
  shroud.position.set(-W / 2 + mm(14), H * 0.28, mm(9));
  shroud.name = 'rear-io';
  g.add(shroud);

  if (m.rgb) {
    const glowMat = mats.emissive(brandAccent(m.brand), 1.1);
    glowMat.userData.rgbZone = 'motherboard';
    const glow = new THREE.Mesh(geo.box(mm(24), mm(140), mm(1)), glowMat);
    glow.position.set(-W / 2 + mm(14), H * 0.28, mm(17.2));
    g.add(glow);
    leds.push(glowMat);
  }

  // 24-pin and EPS headers, so the cable ends have somewhere real to land.
  const atx = new THREE.Mesh(geo.box(mm(11), mm(52), mm(11)), mats.plastic(0x2a2f38));
  atx.position.set(W / 2 - mm(10), H * 0.08, mm(6));
  atx.name = 'header-atx24';
  g.add(atx);

  const eps = new THREE.Mesh(geo.box(mm(20), mm(10), mm(11)), mats.plastic(0x2a2f38));
  eps.position.set(-mm(40), H / 2 - mm(8), mm(6));
  eps.name = 'header-eps8';
  g.add(eps);

  return { group: g, spinners: [], leds };
}

/* ------------------------------------------------------------------ */
/* CPU                                                                 */
/* ------------------------------------------------------------------ */

export function buildCpu(): BuiltPart {
  const g = new THREE.Group();
  const substrate = new THREE.Mesh(geo.box(mm(37.5), mm(37.5), mm(1.2)), mats.pcb(0x0a0d12));
  const ihs = new THREE.Mesh(geo.box(mm(30), mm(30), mm(2.4)), mats.ihs());
  ihs.position.z = mm(1.8);
  ihs.castShadow = true;

  // Orientation triangle — the thing the player has to line up (§10).
  const tri = new THREE.Mesh(
    new THREE.CircleGeometry(mm(2.4), 3),
    mats.emissive(0xffd23f, 0.9)
  );
  tri.position.set(-mm(15), -mm(15), mm(0.7));
  tri.rotation.z = Math.PI / 4;
  tri.name = 'orientation-marker';

  // Notches on two edges, matching the socket keying.
  for (const nx of [-mm(9), mm(9)]) {
    const notch = new THREE.Mesh(geo.box(mm(3), mm(1.6), mm(1.4)), mats.plastic(0x05070a));
    notch.position.set(nx, mm(18.7), 0);
    g.add(notch);
  }

  g.add(substrate, ihs, tri);
  return { group: g, spinners: [], leds: [] };
}

/* ------------------------------------------------------------------ */
/* RAM                                                                 */
/* ------------------------------------------------------------------ */

export function buildRam(r: RamComponent): BuiltPart {
  const g = new THREE.Group();
  const leds: THREE.MeshStandardMaterial[] = [];
  const H = mm(r.height);

  const pcbMesh = new THREE.Mesh(geo.box(mm(133), H * 0.55, mm(1.4)), mats.pcb(0x0a0d12));
  pcbMesh.position.y = -H * 0.22;
  g.add(pcbMesh);

  // Gold edge connector with the off-centre notch (§13).
  const contacts = new THREE.Mesh(geo.box(mm(128), mm(4), mm(1.5)), mats.gold());
  contacts.position.y = -H * 0.5 + mm(1);
  g.add(contacts);
  const notch = new THREE.Mesh(geo.box(mm(2), mm(5), mm(2)), mats.plastic(0x05070a));
  // DDR5 keys sit at a different offset than DDR4 — visible, and checked in code.
  notch.position.set(r.ramType === 'DDR5' ? -mm(14) : mm(8), -H * 0.5 + mm(1), 0);
  notch.name = 'ram-notch';
  g.add(notch);

  // Heatspreader.
  const spreader = new THREE.Mesh(geo.box(mm(133), H * 0.72, mm(6)), mats.aluminium(brandAccent(r.brand)));
  spreader.position.y = mm(2);
  spreader.castShadow = true;
  g.add(spreader);

  if (r.rgb) {
    const barMat = mats.emissive(brandAccent(r.brand), 1.5);
    barMat.userData.rgbZone = 'ram';
    const bar = new THREE.Mesh(geo.box(mm(126), mm(5), mm(6.4)), barMat);
    bar.position.y = H * 0.5 - mm(3);
    g.add(bar);
    leds.push(barMat);
  }

  return { group: g, spinners: [], leds };
}

/* ------------------------------------------------------------------ */
/* GPU — size scales with the catalog dimensions (§31)                 */
/* ------------------------------------------------------------------ */

export function buildGpu(gpu: GpuComponent): BuiltPart {
  const g = new THREE.Group();
  const leds: THREE.MeshStandardMaterial[] = [];
  const L = mm(gpu.dimensions.length);
  const H = mm(gpu.dimensions.height);
  const T = mm(gpu.dimensions.thickness);
  const accent = brandAccent(gpu.brand);

  const board = new THREE.Mesh(geo.box(L, H * 0.78, mm(1.6)), mats.pcb(0x0a0d12));
  board.position.y = mm(6);
  g.add(board);

  // Edge connector and the keying notch.
  const contacts = new THREE.Mesh(geo.box(mm(84), mm(6), mm(1.8)), mats.gold());
  contacts.position.set(L / 2 - mm(58), -H * 0.32, 0);
  g.add(contacts);

  // Cooler shroud.
  const shroud = new THREE.Mesh(geo.box(L, H * 0.86, T), mats.plastic(0x16191f, 0.7));
  shroud.position.set(0, mm(8), T / 2 + mm(1));
  shroud.castShadow = true;
  g.add(shroud);

  // Backplate.
  const backplate = new THREE.Mesh(geo.box(L * 0.97, H * 0.8, mm(2)), mats.aluminium(0x2b3038));
  backplate.position.set(0, mm(8), -mm(2.2));
  g.add(backplate);

  // Fans — count scales with card size, which is what makes big cards read big.
  const fanCount = L > mm(340) ? 3 : L > mm(230) ? 2 : 1;
  const spinners: THREE.Object3D[] = [];
  const fanRadius = Math.min(H * 0.36, L / (fanCount * 2.3));
  for (let i = 0; i < fanCount; i++) {
    const cx = -L / 2 + (L / (fanCount + 1)) * (i + 1);
    const housing = new THREE.Mesh(geo.cylinder(fanRadius, fanRadius, mm(3), 20), mats.plastic(0x0c0e12));
    housing.rotation.x = Math.PI / 2;
    housing.position.set(cx, mm(8), T + mm(0.5));
    g.add(housing);
    const rotor = buildFanRotor(fanRadius * 0.92, accent);
    rotor.position.set(cx, mm(8), T + mm(1.6));
    g.add(rotor);
    spinners.push(rotor);
  }

  // Power connectors along the top edge (§16).
  for (let i = 0; i < gpu.powerConnectors; i++) {
    const conn = new THREE.Mesh(geo.box(mm(20), mm(9), mm(11)), mats.plastic(0x2a2f38));
    conn.position.set(-L / 2 + mm(40) + i * mm(24), H * 0.45, T / 2);
    conn.name = `gpu-power-${i}`;
    g.add(conn);
  }

  // Rear bracket with display outputs.
  const bracket = new THREE.Mesh(geo.box(mm(2), H * 1.15, T + mm(6)), mats.aluminium(0x8f97a2));
  bracket.position.set(L / 2 + mm(1), mm(2), T / 2);
  bracket.name = 'gpu-bracket';
  g.add(bracket);
  for (let i = 0; i < 3; i++) {
    const port = new THREE.Mesh(geo.box(mm(3), mm(7), mm(16)), mats.plastic(0x05070a));
    port.position.set(L / 2 + mm(2), -H * 0.2 + i * mm(20), T / 2);
    g.add(port);
  }

  if (gpu.rgb) {
    const logoMat = mats.emissive(accent, 1.4);
    logoMat.userData.rgbZone = 'gpu';
    const logo = new THREE.Mesh(geo.box(L * 0.34, mm(7), mm(1)), logoMat);
    logo.position.set(0, H * 0.44, T / 2 + mm(1));
    g.add(logo);
    leds.push(logoMat);

    const edgeMat = mats.emissive(accent, 0.9);
    edgeMat.userData.rgbZone = 'gpu';
    const edge = new THREE.Mesh(geo.box(L * 0.9, mm(2.4), mm(1)), edgeMat);
    edge.position.set(0, -H * 0.42, T + mm(1));
    g.add(edge);
    leds.push(edgeMat);
  }

  return { group: g, spinners, leds };
}

/** Shared rotor used by GPU fans, case fans and air coolers. */
function buildFanRotor(radius: number, accent: number): THREE.Group {
  const rotor = new THREE.Group();
  const hub = new THREE.Mesh(geo.cylinder(radius * 0.3, radius * 0.3, mm(4), 12), mats.plastic(0x1a1e24));
  hub.rotation.x = Math.PI / 2;
  rotor.add(hub);
  const bladeGeo = geo.box(radius * 0.95, radius * 0.42, mm(0.9));
  for (let i = 0; i < 7; i++) {
    const blade = new THREE.Mesh(bladeGeo, mats.plastic(0x2e343d, 0.8));
    const a = (i / 7) * Math.PI * 2;
    blade.position.set(Math.cos(a) * radius * 0.5, Math.sin(a) * radius * 0.5, 0);
    blade.rotation.z = a + 0.5;
    blade.rotation.y = 0.42;
    rotor.add(blade);
  }
  const dot = new THREE.Mesh(geo.cylinder(radius * 0.16, radius * 0.16, mm(4.4), 10), mats.aluminium(accent));
  dot.rotation.x = Math.PI / 2;
  rotor.add(dot);
  return rotor;
}

/* ------------------------------------------------------------------ */
/* Case fan                                                            */
/* ------------------------------------------------------------------ */

export function buildFan(f: FanComponent): BuiltPart {
  const g = new THREE.Group();
  const leds: THREE.MeshStandardMaterial[] = [];
  const S = mm(f.size);
  const accent = brandAccent(f.brand);

  // Square frame built from four bars, so the centre stays open.
  const barLong = geo.box(S, mm(9), mm(25));
  for (const dy of [S / 2 - mm(4.5), -S / 2 + mm(4.5)]) {
    const bar = new THREE.Mesh(barLong, mats.plastic(0x14171c));
    bar.position.y = dy;
    g.add(bar);
  }
  const barShort = geo.box(mm(9), S - mm(18), mm(25));
  for (const dx of [S / 2 - mm(4.5), -S / 2 + mm(4.5)]) {
    const bar = new THREE.Mesh(barShort, mats.plastic(0x14171c));
    bar.position.x = dx;
    g.add(bar);
  }

  const rotor = buildFanRotor(S * 0.44, accent);
  g.add(rotor);

  if (f.rgb) {
    const ringMat = mats.emissive(accent, 1.6);
    ringMat.userData.rgbZone = 'fans';
    const ring = new THREE.Mesh(new THREE.TorusGeometry(S * 0.44, mm(2.4), 6, 24), ringMat);
    ring.position.z = mm(11);
    g.add(ring);
    leds.push(ringMat);
  }

  // Airflow arrow moulded into the frame (§17).
  const arrow = new THREE.Mesh(new THREE.ConeGeometry(mm(5), mm(10), 3), mats.plastic(0x4a515c));
  arrow.position.set(S / 2 - mm(4.5), 0, mm(9));
  arrow.rotation.x = Math.PI / 2;
  arrow.name = 'airflow-arrow';
  g.add(arrow);

  return { group: g, spinners: [rotor], leds };
}

/* ------------------------------------------------------------------ */
/* Coolers                                                             */
/* ------------------------------------------------------------------ */

export function buildCooler(c: CoolerComponent): BuiltPart {
  const g = new THREE.Group();
  const leds: THREE.MeshStandardMaterial[] = [];
  const spinners: THREE.Object3D[] = [];
  const accent = brandAccent(c.brand);

  if (c.coolerType === 'aio') {
    // Pump head sits on the CPU; the radiator is a separate child the install
    // animation carries to the case mount.
    const pump = new THREE.Group();
    const body = new THREE.Mesh(geo.cylinder(mm(32), mm(34), mm(45), 20), mats.aluminium(0x2b3038));
    body.rotation.x = Math.PI / 2;
    body.position.z = mm(22);
    pump.add(body);
    const capMat = mats.emissive(accent, 1.5);
    capMat.userData.rgbZone = 'aio';
    const cap = new THREE.Mesh(geo.cylinder(mm(26), mm(26), mm(2), 20), capMat);
    cap.rotation.x = Math.PI / 2;
    cap.position.z = mm(45);
    pump.add(cap);
    leds.push(capMat);
    pump.name = 'pump';
    g.add(pump);

    // Tubes drawn as a curve so they bend naturally (§12).
    const radLen = mm(c.radiatorSize);
    const tubeCurve = new THREE.CatmullRomCurve3([
      new THREE.Vector3(mm(28), mm(10), mm(30)),
      new THREE.Vector3(mm(70), mm(60), mm(20)),
      new THREE.Vector3(mm(90), mm(140), 0),
      new THREE.Vector3(mm(60), radLen * 0.42, -mm(10)),
    ]);
    for (const off of [-mm(10), mm(10)]) {
      const tube = new THREE.Mesh(
        new THREE.TubeGeometry(tubeCurve, 18, mm(6), 8, false),
        mats.cable(0x1a1d22)
      );
      tube.position.x = off;
      tube.name = 'aio-tube';
      g.add(tube);
    }

    const rad = new THREE.Group();
    const radBody = new THREE.Mesh(geo.box(mm(30), radLen, mm(120)), mats.aluminium(0x3a4048));
    rad.add(radBody);
    // Fin stack.
    for (let i = 0; i < 12; i++) {
      const fin = new THREE.Mesh(geo.box(mm(31), mm(2), mm(112)), mats.aluminium(0x4a515c));
      fin.position.y = -radLen / 2 + (i / 12) * radLen + mm(8);
      rad.add(fin);
    }
    const fanCount = c.radiatorSize / 120;
    for (let i = 0; i < fanCount; i++) {
      const rotor = buildFanRotor(mm(52), accent);
      rotor.rotation.y = Math.PI / 2;
      rotor.position.set(mm(22), -radLen / 2 + mm(60) + i * mm(120), 0);
      rad.add(rotor);
      spinners.push(rotor);
    }
    rad.name = 'radiator';
    rad.position.set(mm(60), mm(200), 0);
    g.add(rad);
  } else {
    // Air / tower cooler: baseplate, heatpipes, fin stack, fan.
    const base = new THREE.Mesh(geo.box(mm(48), mm(48), mm(8)), mats.copper());
    base.position.z = mm(4);
    g.add(base);

    const height = mm(c.height);
    const pipeCount = c.coolerType === 'tower' ? 6 : 3;
    for (let i = 0; i < pipeCount; i++) {
      const x = -mm(18) + (i / Math.max(1, pipeCount - 1)) * mm(36);
      const pipe = new THREE.Mesh(geo.cylinder(mm(3), mm(3), height * 0.8, 8), mats.copper());
      pipe.position.set(x, 0, mm(8) + (height * 0.8) / 2);
      pipe.rotation.x = Math.PI / 2;
      g.add(pipe);
    }

    const finCount = c.coolerType === 'tower' ? 34 : 16;
    const finW = c.coolerType === 'tower' ? mm(120) : mm(88);
    const finD = c.coolerType === 'tower' ? mm(52) : mm(80);
    const stackBase = c.coolerType === 'tower' ? height * 0.3 : mm(16);
    const stackTop = height - mm(6);
    for (let i = 0; i < finCount; i++) {
      const fin = new THREE.Mesh(geo.box(finW, finD, mm(0.6)), mats.aluminium(0x9aa3ad));
      fin.position.z = stackBase + (i / finCount) * (stackTop - stackBase);
      g.add(fin);
    }

    if (c.coolerType !== 'stock') {
      const rotor = buildFanRotor(mm(50), accent);
      rotor.position.set(0, finD / 2 + mm(14), (stackBase + stackTop) / 2);
      rotor.rotation.x = Math.PI / 2;
      g.add(rotor);
      spinners.push(rotor);
    } else {
      const rotor = buildFanRotor(mm(42), accent);
      rotor.position.z = height - mm(4);
      g.add(rotor);
      spinners.push(rotor);
    }
  }

  return { group: g, spinners, leds };
}

/* ------------------------------------------------------------------ */
/* PSU                                                                 */
/* ------------------------------------------------------------------ */

export function buildPsu(p: PsuComponent): BuiltPart {
  const g = new THREE.Group();
  const D = mm(p.depth);
  const body = new THREE.Mesh(geo.box(mm(150), mm(86), D), mats.steel(0x101317, 0.6));
  body.castShadow = true;
  g.add(body);

  // Intake fan on the underside.
  const rotor = buildFanRotor(mm(58), brandAccent(p.brand));
  rotor.rotation.x = Math.PI / 2;
  rotor.position.y = -mm(44);
  g.add(rotor);

  const grille = new THREE.Mesh(geo.cylinder(mm(60), mm(60), mm(1), 20), mats.plastic(0x05070a));
  grille.rotation.x = Math.PI / 2;
  grille.position.y = -mm(44.5);
  g.add(grille);

  // Modular connector bank on the front face (§15).
  if (p.modular !== 'none') {
    for (let i = 0; i < 6; i++) {
      const port = new THREE.Mesh(geo.box(mm(18), mm(9), mm(4)), mats.plastic(0x2a2f38));
      port.position.set(-mm(50) + (i % 3) * mm(38), mm(18) - Math.floor(i / 3) * mm(22), D / 2 + mm(1));
      g.add(port);
    }
  }

  const badge = new THREE.Mesh(geo.box(mm(40), mm(18), mm(0.6)), mats.aluminium(brandAccent(p.brand)));
  badge.position.set(0, mm(10), -D / 2 - mm(0.5));
  g.add(badge);

  return { group: g, spinners: [rotor], leds: [] };
}

/* ------------------------------------------------------------------ */
/* Storage                                                             */
/* ------------------------------------------------------------------ */

export function buildStorage(s: StorageComponent): BuiltPart {
  const g = new THREE.Group();
  if (s.kind === 'm2') {
    const board = new THREE.Mesh(geo.box(mm(80), mm(22), mm(1.4)), mats.pcb(0x0a2318));
    g.add(board);
    for (const cx of [-mm(18), mm(10)]) {
      const chip = new THREE.Mesh(geo.box(mm(14), mm(12), mm(1.4)), mats.plastic(0x1a1e24));
      chip.position.set(cx, 0, mm(1.4));
      g.add(chip);
    }
    const contacts = new THREE.Mesh(geo.box(mm(9), mm(20), mm(1.5)), mats.gold());
    contacts.position.set(mm(35), 0, 0);
    g.add(contacts);
    const notch = new THREE.Mesh(geo.box(mm(2), mm(3), mm(2)), mats.plastic(0x05070a));
    notch.position.set(mm(31), mm(4), 0);
    g.add(notch);
  } else {
    const shell = new THREE.Mesh(geo.box(mm(100), mm(70), mm(7)), mats.aluminium(0x3a4048));
    shell.castShadow = true;
    g.add(shell);
    const label = new THREE.Mesh(geo.box(mm(70), mm(40), mm(0.4)), mats.plastic(brandAccent(s.brand), 0.9));
    label.position.z = mm(3.7);
    g.add(label);
    // SATA data + power tongues.
    const data = new THREE.Mesh(geo.box(mm(4), mm(14), mm(6)), mats.plastic(0x1a1e24));
    data.position.set(-mm(48), mm(20), 0);
    data.name = 'sata-data-port';
    g.add(data);
    const power = new THREE.Mesh(geo.box(mm(4), mm(22), mm(6)), mats.plastic(0x1a1e24));
    power.position.set(-mm(48), -mm(6), 0);
    power.name = 'sata-power-port';
    g.add(power);
  }
  return { group: g, spinners: [], leds: [] };
}

/* ------------------------------------------------------------------ */
/* Dispatcher                                                          */
/* ------------------------------------------------------------------ */

export function buildComponent(c: Component, quality: 'low' | 'high' = 'high'): BuiltPart {
  if (isCase(c)) return buildCase(c, quality);
  if (isMobo(c)) return buildMotherboard(c);
  if (isCpu(c)) return buildCpu();
  if (isRam(c)) return buildRam(c);
  if (isGpu(c)) return buildGpu(c);
  if (isFan(c)) return buildFan(c);
  if (isCooler(c)) return buildCooler(c);
  if (isPsu(c)) return buildPsu(c);
  if (isStorage(c)) return buildStorage(c);
  return empty();
}
