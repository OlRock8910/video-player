import type { Screen, UIManager } from './UIManager';
import { button, chip, el, iconButton, money } from './dom';
import { game } from '../core/GameManager';
import { BRANDS } from '../data/brands';
import { CATEGORY_LABELS } from '../data/catalog';
import type { Category, Component } from '../data/types';
import { REPUTATION_LEVELS } from '../data/career';
import { canInstall } from '../sim/CompatibilityManager';
import {
  isCase,
  isCooler,
  isCpu,
  isFan,
  isGpu,
  isMobo,
  isPsu,
  isRam,
  isStorage,
} from '../data/types';

const CATEGORY_ORDER: Category[] = [
  'case',
  'motherboard',
  'cpu',
  'cooler',
  'ram',
  'storage',
  'gpu',
  'psu',
  'fan',
];

/** The parts shop (§29). Every component with its full spec sheet. */
export class ShopScreen implements Screen {
  readonly id = 'shop' as const;
  private category: Category = 'case';

  constructor(private ui: UIManager) {}

  render(root: HTMLElement): void {
    const wallet = el('div', { class: 'wallet', text: money(game.money) });

    const tabs = el(
      'div',
      { class: 'tray-scroll', style: { padding: '4px 14px 10px' } },
      CATEGORY_ORDER.map((c) =>
        chip(CATEGORY_LABELS[c], this.category === c, () => {
          this.category = c;
          this.ui.refresh();
        })
      )
    );

    const list = el('div', { class: 'list' });
    const parts = game
      .shopCatalog()
      .filter((c) => c.category === this.category)
      .sort((a, b) => a.price - b.price);

    // Locked parts still show, so the player can see what to work toward.
    const locked = game
      .availableLocked()
      .filter((c) => c.category === this.category)
      .sort((a, b) => a.price - b.price);

    for (const part of parts) list.append(this.card(part, false));
    for (const part of locked) list.append(this.card(part, true));

    root.append(
      el('div', { class: 'topbar' }, [
        iconButton('‹', () => this.ui.back(), 'Back'),
        el('h2', { text: 'Parts Shop' }),
        wallet,
      ]),
      tabs,
      el('div', { class: 'screen-scroll' }, [list])
    );
  }

  private card(part: Component, locked: boolean): HTMLElement {
    const ownedCount = game.owned(part.id);
    const compat = this.compatibilityBadge(part);

    const node = el('div', { class: `card ${locked ? 'locked' : ''}` }, [
      el('div', { class: 'card-head' }, [
        el('div', { style: { minWidth: '0' } }, [
          el('div', { class: 'card-title', text: part.name }),
          el('div', { class: 'card-brand', text: BRANDS[part.brand]?.name ?? part.brand }),
        ]),
        el('div', { style: { textAlign: 'right' } }, [
          el('div', { class: 'card-price', text: money(part.price) }),
          el('div', { class: `tier tier-${part.tier}`, text: part.tier }),
        ]),
      ]),
      el('div', { class: 'dim', style: { fontSize: '0.8rem', lineHeight: '1.35' }, text: part.blurb }),
      el('div', { class: 'spec-row' }, this.specs(part).map((s) => el('span', { class: 'spec', text: s }))),
      compat,
      locked
        ? el('div', {
            class: 'warn',
            style: { fontSize: '0.76rem' },
            text: `Unlocks at ${REPUTATION_LEVELS[part.reputationRequired ?? 0]?.name ?? 'a higher rank'}`,
          })
        : el('div', { class: 'btn-row' }, [
            button(ownedCount > 0 ? `Buy (own ${ownedCount})` : 'Buy', () => {
              if (game.buy(part)) this.ui.refresh();
            }),
            ownedCount > 0
              ? button('Sell', () => {
                  game.sell(part.id);
                  this.ui.refresh();
                }, { class: 'ghost' })
              : null,
          ].filter(Boolean) as HTMLElement[]),
    ]);
    return node;
  }

  /** Does this part fit what is already on the bench? */
  private compatibilityBadge(part: Component): HTMLElement {
    const build = game.current;
    if (build.parts.length === 0) return el('span');
    // Probe the natural slot for this category.
    const slot = ({
      case: 'case',
      motherboard: 'mobo-tray',
      cpu: 'cpu-socket',
      cooler: 'cooler-mount',
      ram: 'ram-0',
      storage: 'm2-0',
      gpu: 'pcie-0',
      psu: 'psu-bay',
      fan: 'fan-front-0',
    } as const)[part.category];

    const result = canInstall(build, part, slot);
    const blocker = result.issues.find((i) => i.severity === 'blocker');
    // "Slot occupied" is not an incompatibility, just a full slot.
    if (blocker && blocker.title !== 'Slot occupied') {
      return el('div', { class: 'bad', style: { fontSize: '0.74rem' }, text: `❌ ${blocker.title}` });
    }
    const warning = result.issues.find((i) => i.severity === 'warning');
    if (warning) {
      return el('div', { class: 'warn', style: { fontSize: '0.74rem' }, text: `⚠ ${warning.title}` });
    }
    return el('div', { class: 'good', style: { fontSize: '0.74rem' }, text: '✓ Fits your current build' });
  }

  /** Human-readable spec chips per category (§29). */
  private specs(c: Component): string[] {
    if (isCase(c)) {
      return [
        c.supportedFormFactors.join('/'),
        `GPU ≤ ${c.gpuClearance}mm`,
        `Cooler ≤ ${c.coolerClearance}mm`,
        `${c.radiatorSupport.join('/')}mm rad`,
        c.temperedGlass ? 'Tempered glass' : 'Solid panel',
      ];
    }
    if (isMobo(c)) {
      return [c.socket, c.formFactor, c.ramType, `${c.ramSlots} DIMM`, `${c.m2Slots}× M.2`, `${c.vrmWattage}W VRM`];
    }
    if (isCpu(c)) {
      return [
        c.socket,
        `${c.cores}C/${c.threads}T`,
        `${c.boostClock} GHz`,
        `${c.tdp}W`,
        c.integratedGraphics ? 'iGPU' : 'No iGPU',
        c.includesCooler ? 'Cooler included' : 'Cooler not included',
      ];
    }
    if (isCooler(c)) {
      return [
        c.coolerType.toUpperCase(),
        `${c.tdpRating}W rated`,
        c.radiatorSize ? `${c.radiatorSize}mm rad` : `${c.height}mm tall`,
        `${c.noise} dB`,
      ];
    }
    if (isRam(c)) {
      return [c.ramType, `${c.capacity * c.sticks}GB (${c.sticks}×${c.capacity})`, `${c.speed} MT/s`, `CL${c.latency}`, `${c.height}mm`];
    }
    if (isStorage(c)) {
      return [c.kind.toUpperCase(), `${c.capacity}GB`, `${c.readSpeed} MB/s read`];
    }
    if (isGpu(c)) {
      return [
        `${c.vram}GB`,
        `${c.dimensions.length}mm`,
        `${c.slotWidth}-slot`,
        `${c.power}W`,
        c.powerConnectors ? `${c.powerConnectors}× 8-pin` : 'Slot powered',
        `${c.recommendedPsu}W PSU`,
      ];
    }
    if (isPsu(c)) {
      return [
        `${c.wattage}W`,
        c.efficiency,
        `${c.modular} modular`,
        `${c.connectors.pcie8}× PCIe`,
        `${c.depth}mm`,
      ];
    }
    if (isFan(c)) {
      return [`${c.size}mm`, `${c.cfm} CFM`, `${c.noise} dB`, c.rgb ? 'ARGB' : 'No lighting'];
    }
    return [];
  }
}
