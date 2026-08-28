import type { UIManager } from './UIManager';
import { button, chip, el, sectionTitle, slider } from './dom';
import { save } from '../core/SaveManager';
import { audio } from '../core/AudioManager';
import { toast } from '../core/EventBus';
import { RGB_ZONES, type Build, type RgbProfile, type RgbZone } from '../sim/Build';

const MODES: RgbProfile['mode'][] = ['static', 'breathing', 'rainbow', 'wave', 'pulse', 'reactive'];

const PALETTE = [
  0x00d0ff, 0x00e5a0, 0x9d7bff, 0xff4fd8, 0xff3d6e, 0xff7a2f, 0xffd23f, 0xffffff,
];

const ZONE_LABELS: Record<RgbZone, string> = {
  ram: 'Memory',
  gpu: 'Graphics card',
  fans: 'Case fans',
  motherboard: 'Motherboard',
  case: 'Case lighting',
  aio: 'AIO pump',
};

/** RGB control panel with per-zone colours and saved profiles (§32). */
export function openRgbPanel(ui: UIManager, build: Build, onChange: () => void): void {
  let activeZone: RgbZone = 'fans';

  const body = el('div', { class: 'screen-scroll', style: { padding: '0 0 12px' } });

  const rebuild = (): void => {
    body.replaceChildren();

    body.append(
      sectionTitle('Effect'),
      el(
        'div',
        { style: { display: 'flex', flexWrap: 'wrap', gap: '7px' } },
        MODES.map((m) =>
          chip(m.toUpperCase(), build.rgb.mode === m, () => {
            build.rgb.mode = m;
            onChange();
            rebuild();
          })
        )
      ),

      sectionTitle('Zone'),
      el(
        'div',
        { style: { display: 'flex', flexWrap: 'wrap', gap: '7px' } },
        RGB_ZONES.map((z) =>
          chip(ZONE_LABELS[z], activeZone === z, () => {
            activeZone = z;
            rebuild();
          })
        )
      ),

      sectionTitle(`${ZONE_LABELS[activeZone]} colour`),
      el(
        'div',
        { style: { display: 'flex', flexWrap: 'wrap', gap: '9px' } },
        PALETTE.map((c) =>
          el('button', {
            class: `swatch ${build.rgb.zones[activeZone] === c ? 'active' : ''}`,
            style: { background: `#${c.toString(16).padStart(6, '0')}` },
            on: {
              click: () => {
                build.rgb.zones[activeZone] = c;
                audio.play('ui-tap');
                onChange();
                rebuild();
              },
            },
          })
        )
      ),

      sectionTitle('Adjust'),
      el('div', { class: 'list' }, [
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Speed' }),
          slider(build.rgb.speed, 0.05, 1.5, 0.05, (v) => {
            build.rgb.speed = v;
            onChange();
          }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Brightness' }),
          slider(build.rgb.brightness, 0.1, 1.5, 0.05, (v) => {
            build.rgb.brightness = v;
            onChange();
          }),
        ]),
        el('div', { class: 'row' }, [
          el('span', { class: 'k', text: 'Apply to all zones' }),
          button('Apply', () => {
            const c = build.rgb.zones[activeZone];
            for (const z of RGB_ZONES) build.rgb.zones[z] = c;
            onChange();
            rebuild();
          }),
        ]),
      ]),

      sectionTitle('Profiles'),
      el(
        'div',
        { class: 'list' },
        Object.keys(save.get().rgbProfiles).map((name) =>
          el('div', { class: 'row' }, [
            el('span', { class: 'k', text: name }),
            el('div', { style: { display: 'flex', gap: '8px' } }, [
              button('Load', () => {
                const p = save.get().rgbProfiles[name];
                if (!p) return;
                build.rgb = JSON.parse(JSON.stringify(p)) as RgbProfile;
                onChange();
                rebuild();
                toast(`Loaded "${name}"`, 'good');
              }),
            ]),
          ])
        )
      ),
      el('div', { class: 'btn-row', style: { marginTop: '12px' } }, [
        button('Save as new profile', () => {
          const name = `Profile ${Object.keys(save.get().rgbProfiles).length + 1}`;
          save.update((d) => {
            d.rgbProfiles[name] = JSON.parse(JSON.stringify(build.rgb)) as RgbProfile;
          });
          toast(`Saved "${name}"`, 'good');
          rebuild();
        }),
      ])
    );
  };

  rebuild();
  ui.openSheet('RGB Lighting', body);
}
