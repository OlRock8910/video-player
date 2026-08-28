import type { UIManager } from './UIManager';
import { button, el, money, sectionTitle } from './dom';
import { audio } from '../core/AudioManager';
import { haptics } from '../core/HapticsManager';
import { settings } from '../core/Settings';
import type { Build } from '../sim/Build';
import { buildCost } from '../sim/Build';
import { GRADE_COLORS, type BenchmarkResult } from '../sim/BenchmarkManager';
import { cableReport } from '../sim/CableManager';

export interface BuildReportOptions {
  ui: UIManager;
  build: Build;
  result: BenchmarkResult;
  onDone: () => void;
  onShowcase?: () => void;
}

/** Count a number up to its final value; the payoff of a finished build (§24). */
function animateNumber(node: HTMLElement, to: number, suffix = '', durationMs = 1100): void {
  if (settings.get().reduceMotion) {
    node.textContent = to.toLocaleString('en-US') + suffix;
    return;
  }
  const start = performance.now();
  const tick = (now: number): void => {
    const t = Math.min(1, (now - start) / durationMs);
    // Ease out so the last digits settle rather than snapping.
    const eased = 1 - Math.pow(1 - t, 3);
    node.textContent = Math.round(to * eased).toLocaleString('en-US') + suffix;
    if (t < 1) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

function metric(label: string, value: number, suffix = '', delay = 0): HTMLElement {
  const v = el('div', { class: 'v', text: '0' });
  const node = el('div', { class: 'bench-metric' }, [el('div', { class: 'k', text: label }), v]);
  setTimeout(() => animateNumber(v, value, suffix), delay);
  return node;
}

/**
 * The animated benchmark and final build report (§24). Scores count up, then
 * the grade lands.
 */
export function showBuildReport(opts: BuildReportOptions): void {
  const { ui, build, result } = opts;
  const cables = cableReport(build);

  const gradeNode = el('div', {
    class: 'grade',
    text: result.grade,
    style: { color: GRADE_COLORS[result.grade], opacity: '0' },
  });

  const content = el(
    'div',
    {
      class: 'screen-scroll',
      style: {
        paddingTop: 'calc(env(safe-area-inset-top, 0px) + 24px)',
        maxWidth: '620px',
        margin: '0 auto',
        width: '100%',
      },
    },
    [
      el('div', {
        style: {
          textAlign: 'center',
          fontSize: '0.72rem',
          letterSpacing: '0.34em',
          textTransform: 'uppercase',
          color: 'var(--accent)',
        },
        text: 'System Test Complete',
      }),
      el('h1', { style: { textAlign: 'center', fontSize: '1.6rem', marginTop: '6px' }, text: build.name }),

      gradeNode,
      el('div', {
        style: {
          textAlign: 'center',
          fontFamily: 'var(--mono)',
          fontSize: '1.9rem',
          fontWeight: '700',
          marginTop: '-6px',
        },
        id: 'overall-score',
        text: '0',
      }),
      el('div', {
        class: 'dim',
        style: { textAlign: 'center', fontSize: '0.72rem', letterSpacing: '0.24em', marginTop: '2px' },
        text: 'OVERALL SCORE',
      }),

      sectionTitle('Component scores'),
      el('div', { class: 'bench-grid' }, [
        metric('CPU Score', result.cpuScore, '', 200),
        metric('GPU Score', result.gpuScore, '', 340),
        metric('Memory Score', result.ramScore, '', 480),
        metric('SSD Score', result.ssdScore, '', 620),
        metric('Thermal Score', result.thermalScore, '%', 760),
        metric('Power Score', result.powerScore, '%', 880),
      ]),

      sectionTitle('Gaming performance'),
      el(
        'div',
        { class: 'list' },
        result.games.map((g) =>
          el('div', { class: 'row' }, [
            el('span', {}, [
              el('div', { style: { fontWeight: '600' }, text: g.name }),
              el('div', { class: 'faint', style: { fontSize: '0.7rem' }, text: g.preset }),
            ]),
            el('span', {
              class: 'v',
              style: {
                color: g.fps >= 144 ? 'var(--good)' : g.fps >= 60 ? 'var(--text)' : 'var(--warn)',
              },
              text: `${g.fps} FPS`,
            }),
          ])
        )
      ),

      sectionTitle('Thermals & acoustics'),
      el('div', { class: 'list' }, [
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'CPU temperature' }),
          el('span', {
            class: `v ${result.cpuTemp > 85 ? 'bad' : result.cpuTemp > 75 ? 'warn' : 'good'}`,
            text: `${result.cpuTemp} °C`,
          }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'GPU temperature' }),
          el('span', {
            class: `v ${result.gpuTemp > 84 ? 'bad' : result.gpuTemp > 74 ? 'warn' : 'good'}`,
            text: `${result.gpuTemp} °C`,
          }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Case temperature' }),
          el('span', { class: 'v', text: `${result.caseTemp} °C` }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Noise' }),
          el('span', {
            class: `v ${result.noise < 30 ? 'good' : result.noise < 42 ? '' : 'warn'}`,
            text: `${result.noise} dB — ${result.noiseLabel}`,
          }),
        ]),
      ]),

      sectionTitle('Cable management'),
      el('div', { class: 'list' }, [
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Score' }),
          el('span', {
            class: `v ${cables.score >= 85 ? 'good' : cables.score >= 55 ? '' : 'warn'}`,
            text: `${cables.score}/100 — ${cables.label}`,
          }),
        ]),
        ...cables.notes.slice(0, 3).map((n) =>
          el('div', { class: 'row' }, [el('span', { class: 'k', text: n })])
        ),
      ]),

      sectionTitle('Summary'),
      el('div', { class: 'list' }, [
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Memory installed' }),
          el('span', { class: 'v', text: `${result.totalRamGb} GB` }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Storage installed' }),
          el('span', { class: 'v', text: `${result.totalStorageGb} GB` }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Build cost' }),
          el('span', { class: 'v gold', text: money(buildCost(build)) }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Build time' }),
          el('span', {
            class: 'v',
            text: formatDuration(build.elapsedMs),
          }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Mistakes' }),
          el('span', { class: `v ${build.mistakes === 0 ? 'good' : ''}`, text: String(build.mistakes) }),
        ]),
      ]),

      el('div', { class: 'btn-row', style: { margin: '20px 0 8px' } }, [
        opts.onShowcase
          ? button('Showcase', () => {
              close();
              opts.onShowcase?.();
            })
          : null,
        button(
          'Finish',
          () => {
            close();
            opts.onDone();
          },
          { class: 'primary' }
        ),
      ].filter(Boolean) as HTMLElement[]),
    ]
  );

  const close = ui.openFull(content);

  // Overall score counts up, then the grade pops in on top of it.
  const overall = content.querySelector('#overall-score') as HTMLElement;
  setTimeout(() => animateNumber(overall, result.overall, '', 1500), 240);
  setTimeout(
    () => {
      gradeNode.style.opacity = '1';
      audio.play('success');
      haptics.fire('success');
    },
    settings.get().reduceMotion ? 300 : 1800
  );
}

export function formatDuration(ms: number): string {
  if (ms <= 0) return '—';
  const total = Math.round(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}m ${String(s).padStart(2, '0')}s`;
}
