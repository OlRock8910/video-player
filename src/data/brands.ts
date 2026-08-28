/**
 * Fictional hardware brands (§30). Every brand gets a signature accent colour so
 * parts read as visually distinct on the shelf and inside the case.
 */
export interface Brand {
  id: string;
  name: string;
  accent: number;
  secondary: number;
  tagline: string;
}

export const BRANDS: Record<string, Brand> = {
  coreforge: { id: 'coreforge', name: 'CoreForge', accent: 0xff7a2f, secondary: 0x2b1608, tagline: 'Forged for throughput.' },
  quantumcore: { id: 'quantumcore', name: 'QuantumCore', accent: 0x4fd1ff, secondary: 0x08222b, tagline: 'Compute, uncollapsed.' },
  voltcore: { id: 'voltcore', name: 'VoltCore', accent: 0xffd23f, secondary: 0x2b2308, tagline: 'Raw current.' },
  nexachip: { id: 'nexachip', name: 'NexaChip', accent: 0x9d7bff, secondary: 0x1a1030, tagline: 'The next node.' },

  pixelstorm: { id: 'pixelstorm', name: 'PixelStorm', accent: 0x00e5a0, secondary: 0x052b20, tagline: 'Frames like weather.' },
  hyperframe: { id: 'hyperframe', name: 'HyperFrame', accent: 0xff3d6e, secondary: 0x2b0812, tagline: 'Every frame, faster.' },
  vectorx: { id: 'vectorx', name: 'VectorX', accent: 0x3d8bff, secondary: 0x08172b, tagline: 'Direction and magnitude.' },
  novarender: { id: 'novarender', name: 'NovaRender', accent: 0xffffff, secondary: 0x1a1a22, tagline: 'Light it up.' },

  forgeboard: { id: 'forgeboard', name: 'ForgeBoard', accent: 0xff9a3f, secondary: 0x241505, tagline: 'The foundation.' },
  nexatech: { id: 'nexatech', name: 'NexaTech', accent: 0x8b6bff, secondary: 0x150e2b, tagline: 'Engineered layers.' },
  voltboard: { id: 'voltboard', name: 'VoltBoard', accent: 0xf2e14c, secondary: 0x23200a, tagline: 'Clean power delivery.' },

  pulseram: { id: 'pulseram', name: 'PulseRAM', accent: 0xff4fd8, secondary: 0x2b0824, tagline: 'Feel the frequency.' },
  glowmemory: { id: 'glowmemory', name: 'GlowMemory', accent: 0x5affc8, secondary: 0x082b22, tagline: 'Memory, illuminated.' },
  hyperram: { id: 'hyperram', name: 'HyperRAM', accent: 0xff6a2f, secondary: 0x2b1408, tagline: 'Latency is a choice.' },

  flashforge: { id: 'flashforge', name: 'FlashForge', accent: 0x2fd3ff, secondary: 0x08222b, tagline: 'Instant everything.' },
  datacore: { id: 'datacore', name: 'DataCore', accent: 0x7f8fa6, secondary: 0x14181f, tagline: 'Keep what matters.' },
  speedvault: { id: 'speedvault', name: 'SpeedVault', accent: 0xffc23f, secondary: 0x2b2008, tagline: 'Locked in, launched fast.' },

  ampereus: { id: 'ampereus', name: 'Ampereus', accent: 0x4affa0, secondary: 0x082b1c, tagline: 'Silent watts.' },
  ironvolt: { id: 'ironvolt', name: 'IronVolt', accent: 0xc0c6d0, secondary: 0x181c22, tagline: 'Built like a substation.' },

  cryoflux: { id: 'cryoflux', name: 'CryoFlux', accent: 0x6fe8ff, secondary: 0x082630, tagline: 'Heat has nowhere to go.' },
  zephyr: { id: 'zephyr', name: 'Zephyr Dynamics', accent: 0xb8e0ff, secondary: 0x111c26, tagline: 'Air, moved properly.' },

  obsidian: { id: 'obsidian', name: 'Obsidian Works', accent: 0x6f7480, secondary: 0x0e1014, tagline: 'Chassis as furniture.' },
  lumenshell: { id: 'lumenshell', name: 'LumenShell', accent: 0x00d0ff, secondary: 0x08202b, tagline: 'Show the build.' },
};

export const brandAccent = (id: string): number => BRANDS[id]?.accent ?? 0x8892a0;
export const brandName = (id: string): string => BRANDS[id]?.name ?? 'Generic';
