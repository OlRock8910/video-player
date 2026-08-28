import type { Category, Component } from '../types';
import { CASES } from './cases';
import { CPUS } from './cpus';
import { GPUS } from './gpus';
import { MOTHERBOARDS } from './motherboards';
import { COOLERS, FANS, PSUS, RAM, STORAGE } from './misc';

export { CASES, CPUS, GPUS, MOTHERBOARDS, COOLERS, FANS, PSUS, RAM, STORAGE };

export const ALL_COMPONENTS: Component[] = [
  ...CASES,
  ...MOTHERBOARDS,
  ...CPUS,
  ...COOLERS,
  ...RAM,
  ...STORAGE,
  ...GPUS,
  ...PSUS,
  ...FANS,
];

const BY_ID = new Map<string, Component>(ALL_COMPONENTS.map((c) => [c.id, c]));

export function getComponent(id: string): Component | undefined {
  return BY_ID.get(id);
}

/** Throws on unknown ids — used where a missing part is a programming error. */
export function requireComponent(id: string): Component {
  const c = BY_ID.get(id);
  if (!c) throw new Error(`Unknown component id: ${id}`);
  return c;
}

export function byCategory(category: Category): Component[] {
  return ALL_COMPONENTS.filter((c) => c.category === category);
}

export const CATEGORY_LABELS: Record<Category, string> = {
  case: 'Cases',
  motherboard: 'Motherboards',
  cpu: 'Processors',
  cooler: 'Coolers',
  ram: 'Memory',
  storage: 'Storage',
  gpu: 'Graphics Cards',
  psu: 'Power Supplies',
  fan: 'Case Fans',
};

/** Duplicate ids would silently break saves, so assert at module load. */
if (BY_ID.size !== ALL_COMPONENTS.length) {
  const seen = new Set<string>();
  const dupes = ALL_COMPONENTS.filter((c) => (seen.has(c.id) ? true : (seen.add(c.id), false)));
  throw new Error(`Duplicate component ids: ${dupes.map((d) => d.id).join(', ')}`);
}
