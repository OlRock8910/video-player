import type { CompatibilityIssue } from '../sim/CompatibilityManager';
import type { Slot } from '../data/types';
import type { BuildState } from '../sim/Build';

/**
 * Every cross-system message in the game. Managers talk through this rather
 * than holding references to each other (§54).
 */
export interface GameEvents {
  'screen:change': { screen: string };
  'build:state': { state: BuildState };
  'build:installed': { componentId: string; slot: Slot };
  'build:removed': { componentId: string; slot: Slot };
  'build:rejected': { issue: CompatibilityIssue };
  'build:paste': { quality: string };
  'build:screw': { slot: Slot; driven: number; required: number };
  'build:cable': { cable: string; connected: boolean };
  'build:panel': { removed: boolean };
  'part:selected': { componentId: string | null };
  'part:grabbed': { componentId: string };
  'part:dropped': { componentId: string };
  'toast': { text: string; kind: 'info' | 'good' | 'warn' | 'error'; detail?: string };
  'tutorial:step': { index: number; id: string };
  'tutorial:complete': Record<string, never>;
  'achievement': { id: string };
  'money:change': { amount: number; total: number };
  'reputation:change': { amount: number; total: number };
  'camera:focus': { target: string };
  'settings:change': Record<string, never>;
  'save:written': Record<string, never>;
}

type Handler<K extends keyof GameEvents> = (payload: GameEvents[K]) => void;

class Bus {
  private handlers = new Map<string, Set<(p: unknown) => void>>();

  on<K extends keyof GameEvents>(event: K, handler: Handler<K>): () => void {
    let set = this.handlers.get(event);
    if (!set) {
      set = new Set();
      this.handlers.set(event, set);
    }
    set.add(handler as (p: unknown) => void);
    return () => this.off(event, handler);
  }

  once<K extends keyof GameEvents>(event: K, handler: Handler<K>): void {
    const off = this.on(event, (payload) => {
      off();
      handler(payload);
    });
  }

  off<K extends keyof GameEvents>(event: K, handler: Handler<K>): void {
    this.handlers.get(event)?.delete(handler as (p: unknown) => void);
  }

  emit<K extends keyof GameEvents>(event: K, payload: GameEvents[K]): void {
    const set = this.handlers.get(event);
    if (!set) return;
    // Copy so handlers may unsubscribe during dispatch.
    for (const h of [...set]) {
      try {
        h(payload);
      } catch (err) {
        // One bad listener must never take the frame down (§50).
        console.error(`[bus] handler for "${event}" threw`, err);
      }
    }
  }

  clear(): void {
    this.handlers.clear();
  }
}

export const bus = new Bus();

/** Convenience for the most common notification. */
export const toast = (
  text: string,
  kind: GameEvents['toast']['kind'] = 'info',
  detail?: string
): void => bus.emit('toast', { text, kind, ...(detail ? { detail } : {}) });
