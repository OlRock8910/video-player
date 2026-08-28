import type { Screen, UIManager } from './UIManager';
import { button, el, iconButton, money, row, sectionTitle } from './dom';
import { game } from '../core/GameManager';
import { toast } from '../core/EventBus';
import { GRADE_COLORS } from '../sim/BenchmarkManager';
import { formatDuration } from './BuildReport';
import type { SavedPc } from '../core/SaveManager';

/** The garage of finished machines (§34). */
export class GarageScreen implements Screen {
  readonly id = 'garage' as const;

  constructor(private ui: UIManager) {}

  render(root: HTMLElement): void {
    const pcs = game.garage();
    const body = el('div', { class: 'screen-scroll' });

    if (pcs.length === 0) {
      body.append(
        el('div', { class: 'card', style: { marginTop: '24px', textAlign: 'center' } }, [
          el('div', { class: 'card-title', text: 'No builds yet' }),
          el('div', { class: 'dim', text: 'Finish a PC and it will be displayed here.' }),
          button('Start building', () => {
            game.newBuild('Free Build', 'free');
            this.ui.show('workshop');
          }, { class: 'primary' }),
        ])
      );
    }

    for (const pc of pcs) body.append(this.card(pc));

    root.append(
      el('div', { class: 'topbar' }, [
        iconButton('‹', () => this.ui.back(), 'Back'),
        el('h2', { text: 'Garage' }),
        el('div', { class: 'wallet', text: money(game.money) }),
      ]),
      body
    );
  }

  private card(pc: SavedPc): HTMLElement {
    const r = pc.result;
    return el('div', { class: 'card' }, [
      el('div', { class: 'card-head' }, [
        el('div', { style: { minWidth: '0' } }, [
          el('div', { class: 'card-title', text: pc.name }),
          el('div', {
            class: 'card-brand',
            text: new Date(pc.builtAt).toLocaleDateString('en-US', {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            }),
          }),
        ]),
        el('div', {
          style: {
            fontSize: '2.2rem',
            fontWeight: '800',
            lineHeight: '1',
            color: GRADE_COLORS[r.grade],
          },
          text: r.grade,
        }),
      ]),
      el('div', { class: 'spec-row' }, [
        el('span', { class: 'spec', text: `${r.overall.toLocaleString('en-US')} pts` }),
        el('span', { class: 'spec', text: `${r.totalRamGb}GB RAM` }),
        el('span', { class: 'spec', text: `${r.cpuTemp}°C CPU` }),
        el('span', { class: 'spec', text: `${r.noise} dB` }),
        el('span', { class: 'spec', text: `Cables ${r.cableScore}/100` }),
      ]),
      el('div', { class: 'btn-row' }, [
        button('Details', () => this.showDetails(pc)),
        button('Duplicate', () => {
          game.duplicatePc(pc.id);
          toast('Build duplicated', 'good');
          this.ui.refresh();
        }),
      ]),
      el('div', { class: 'btn-row' }, [
        button('Rename', () => this.rename(pc)),
        button(
          'Delete',
          () => {
            game.deletePc(pc.id);
            toast('Build deleted', 'info');
            this.ui.refresh();
          },
          { class: 'danger' }
        ),
      ]),
    ]);
  }

  private rename(pc: SavedPc): void {
    const input = el('input', { attrs: { type: 'text', value: pc.name } }) as HTMLInputElement;
    const content = el('div', {}, [
      input,
      el('div', { class: 'btn-row', style: { marginTop: '14px' } }, [
        button('Save', () => {
          const name = input.value.trim();
          if (name) {
            game.renamePc(pc.id, name);
            toast('Renamed', 'good');
          }
          close();
          this.ui.refresh();
        }, { class: 'primary' }),
      ]),
    ]);
    const close = this.ui.openSheet('Rename build', content);
    setTimeout(() => input.focus(), 120);
  }

  private showDetails(pc: SavedPc): void {
    const r = pc.result;
    const content = el('div', { class: 'screen-scroll', style: { padding: '0 0 12px' } }, [
      sectionTitle('Scores'),
      el('div', { class: 'list' }, [
        row('Overall', r.overall.toLocaleString('en-US')),
        row('CPU', r.cpuScore.toLocaleString('en-US')),
        row('GPU', r.gpuScore.toLocaleString('en-US')),
        row('Memory', r.ramScore.toLocaleString('en-US')),
        row('Storage', r.ssdScore.toLocaleString('en-US')),
        row('Thermal', `${r.thermalScore}%`),
      ]),
      sectionTitle('Gaming'),
      el('div', { class: 'list' }, r.games.map((g) => row(g.name, `${g.fps} FPS · ${g.preset}`))),
      sectionTitle('Build'),
      el('div', { class: 'list' }, [
        row('Cost', money(pc.cost)),
        row('Build time', formatDuration(pc.build.elapsedMs)),
        row('Mistakes', String(pc.build.mistakes)),
        row('Noise', `${r.noise} dB — ${r.noiseLabel}`),
        row('Cable score', `${r.cableScore}/100 — ${r.cableLabel}`),
      ]),
    ]);
    this.ui.openSheet(pc.name, content);
  }
}
