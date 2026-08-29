/**
 * Workbenches the player can buy (cosmetic progression).
 *
 * A desk is not a PC part — it never goes in a build and has no bearing on
 * compatibility or benchmarks. It changes how the workshop looks and how much
 * room the bench props get, which is the point: a better shop should *look*
 * like a better shop.
 *
 * Adding a desk is one entry here; SceneRoot builds whatever this describes.
 */
export interface Desk {
  id: string;
  name: string;
  maker: string;
  price: number;
  blurb: string;
  /** Reputation level needed before the shop will sell it. */
  reputationRequired?: number;

  /** Desk top, in world units (1 unit = 10cm). */
  width: number;
  depth: number;
  height: number;

  /**
   * Base colour of the desk top. Deliberately darker than the real-world
   * material: the workshop's light rig is hot, and a flat desk that renders
   * near-white pulls attention off the machine.
   */
  topColor: number;
  legColor: number;
  /** 0-1; higher is glossier. */
  gloss: number;
  /** Anti-static mat colour and size multiplier. */
  matColor: number;
  matScale: number;

  /** Under-desk lighting strip. */
  ledStrip: boolean;
  ledColor: number;
  /** Monitors standing on the desk. */
  monitors: 1 | 2;
  /** A pegboard behind the bench with tools on it. */
  pegboard: boolean;
}

export const DESKS: Desk[] = [
  {
    id: 'desk-scrap',
    name: 'Scrap Bench',
    maker: 'Salvage',
    price: 0,
    blurb: 'A chipboard slab on steel legs. It came with the unit.',
    width: 14,
    depth: 8.4,
    height: 3.6,
    topColor: 0x191410,
    legColor: 0x1a1d22,
    gloss: 0.78,
    matColor: 0x14243a,
    matScale: 1,
    ledStrip: false,
    ledColor: 0x00d0ff,
    monitors: 1,
    pegboard: false,
  },
  {
    id: 'desk-oak',
    name: 'Oak Workbench',
    maker: 'Timberline',
    price: 320,
    blurb: 'Solid oak top, properly finished. Feels like a real workshop.',
    width: 15.5,
    depth: 9,
    height: 3.7,
    topColor: 0x261a10,
    legColor: 0x22262d,
    gloss: 0.55,
    matColor: 0x16283f,
    matScale: 1.12,
    ledStrip: false,
    ledColor: 0x00d0ff,
    monitors: 1,
    pegboard: true,
  },
  {
    id: 'desk-studio',
    name: 'Studio White',
    maker: 'Northlight',
    price: 780,
    blurb: 'A pale matte top that makes small screws easy to find.',
    reputationRequired: 1,
    width: 16,
    depth: 9.4,
    height: 3.75,
    topColor: 0x56544f,
    legColor: 0x9aa1ab,
    gloss: 0.42,
    matColor: 0x1d3350,
    matScale: 1.16,
    ledStrip: true,
    ledColor: 0x9d7bff,
    monitors: 2,
    pegboard: true,
  },
  {
    id: 'desk-carbon',
    name: 'Carbon Sit-Stand',
    maker: 'Northlight',
    price: 1650,
    blurb: 'Carbon-weave top on a motorised frame, with a lit underside.',
    reputationRequired: 2,
    width: 17,
    depth: 9.8,
    height: 3.9,
    topColor: 0x121417,
    legColor: 0x33383f,
    gloss: 0.3,
    matColor: 0x122b3d,
    matScale: 1.2,
    ledStrip: true,
    ledColor: 0x00d0ff,
    monitors: 2,
    pegboard: true,
  },
  {
    id: 'desk-atelier',
    name: 'The Atelier',
    maker: 'Northlight',
    price: 4200,
    blurb: 'Machined aluminium and walnut. The bench you photograph builds on.',
    reputationRequired: 4,
    width: 18,
    depth: 10.4,
    height: 3.95,
    topColor: 0x17110c,
    legColor: 0xb9c0cb,
    gloss: 0.24,
    matColor: 0x0f2f2a,
    matScale: 1.26,
    ledStrip: true,
    ledColor: 0x00e5a0,
    monitors: 2,
    pegboard: true,
  },
];

export const DEFAULT_DESK_ID = 'desk-scrap';

export function getDesk(id: string): Desk {
  return DESKS.find((d) => d.id === id) ?? DESKS[0];
}
