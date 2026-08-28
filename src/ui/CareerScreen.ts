import type { Screen, UIManager } from './UIManager';
import { button, el, iconButton, money, row, sectionTitle } from './dom';
import { game } from '../core/GameManager';
import { save } from '../core/SaveManager';
import { ACHIEVEMENTS, reputationProgress } from '../data/career';

/** Career hub: money, reputation, the job board and achievements (§25-§28). */
export class CareerScreen implements Screen {
  readonly id = 'career' as const;

  constructor(private ui: UIManager) {}

  render(root: HTMLElement): void {
    const data = save.get();
    const progress = reputationProgress(data.career.reputation);

    const body = el('div', { class: 'screen-scroll' });

    // Status header.
    body.append(
      el('div', { class: 'card' }, [
        el('div', { class: 'card-head' }, [
          el('div', {}, [
            el('div', { class: 'card-brand', text: `Day ${data.career.day}` }),
            el('div', { class: 'card-title', style: { fontSize: '1.3rem' }, text: progress.current.name }),
          ]),
          el('div', { class: 'card-price', text: money(data.career.money) }),
        ]),
        el('div', { class: 'progress-track' }, [
          el('div', { class: 'progress-fill', style: { width: `${Math.round(progress.pct * 100)}%` } }),
        ]),
        el('div', {
          class: 'faint',
          style: { fontSize: '0.72rem' },
          text: progress.next
            ? `${data.career.reputation} / ${progress.next.threshold} reputation to ${progress.next.name}`
            : 'Maximum reputation reached',
        }),
        el('div', {
          class: 'dim',
          style: { fontSize: '0.76rem' },
          text: `Payout multiplier ×${progress.current.payMultiplier.toFixed(2)}`,
        }),
      ])
    );

    // Active job.
    const active = game.activeJob;
    if (active) {
      const customer = game.customerFor(active);
      body.append(
        sectionTitle('Active job'),
        el('div', { class: 'card selected' }, [
          el('div', { class: 'card-head' }, [
            el('div', {}, [
              el('div', { class: 'card-title', text: active.title }),
              el('div', { class: 'card-brand', style: { color: customer.accent }, text: customer.name }),
            ]),
            el('div', { class: 'card-price', text: money(active.budget) }),
          ]),
          el('div', { class: 'dim', style: { fontSize: '0.82rem', fontStyle: 'italic' }, text: `"${active.brief}"` }),
          el('div', { class: 'spec-row' }, active.bullets.map((b) => el('span', { class: 'spec', text: b }))),
          el('div', { class: 'btn-row' }, [
            button('Continue build', () => this.ui.show('workshop'), { class: 'primary' }),
            button(
              'Abandon',
              () => {
                save.update((d) => {
                  d.career.activeJobId = null;
                });
                game.newBuild('Free Build', 'free');
                this.ui.refresh();
              },
              { class: 'danger' }
            ),
          ]),
        ])
      );
    }

    // Job board.
    body.append(sectionTitle(active ? 'Other offers' : 'Job board'));
    const offers = data.career.availableJobs
      .map((id) => game.jobById(id))
      .filter((j): j is NonNullable<typeof j> => !!j && j.id !== active?.id);

    if (offers.length === 0) {
      body.append(
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'No new jobs right now. Finish a build to bring more in.' }),
        ])
      );
    }

    for (const job of offers) {
      const customer = game.customerFor(job);
      body.append(
        el('div', { class: 'card' }, [
          el('div', { class: 'card-head' }, [
            el('div', { style: { minWidth: '0' } }, [
              el('div', { class: 'card-title', text: job.title }),
              el('div', { class: 'card-brand', style: { color: customer.accent }, text: `${customer.name} · ${customer.archetype}` }),
            ]),
            el('div', { style: { textAlign: 'right' } }, [
              el('div', { class: 'card-price', text: money(job.budget) }),
              el('div', { class: 'faint', style: { fontSize: '0.7rem' }, text: 'budget' }),
            ]),
          ]),
          el('div', { class: 'dim', style: { fontSize: '0.82rem', fontStyle: 'italic' }, text: `"${job.brief}"` }),
          el('div', { class: 'faint', style: { fontSize: '0.76rem' }, text: `"${customer.quote}"` }),
          el('div', { class: 'spec-row' }, job.bullets.map((b) => el('span', { class: 'spec', text: b }))),
          el('div', { class: 'spec-row' }, [
            el('span', { class: 'spec', text: `Pays ${money(job.reward)}` }),
            el('span', { class: 'spec', text: `+${job.reputationReward} rep` }),
          ]),
          button(
            'Accept job',
            () => {
              game.acceptJob(job.id);
              this.ui.show('workshop');
            },
            { class: 'primary' }
          ),
        ])
      );
    }

    // Achievements.
    body.append(sectionTitle('Achievements'));
    const list = el('div', { class: 'list' });
    for (const a of ACHIEVEMENTS) {
      const unlocked = game.hasAchievement(a.id);
      list.append(
        el('div', { class: 'row', style: unlocked ? {} : { opacity: '0.45' } }, [
          el('span', { class: 'k' }, [
            el('span', { style: { fontSize: '1.1rem', marginRight: '8px' }, text: a.icon }),
            el('span', { style: { fontWeight: unlocked ? '700' : '400', color: unlocked ? 'var(--text)' : undefined }, text: a.title }),
            el('div', { class: 'faint', style: { fontSize: '0.72rem', marginTop: '2px' }, text: a.description }),
          ]),
          el('span', { class: `v ${unlocked ? 'good' : 'faint'}`, text: unlocked ? '✓' : '—' }),
        ])
      );
    }
    body.append(list);

    body.append(
      sectionTitle('Statistics'),
      el('div', { class: 'list' }, [
        row('Builds completed', String(data.stats.buildsCompleted)),
        row('Total earned', money(data.stats.totalEarned)),
        row('Best score', data.stats.bestScore.toLocaleString('en-US')),
        row(
          'Fastest build',
          data.stats.fastestBuildMs
            ? `${Math.floor(data.stats.fastestBuildMs / 60000)}m ${String(
                Math.floor((data.stats.fastestBuildMs % 60000) / 1000)
              ).padStart(2, '0')}s`
            : '—'
        ),
      ])
    );

    root.append(
      el('div', { class: 'topbar' }, [
        iconButton('‹', () => this.ui.back(), 'Back'),
        el('h2', { text: 'Career' }),
        el('div', { class: 'wallet', text: money(game.money) }),
      ]),
      body
    );
  }
}
