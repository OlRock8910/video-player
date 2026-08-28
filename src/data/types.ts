/**
 * Component data model.
 *
 * Everything in the game — the shop, the compatibility engine, the 3D builders,
 * the benchmark — reads components through these interfaces. Adding a new GPU
 * means adding one entry to `src/data/catalog/gpus.ts`; no other file changes.
 */

export type Category =
  | 'case'
  | 'motherboard'
  | 'cpu'
  | 'cooler'
  | 'ram'
  | 'storage'
  | 'gpu'
  | 'psu'
  | 'fan';

export type Tier = 'budget' | 'standard' | 'performance' | 'enthusiast' | 'legendary';

export type Socket = 'FG-1700' | 'FG-2066' | 'NX-AM5';
export type RamType = 'DDR4' | 'DDR5';
export type FormFactor = 'ITX' | 'mATX' | 'ATX' | 'EATX';
export type CoolerType = 'stock' | 'air' | 'tower' | 'aio';
export type StorageKind = 'm2' | 'sata-ssd' | 'hdd';
export type FanSize = 120 | 140;

/** Cable kinds the player physically connects (§15). */
export type CableKind =
  | 'atx24'
  | 'eps8'
  | 'pcie8'
  | 'sata-power'
  | 'sata-data'
  | 'cpu-fan'
  | 'front-panel'
  | 'rgb-header'
  | 'pump-power';

/** Where a part physically lives once installed (§52 installation location). */
export type Slot =
  | 'case'
  | 'mobo-tray'
  | 'cpu-socket'
  | 'cooler-mount'
  | 'ram-0'
  | 'ram-1'
  | 'ram-2'
  | 'ram-3'
  | 'm2-0'
  | 'm2-1'
  | 'drive-bay-0'
  | 'drive-bay-1'
  | 'pcie-0'
  | 'psu-bay'
  | 'fan-front-0'
  | 'fan-front-1'
  | 'fan-front-2'
  | 'fan-rear-0'
  | 'fan-top-0'
  | 'fan-top-1'
  | 'fan-bottom-0';

export type FanMount = 'front' | 'rear' | 'top' | 'bottom';
/** Airflow direction relative to the case (§17). */
export type Airflow = 'intake' | 'exhaust';

/** Millimetre dimensions — the compatibility engine is purely metric. */
export interface Dimensions {
  /** Long axis: GPU length, radiator length, PSU depth, case height. */
  length: number;
  /** Cooler height / GPU height / case width. */
  height: number;
  /** GPU slot thickness in mm, PSU width, etc. */
  thickness: number;
}

export interface BaseComponent {
  id: string;
  name: string;
  brand: string;
  category: Category;
  tier: Tier;
  price: number;
  /** Watts drawn under load. Cases/coolers are ~0. */
  power: number;
  /** Marketing-ish 0-100 quality used for shop star ratings. */
  rating: number;
  /** Has addressable lighting (§32). */
  rgb: boolean;
  /** Short shop blurb. */
  blurb: string;
  /** Cables this part needs plugged in before the build can POST (§15, §19). */
  requiredCables?: CableKind[];
  /** Unlocked only at/above this reputation level index (§28). */
  reputationRequired?: number;
}

export interface CaseComponent extends BaseComponent {
  category: 'case';
  supportedFormFactors: FormFactor[];
  /** Max GPU length in mm. */
  gpuClearance: number;
  /** Max CPU cooler height in mm. */
  coolerClearance: number;
  /** Radiator lengths the case can take, in mm (240 = 2x120 etc). */
  radiatorSupport: number[];
  /** Max PSU depth in mm. */
  psuClearance: number;
  /** How many fans fit at each mount, and which sizes. */
  fanMounts: Record<FanMount, { count: number; sizes: FanSize[] }>;
  /** Drive bays for 2.5"/3.5" storage. */
  driveBays: number;
  /** 0-1: how well the case moves air on its own. Feeds the thermal model. */
  airflowQuality: number;
  /** 0-1: how good the routing space behind the tray is. Feeds cable scoring. */
  cableRouting: number;
  /** Tempered glass side panel (affects showcase + a little noise). */
  temperedGlass: boolean;
  /** Base noise damping 0-1; sound-dampened panels raise this. */
  soundDamping: number;
  dimensions: Dimensions;
}

export interface MotherboardComponent extends BaseComponent {
  category: 'motherboard';
  socket: Socket;
  formFactor: FormFactor;
  ramType: RamType;
  ramSlots: 2 | 4;
  /** Highest officially supported memory speed in MT/s. */
  maxRamSpeed: number;
  m2Slots: number;
  sataPorts: number;
  pcieSlots: number;
  /** Recommended ceiling for CPU power delivery; over this we warn (§19). */
  vrmWattage: number;
}

export interface CpuComponent extends BaseComponent {
  category: 'cpu';
  socket: Socket;
  cores: number;
  threads: number;
  /** GHz boost, used in the fictional benchmark. */
  boostClock: number;
  /** 0-100 single-thread strength. */
  singleCore: number;
  /** 0-100 multi-thread strength. */
  multiCore: number;
  ramType: RamType;
  /** Watts of heat the cooler must move at full load. */
  tdp: number;
  integratedGraphics: boolean;
  includesCooler: boolean;
}

export interface CoolerComponent extends BaseComponent {
  category: 'cooler';
  coolerType: CoolerType;
  sockets: Socket[];
  /** Watts of heat it can dissipate before temps run away. */
  tdpRating: number;
  /** Tower height in mm (air) — checked against case cooler clearance. */
  height: number;
  /** Radiator length in mm for AIOs (0 for air). */
  radiatorSize: number;
  /** dB at full tilt. */
  noise: number;
  includesPaste: boolean;
}

export interface RamComponent extends BaseComponent {
  category: 'ram';
  ramType: RamType;
  /** GB per stick. */
  capacity: number;
  /** Sticks in the kit. */
  sticks: number;
  speed: number;
  /** Lower is better. */
  latency: number;
  /** Heatspreader height in mm — tall RAM fights big air coolers. */
  height: number;
}

export interface StorageComponent extends BaseComponent {
  category: 'storage';
  kind: StorageKind;
  /** GB. */
  capacity: number;
  /** MB/s sequential read, drives the SSD benchmark score. */
  readSpeed: number;
  writeSpeed: number;
}

export interface GpuComponent extends BaseComponent {
  category: 'gpu';
  /** 0-100 raster strength, drives FPS + GPU score. */
  performance: number;
  vram: number;
  /** How many PCIe power connectors it needs (§50). */
  powerConnectors: number;
  /** Recommended PSU wattage for the whole system. */
  recommendedPsu: number;
  /** Slots the card physically occupies. */
  slotWidth: number;
  /** Cooling capability 0-100, feeds GPU temperature. */
  coolingCapacity: number;
  noise: number;
  dimensions: Dimensions;
}

export interface PsuComponent extends BaseComponent {
  category: 'psu';
  wattage: number;
  /** '80+ Bronze' etc. */
  efficiency: string;
  /** 0-1 efficiency multiplier used for heat + a small score nudge. */
  efficiencyFactor: number;
  modular: 'none' | 'semi' | 'full';
  /** How many of each connector the PSU physically provides. */
  connectors: { pcie8: number; eps8: number; sata: number };
  /** Depth in mm. */
  depth: number;
  noise: number;
}

export interface FanComponent extends BaseComponent {
  category: 'fan';
  size: FanSize;
  /** Airflow in CFM at 100%. */
  cfm: number;
  /** dB at 100%. */
  noise: number;
  /** Static pressure rating, matters a little for radiators. */
  staticPressure: number;
}

export type Component =
  | CaseComponent
  | MotherboardComponent
  | CpuComponent
  | CoolerComponent
  | RamComponent
  | StorageComponent
  | GpuComponent
  | PsuComponent
  | FanComponent;

/** Narrowing helpers so call sites stay readable. */
export const isCase = (c: Component): c is CaseComponent => c.category === 'case';
export const isMobo = (c: Component): c is MotherboardComponent => c.category === 'motherboard';
export const isCpu = (c: Component): c is CpuComponent => c.category === 'cpu';
export const isCooler = (c: Component): c is CoolerComponent => c.category === 'cooler';
export const isRam = (c: Component): c is RamComponent => c.category === 'ram';
export const isStorage = (c: Component): c is StorageComponent => c.category === 'storage';
export const isGpu = (c: Component): c is GpuComponent => c.category === 'gpu';
export const isPsu = (c: Component): c is PsuComponent => c.category === 'psu';
export const isFan = (c: Component): c is FanComponent => c.category === 'fan';
