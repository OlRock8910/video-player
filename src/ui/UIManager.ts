import { bus, type GameEvents } from '../core/EventBus';
import { audio } from '../core/AudioManager';
import { haptics } from '../core/HapticsManager';
import { clearNode, el } from './dom';

export type ScreenId =
  | 'menu'
  | 'workshop'
  | 'shop'
  | 'garage'
  | 'career'
  | 'challenges'
  | 'settings'
  | 'benchmark'
  | 'showcase';

export interface Screen {
  id: ScreenId;
  /** Build the DOM for this screen. Called on every entry. */
  render(root: HTMLElement): void;
  /** Called after the screen becomes visible. */
  onEnter?(): void;
  onExit?(): void;
  /** Return true to consume the back action. */
  onBack?(): boolean;
}

/**
 * Screen router and notification host (§45, §54). Screens are plain objects
 * that render into a container; the manager handles transitions, the toast
 * queue, and the Android back button.
 */
export class UIManager {
  private root: HTMLElement;
  private screens = new Map<ScreenId, { screen: Screen; node: HTMLElement }>();
  private toastHost: HTMLElement;
  private current: ScreenId | null = null;
  private history: ScreenId[] = [];
  private overlays: HTMLElement[] = [];

  constructor(root: HTMLElement) {
    this.root = root;
    this.toastHost = el('div', { id: 'toasts' });
    root.append(this.toastHost);
    bus.on('toast', (p) => this.toast(p));

    // Android hardware back / browser back.
    window.addEventListener('popstate', () => this.back());
    history.pushState({ pcbuilder: true }, '');
  }

  register(screen: Screen): void {
    const node = el('div', { class: 'screen', id: screen.id });
    this.root.append(node);
    this.screens.set(screen.id, { screen, node });
  }

  get activeScreen(): ScreenId | null {
    return this.current;
  }

  show(id: ScreenId, opts: { replace?: boolean } = {}): void {
    const entry = this.screens.get(id);
    if (!entry) {
      console.warn(`[ui] unknown screen "${id}"`);
      return;
    }
    if (this.current === id) return;

    if (this.current) {
      const prev = this.screens.get(this.current);
      prev?.node.classList.remove('active');
      prev?.screen.onExit?.();
      if (!opts.replace) this.history.push(this.current);
    }

    clearNode(entry.node);
    try {
      entry.screen.render(entry.node);
    } catch (err) {
      // A screen that fails to render must not strand the player (§50).
      console.error(`[ui] screen "${id}" failed to render`, err);
      entry.node.append(
        el('div', { class: 'panel', style: { margin: '24px', padding: '18px' } }, [
          el('h2', { text: 'Something went wrong' }),
          el('p', { class: 'dim', text: 'This screen could not be drawn. Your progress is safe.' }),
        ])
      );
    }
    this.current = id;
    // Force a reflow so the transition plays on first show.
    void entry.node.offsetHeight;
    entry.node.classList.add('active');
    entry.screen.onEnter?.();
    bus.emit('screen:change', { screen: id });
  }

  /** Re-render the current screen in place, keeping it visible. */
  refresh(): void {
    if (!this.current) return;
    const entry = this.screens.get(this.current);
    if (!entry) return;
    clearNode(entry.node);
    try {
      entry.screen.render(entry.node);
    } catch (err) {
      console.error('[ui] refresh failed', err);
    }
  }

  back(): void {
    // Overlays close first.
    if (this.overlays.length > 0) {
      this.closeOverlay();
      return;
    }
    const entry = this.current ? this.screens.get(this.current) : undefined;
    if (entry?.screen.onBack?.()) return;

    const prev = this.history.pop();
    if (prev) {
      const target = prev;
      // show() would push the current screen back onto history, so bypass it.
      const cur = this.current ? this.screens.get(this.current) : undefined;
      cur?.node.classList.remove('active');
      cur?.screen.onExit?.();
      this.current = null;
      this.show(target, { replace: true });
    }
    // Keep a state entry so the next back press still reaches us.
    history.pushState({ pcbuilder: true }, '');
  }

  /* ---------------------------------------------------------------- */
  /* Overlays                                                          */
  /* ---------------------------------------------------------------- */

  /** Bottom sheet overlay. Returns a close function. */
  openSheet(title: string, content: HTMLElement, opts: { onClose?: () => void } = {}): () => void {
    const sheet = el('div', { class: 'sheet' }, [
      el('div', { class: 'sheet-grip' }),
      el('h2', { text: title, style: { marginBottom: '12px' } }),
      content,
    ]);
    const overlay = el(
      'div',
      {
        class: 'overlay',
        on: {
          click: (e) => {
            if (e.target === overlay) close();
          },
        },
      },
      [sheet]
    );
    this.root.append(overlay);
    this.overlays.push(overlay);
    void overlay.offsetHeight;
    overlay.classList.add('active');
    audio.play('ui-tap');

    const close = (): void => {
      overlay.classList.remove('active');
      const i = this.overlays.indexOf(overlay);
      if (i >= 0) this.overlays.splice(i, 1);
      setTimeout(() => overlay.remove(), 320);
      opts.onClose?.();
    };
    return close;
  }

  /** Full-bleed overlay used for cinematics, POST and reports. */
  openFull(content: HTMLElement): () => void {
    const overlay = el('div', { class: 'overlay' }, [content]);
    overlay.style.background = 'rgba(3, 5, 8, 0.97)';
    this.root.append(overlay);
    this.overlays.push(overlay);
    void overlay.offsetHeight;
    overlay.classList.add('active');

    return (): void => {
      overlay.classList.remove('active');
      const i = this.overlays.indexOf(overlay);
      if (i >= 0) this.overlays.splice(i, 1);
      setTimeout(() => overlay.remove(), 320);
    };
  }

  closeOverlay(): void {
    const overlay = this.overlays.pop();
    if (!overlay) return;
    overlay.classList.remove('active');
    audio.play('ui-back');
    setTimeout(() => overlay.remove(), 320);
  }

  get hasOverlay(): boolean {
    return this.overlays.length > 0;
  }

  /* ---------------------------------------------------------------- */
  /* Toasts                                                            */
  /* ---------------------------------------------------------------- */

  private toast(p: GameEvents['toast']): void {
    const node = el('div', { class: `toast ${p.kind}` }, [
      el('div', { class: 'tt', text: p.text }),
      p.detail ? el('div', { class: 'td', text: p.detail }) : null,
    ]);
    this.toastHost.append(node);
    if (p.kind === 'error') {
      audio.play('error');
      haptics.fire('error');
    } else if (p.kind === 'good') {
      audio.play('success');
      haptics.fire('success');
    }

    // Cap the stack so a burst cannot fill the screen.
    while (this.toastHost.childElementCount > 3) this.toastHost.firstElementChild?.remove();

    setTimeout(() => {
      node.classList.add('leaving');
      setTimeout(() => node.remove(), 260);
    }, p.detail ? 4200 : 2600);
  }
}
