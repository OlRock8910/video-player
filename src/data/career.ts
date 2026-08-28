/** Career, customers, reputation, challenges and achievements (§25-§28, §35, §47). */

export interface ReputationLevel {
  index: number;
  name: string;
  /** Reputation points needed to reach this level. */
  threshold: number;
  /** Payout multiplier at this level. */
  payMultiplier: number;
}

export const REPUTATION_LEVELS: ReputationLevel[] = [
  { index: 0, name: 'Rookie', threshold: 0, payMultiplier: 1 },
  { index: 1, name: 'Apprentice', threshold: 50, payMultiplier: 1.15 },
  { index: 2, name: 'Builder', threshold: 150, payMultiplier: 1.35 },
  { index: 3, name: 'Expert', threshold: 350, payMultiplier: 1.6 },
  { index: 4, name: 'Master Builder', threshold: 700, payMultiplier: 2 },
  { index: 5, name: 'Legend', threshold: 1200, payMultiplier: 2.6 },
];

export function reputationLevel(points: number): ReputationLevel {
  let level = REPUTATION_LEVELS[0];
  for (const l of REPUTATION_LEVELS) if (points >= l.threshold) level = l;
  return level;
}

export function reputationProgress(points: number): { current: ReputationLevel; next?: ReputationLevel; pct: number } {
  const current = reputationLevel(points);
  const next = REPUTATION_LEVELS[current.index + 1];
  if (!next) return { current, pct: 1 };
  const span = next.threshold - current.threshold;
  return { current, next, pct: Math.max(0, Math.min(1, (points - current.threshold) / span)) };
}

/* ------------------------------------------------------------------ */
/* Customers (§27)                                                     */
/* ------------------------------------------------------------------ */

export type CustomerArchetype =
  | 'budget'
  | 'rgb'
  | 'gamer'
  | 'beginner'
  | 'creator'
  | 'professional'
  | 'enthusiast';

export interface Customer {
  id: string;
  name: string;
  archetype: CustomerArchetype;
  /** Shown in the job briefing. */
  quote: string;
  /** Which scored dimensions this customer actually cares about, 0-1 weights. */
  priorities: {
    performance: number;
    quiet: number;
    looks: number;
    thermals: number;
    budget: number;
  };
  /** Portrait accent colour. */
  accent: string;
}

export const CUSTOMERS: Customer[] = [
  {
    id: 'mara',
    name: 'Mara Quill',
    archetype: 'budget',
    quote: "It just needs to work. I'm not paying for lights I'll never look at.",
    priorities: { performance: 0.35, quiet: 0.2, looks: 0.05, thermals: 0.2, budget: 1 },
    accent: '#8b9bb4',
  },
  {
    id: 'kade',
    name: 'Kade Rios',
    archetype: 'rgb',
    quote: 'I want it to look INSANE. If it does not glow, I do not want it.',
    priorities: { performance: 0.4, quiet: 0.05, looks: 1, thermals: 0.3, budget: 0.3 },
    accent: '#ff4fd8',
  },
  {
    id: 'vex',
    name: 'Vex Tanaka',
    archetype: 'gamer',
    quote: "I don't care how it looks. I just want maximum FPS.",
    priorities: { performance: 1, quiet: 0.1, looks: 0.05, thermals: 0.5, budget: 0.2 },
    accent: '#00e5a0',
  },
  {
    id: 'olive',
    name: 'Olive Brand',
    archetype: 'beginner',
    quote: "My son says I need a 'good one'. I trust you completely.",
    priorities: { performance: 0.4, quiet: 0.5, looks: 0.3, thermals: 0.5, budget: 0.6 },
    accent: '#4fd1ff',
  },
  {
    id: 'juno',
    name: 'Juno Park',
    archetype: 'creator',
    quote: 'I edit 4K all day and stream at night. It cannot stutter, and it cannot roar.',
    priorities: { performance: 0.8, quiet: 0.7, looks: 0.4, thermals: 0.6, budget: 0.3 },
    accent: '#9d7bff',
  },
  {
    id: 'ferris',
    name: 'Dr. Ferris Nomura',
    archetype: 'professional',
    quote: 'Reliability first. I would rather have headroom than a benchmark record.',
    priorities: { performance: 0.7, quiet: 0.5, looks: 0.1, thermals: 0.9, budget: 0.4 },
    accent: '#ffd23f',
  },
  {
    id: 'sable',
    name: 'Sable Hex',
    archetype: 'enthusiast',
    quote: 'Show me something I have not seen before. Spare no expense.',
    priorities: { performance: 0.9, quiet: 0.4, looks: 0.8, thermals: 0.8, budget: 0.05 },
    accent: '#ff7a2f',
  },
];

/* ------------------------------------------------------------------ */
/* Jobs (§26)                                                          */
/* ------------------------------------------------------------------ */

export interface JobRequirement {
  minRamGb?: number;
  minStorageGb?: number;
  minOverallScore?: number;
  minFpsIn?: { gameId: string; fps: number };
  maxNoiseDb?: number;
  maxCpuTemp?: number;
  requireRgbZones?: number;
  maxFormFactor?: 'ITX' | 'mATX' | 'ATX';
  minCableScore?: number;
}

export interface JobTemplate {
  id: string;
  title: string;
  customerId: string;
  brief: string;
  budget: number;
  /** Base payout before the reputation multiplier. */
  reward: number;
  reputationReward: number;
  requirements: JobRequirement;
  /** Minimum reputation level to be offered this job. */
  minReputationLevel: number;
  bullets: string[];
}

export const JOBS: JobTemplate[] = [
  {
    id: 'job-school',
    title: 'Office PC',
    customerId: 'mara',
    brief: 'I need a cheap PC for school.',
    budget: 450,
    reward: 75,
    reputationReward: 10,
    minReputationLevel: 0,
    requirements: { minRamGb: 16, minStorageGb: 500, maxNoiseDb: 38 },
    bullets: ['16GB RAM', '500GB storage', 'Quiet', 'Low power'],
  },
  {
    id: 'job-student',
    title: 'Student PC',
    customerId: 'olive',
    brief: 'Affordable and reliable — it has to last four years.',
    budget: 700,
    reward: 120,
    reputationReward: 14,
    minReputationLevel: 0,
    requirements: { minRamGb: 16, minStorageGb: 1000, maxCpuTemp: 80 },
    bullets: ['16GB RAM', '1TB storage', 'Runs cool', 'Reliable parts'],
  },
  {
    id: 'job-gaming',
    title: 'Gaming PC',
    customerId: 'vex',
    brief: 'High FPS. That is the whole brief.',
    budget: 1400,
    reward: 260,
    reputationReward: 22,
    minReputationLevel: 1,
    requirements: { minRamGb: 16, minStorageGb: 1000, minFpsIn: { gameId: 'cyber-arena', fps: 144 } },
    bullets: ['144+ FPS in Cyber Arena', '16GB RAM', '1TB storage'],
  },
  {
    id: 'job-rgb',
    title: 'RGB Gaming PC',
    customerId: 'kade',
    brief: 'It has to look amazing. Every part that can glow, should.',
    budget: 1800,
    reward: 340,
    reputationReward: 26,
    minReputationLevel: 1,
    requirements: { requireRgbZones: 4, minRamGb: 32, minFpsIn: { gameId: 'cyber-arena', fps: 120 } },
    bullets: ['At least 4 RGB zones', 'Tempered glass case', '32GB RAM', '120+ FPS'],
  },
  {
    id: 'job-silent',
    title: 'Silent PC',
    customerId: 'ferris',
    brief: 'It sits next to my desk. I must not hear it.',
    budget: 1500,
    reward: 300,
    reputationReward: 28,
    minReputationLevel: 2,
    requirements: { maxNoiseDb: 30, maxCpuTemp: 75, minRamGb: 32 },
    bullets: ['Under 30 dB', 'CPU under 75°C', '32GB RAM'],
  },
  {
    id: 'job-streaming',
    title: 'Streaming PC',
    customerId: 'juno',
    brief: 'Encoding and gaming at the same time, without dropping frames.',
    budget: 2200,
    reward: 430,
    reputationReward: 30,
    minReputationLevel: 2,
    requirements: { minRamGb: 32, minStorageGb: 2000, minOverallScore: 14000, maxNoiseDb: 38 },
    bullets: ['Strong multi-core CPU', '32GB RAM', '2TB storage', 'A-grade or better'],
  },
  {
    id: 'job-editing',
    title: 'Editing PC',
    customerId: 'juno',
    brief: 'Timeline scrubbing on 4K footage. RAM and storage are king.',
    budget: 2600,
    reward: 500,
    reputationReward: 32,
    minReputationLevel: 2,
    requirements: { minRamGb: 64, minStorageGb: 2000, minOverallScore: 16000 },
    bullets: ['64GB RAM', '2TB+ fast storage', 'High multi-core score'],
  },
  {
    id: 'job-sff',
    title: 'Small Form Factor PC',
    customerId: 'olive',
    brief: 'It has to fit in the cabinet under the TV.',
    budget: 1600,
    reward: 380,
    reputationReward: 34,
    minReputationLevel: 3,
    requirements: { maxFormFactor: 'ITX', minRamGb: 16, maxCpuTemp: 85, minOverallScore: 11000 },
    bullets: ['ITX case and board', 'Under 85°C', 'Still genuinely fast'],
  },
  {
    id: 'job-workstation',
    title: 'Professional Workstation',
    customerId: 'ferris',
    brief: 'Simulation work. Performance and reliability, in that order.',
    budget: 3800,
    reward: 760,
    reputationReward: 42,
    minReputationLevel: 3,
    requirements: { minRamGb: 64, minStorageGb: 2000, minOverallScore: 22000, maxCpuTemp: 80 },
    bullets: ['64GB RAM', 'S-grade performance', 'Thermal headroom', 'Tidy cabling'],
  },
  {
    id: 'job-extreme',
    title: 'Extreme Gaming PC',
    customerId: 'sable',
    brief: 'Build me the best machine you can. Money is not the constraint.',
    budget: 6000,
    reward: 1250,
    reputationReward: 55,
    minReputationLevel: 4,
    requirements: {
      minOverallScore: 26000,
      minRamGb: 64,
      minCableScore: 80,
      minFpsIn: { gameId: 'galactic-warfare', fps: 200 },
    },
    bullets: ['S+ grade', '200+ FPS in Galactic Warfare', '64GB RAM', 'Show-quality cabling'],
  },
];

/* ------------------------------------------------------------------ */
/* Challenges (§35)                                                    */
/* ------------------------------------------------------------------ */

export interface Challenge {
  id: string;
  title: string;
  description: string;
  /** Constraint the run is scored against. */
  rule: string;
  reward: number;
  reputationReward: number;
}

export const CHALLENGES: Challenge[] = [
  {
    id: 'ch-500',
    title: '$500 CHALLENGE',
    description: 'Build the fastest PC you can for under five hundred dollars.',
    rule: 'Total part cost must stay under $500.',
    reward: 200,
    reputationReward: 20,
  },
  {
    id: 'ch-tiny',
    title: 'TINY PC',
    description: 'Everything has to fit in the smallest case on the shelf.',
    rule: 'Use an ITX case, and still score 10,000+.',
    reward: 280,
    reputationReward: 24,
  },
  {
    id: 'ch-rgb',
    title: 'RGB EVERYTHING',
    description: 'If a part can light up, it must light up.',
    rule: 'Every installed part that supports RGB must have RGB.',
    reward: 320,
    reputationReward: 26,
  },
  {
    id: 'ch-silent',
    title: 'SILENT BUILD',
    description: 'Keep it cool and keep it quiet.',
    rule: 'Under 28 dB with the CPU below 70°C.',
    reward: 360,
    reputationReward: 30,
  },
  {
    id: 'ch-nomistakes',
    title: 'NO MISTAKES',
    description: 'A clean run, start to finish.',
    rule: 'Complete a build without a single rejected installation.',
    reward: 240,
    reputationReward: 22,
  },
  {
    id: 'ch-speed',
    title: 'SPEED BUILD',
    description: 'The clock is running.',
    rule: 'Finish a complete, booting build in under five minutes.',
    reward: 300,
    reputationReward: 25,
  },
];

/* ------------------------------------------------------------------ */
/* Achievements (§47)                                                  */
/* ------------------------------------------------------------------ */

export interface Achievement {
  id: string;
  title: string;
  description: string;
  icon: string;
}

export const ACHIEVEMENTS: Achievement[] = [
  { id: 'first-boot', title: 'FIRST BOOT', description: 'Boot your first PC.', icon: '⚡' },
  { id: 'cable-master', title: 'CABLE MASTER', description: 'Score 100/100 on cable management.', icon: '🧵' },
  { id: 'rgb-overload', title: 'RGB OVERLOAD', description: 'Install RGB on every possible component.', icon: '🌈' },
  { id: 'speed-builder', title: 'SPEED BUILDER', description: 'Complete a build in under five minutes.', icon: '⏱' },
  { id: 'budget-king', title: 'BUDGET KING', description: 'Score 9,000+ with parts under $500.', icon: '💰' },
  { id: 'no-mistakes', title: 'NO MISTAKES', description: 'Complete a build with zero rejected installs.', icon: '✓' },
  { id: 'overkill', title: 'OVERKILL', description: 'Build a machine that grades S+.', icon: '💀' },
  { id: 'silent-night', title: 'SILENT NIGHT', description: 'Finish a build measuring under 28 dB.', icon: '🌙' },
  { id: 'master-builder', title: 'MASTER BUILDER', description: 'Reach the highest reputation level.', icon: '👑' },
  { id: 'full-house', title: 'FULL HOUSE', description: 'Fill every fan mount in a case.', icon: '🌀' },
];
