import { audio } from '../core/AudioManager';
import { haptics } from '../core/HapticsManager';

/** Tiny DOM helper so screens read as structure rather than boilerplate. */
export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: {
    class?: string;
    text?: string;
    html?: string;
    id?: string;
    attrs?: Record<string, string>;
    style?: Partial<CSSStyleDeclaration>;
    on?: Partial<{ [E in keyof HTMLElementEventMap]: (ev: HTMLElementEventMap[E]) => void }>;
  } = {},
  children: (Node | string | null | undefined | false)[] = []
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (props.class) node.className = props.class;
  if (props.id) node.id = props.id;
  if (props.text !== undefined) node.textContent = props.text;
  if (props.html !== undefined) node.innerHTML = props.html;
  if (props.attrs) for (const [k, v] of Object.entries(props.attrs)) node.setAttribute(k, v);
  if (props.style) Object.assign(node.style, props.style);
  if (props.on) {
    for (const [k, v] of Object.entries(props.on)) {
      node.addEventListener(k, v as EventListener);
    }
  }
  for (const c of children) {
    if (c === null || c === undefined || c === false) continue;
    node.append(typeof c === 'string' ? document.createTextNode(c) : c);
  }
  return node;
}

/** A button that always gives audio + haptic feedback (§41, §42, §45). */
export function button(
  label: string,
  onClick: () => void,
  opts: { class?: string; icon?: string; sub?: string; sound?: 'ui-tap' | 'ui-back' } = {}
): HTMLButtonElement {
  const btn = el('button', {
    class: `btn ${opts.class ?? ''}`.trim(),
    on: {
      click: (e) => {
        e.preventDefault();
        audio.unlock();
        audio.play(opts.sound ?? 'ui-tap');
        haptics.fire('light');
        onClick();
      },
    },
  });
  if (opts.icon) btn.append(el('span', { class: 'icon', text: opts.icon }));
  const labelWrap = el('span', { class: 'label' }, [
    document.createTextNode(label),
    opts.sub ? el('span', { class: 'sub', text: opts.sub }) : null,
  ]);
  btn.append(labelWrap);
  return btn;
}

export function iconButton(glyph: string, onClick: () => void, title = ''): HTMLButtonElement {
  return el('button', {
    class: 'icon-btn',
    text: glyph,
    attrs: title ? { 'aria-label': title, title } : {},
    on: {
      click: () => {
        audio.unlock();
        audio.play('ui-tap');
        haptics.fire('tick');
        onClick();
      },
    },
  });
}

export function chip(label: string, active: boolean, onClick: () => void): HTMLButtonElement {
  return el('button', {
    class: `chip ${active ? 'active' : ''}`,
    text: label,
    on: {
      click: () => {
        audio.play('ui-tap');
        haptics.fire('tick');
        onClick();
      },
    },
  });
}

export function toggle(on: boolean, onChange: (v: boolean) => void): HTMLElement {
  const node = el('div', {
    class: `toggle ${on ? 'on' : ''}`,
    on: {
      click: () => {
        const next = !node.classList.contains('on');
        node.classList.toggle('on', next);
        audio.play('ui-tap');
        haptics.fire('tick');
        onChange(next);
      },
    },
  });
  return node;
}

export function slider(
  value: number,
  min: number,
  max: number,
  step: number,
  onChange: (v: number) => void
): HTMLInputElement {
  const input = el('input', {
    attrs: {
      type: 'range',
      min: String(min),
      max: String(max),
      step: String(step),
      value: String(value),
    },
    on: {
      input: (e) => onChange(Number((e.target as HTMLInputElement).value)),
    },
  }) as HTMLInputElement;
  // Sliders must scroll-lock, or dragging them pans the page.
  input.style.touchAction = 'none';
  return input;
}

export function row(key: string, value: Node | string): HTMLElement {
  return el('div', { class: 'row' }, [
    el('span', { class: 'k', text: key }),
    typeof value === 'string' ? el('span', { class: 'v', text: value }) : value,
  ]);
}

export function sectionTitle(text: string): HTMLElement {
  return el('div', { class: 'section-title', text });
}

export const money = (n: number): string =>
  `$${Math.round(n).toLocaleString('en-US')}`;

export const clearNode = (node: HTMLElement): void => {
  while (node.firstChild) node.removeChild(node.firstChild);
};
