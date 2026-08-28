import type { Screen, UIManager } from './UIManager';
import { button, el, iconButton, money } from './dom';
import { game } from '../core/GameManager';
import { save } from '../core/SaveManager';
import { CHALLENGES } from '../data/career';

/** Special building challenges (§35). */
export class ChallengesScreen implements Screen {
  readonly id = 'challenges' as const;

  constructor(private ui: UIManager) {}

  render(root: HTMLElement): void {
    const done = save.get().career.completedChallenges;
    const body = el('div', { class: 'screen-scroll' });

    for (const ch of CHALLENGES) {
      const complete = done.includes(ch.id);
      body.append(
        el('div', { class: `card ${complete ? 'selected' : ''}` }, [
          el('div', { class: 'card-head' }, [
            el('div', { style: { minWidth: '0' } }, [
              el('div', { class: 'card-title', text: ch.title }),
              el('div', { class: 'card-brand', text: complete ? 'Completed' : 'Not attempted' }),
            ]),
            complete
              ? el('div', { style: { fontSize: '1.6rem', color: 'var(--good)' }, text: '✓' })
              : el('div', { class: 'card-price', text: money(ch.reward) }),
          ]),
          el('div', { class: 'dim', style: { fontSize: '0.84rem' }, text: ch.description }),
          el('div', { class: 'spec-row' }, [
            el('span', { class: 'spec', text: ch.rule }),
            el('span', { class: 'spec', text: `+${ch.reputationReward} rep` }),
          ]),
          button(
            complete ? 'Play again' : 'Start challenge',
            () => {
              game.startChallenge(ch.id);
              this.ui.show('workshop');
            },
            { class: complete ? '' : 'primary' }
          ),
        ])
      );
    }

    root.append(
      el('div', { class: 'topbar' }, [
        iconButton('‹', () => this.ui.back(), 'Back'),
        el('h2', { text: 'Challenges' }),
        el('div', { class: 'wallet', text: money(game.money) }),
      ]),
      body
    );
  }
}
