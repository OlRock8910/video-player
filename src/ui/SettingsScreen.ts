import type { Screen, UIManager } from './UIManager';
import { button, chip, el, iconButton, sectionTitle, slider, toggle } from './dom';
import { settings, recommendQuality, type QualityPreset } from '../core/Settings';
import { save } from '../core/SaveManager';
import { audio } from '../core/AudioManager';
import { haptics } from '../core/HapticsManager';
import { toast } from '../core/EventBus';
import type { SceneRoot } from '../scene/SceneRoot';

const PRESETS: QualityPreset[] = ['low', 'medium', 'high', 'ultra'];

const PRESET_NOTES: Record<QualityPreset, string> = {
  low: 'No shadows, simple reflections, reduced particles',
  medium: 'Medium shadows, basic reflections',
  high: 'High shadows, better lighting, bloom',
  ultra: 'High-quality shadows, reflections, bloom, enhanced lighting',
};

/** Graphics, audio, controls, haptics and accessibility (§39, §42, §49). */
export class SettingsScreen implements Screen {
  readonly id = 'settings' as const;

  constructor(
    private ui: UIManager,
    private scene: SceneRoot
  ) {}

  render(root: HTMLElement): void {
    const s = settings.get();
    const body = el('div', { class: 'screen-scroll' });

    const settingRow = (label: string, control: Node, note?: string): HTMLElement =>
      el('div', { class: 'row' }, [
        el('span', { class: 'k' }, [
          el('div', { text: label }),
          note ? el('div', { class: 'faint', style: { fontSize: '0.7rem', marginTop: '2px' }, text: note }) : null,
        ]),
        control,
      ]);

    /* ---- graphics ------------------------------------------------- */
    body.append(sectionTitle('Graphics'));
    const presetRow = el(
      'div',
      { style: { display: 'flex', flexWrap: 'wrap', gap: '7px', marginBottom: '9px' } },
      PRESETS.map((p) =>
        chip(p.toUpperCase(), s.quality === p && !s.autoQuality, () => {
          settings.set('autoQuality', false);
          settings.set('quality', p);
          this.scene.applyQuality();
          this.ui.refresh();
        })
      )
    );
    body.append(
      presetRow,
      el('div', { class: 'faint', style: { fontSize: '0.74rem', marginBottom: '10px' }, text: PRESET_NOTES[s.quality] }),
      el('div', { class: 'list' }, [
        settingRow(
          'Auto quality',
          toggle(s.autoQuality, (v) => {
            settings.set('autoQuality', v);
            if (v) {
              const rec = recommendQuality();
              settings.set('quality', rec);
              this.scene.applyQuality();
              toast(`Auto: ${rec.toUpperCase()}`, 'info', 'Chosen from this device’s reported capability.');
            }
            this.ui.refresh();
          }),
          'Pick a preset from device capability'
        ),
        settingRow(
          'Frame rate target',
          el('div', { style: { display: 'flex', gap: '7px' } }, [
            chip('30', s.targetFps === 30, () => {
              settings.set('targetFps', 30);
              this.ui.refresh();
            }),
            chip('60', s.targetFps === 60, () => {
              settings.set('targetFps', 60);
              this.ui.refresh();
            }),
          ])
        ),
        settingRow(
          'Dynamic resolution',
          toggle(s.dynamicResolution, (v) => settings.set('dynamicResolution', v)),
          'Scale the render buffer to hold the target'
        ),
      ])
    );

    /* ---- audio ---------------------------------------------------- */
    body.append(
      sectionTitle('Audio'),
      el('div', { class: 'list' }, [
        settingRow(
          'Master volume',
          slider(s.masterVolume, 0, 1, 0.05, (v) => {
            settings.set('masterVolume', v);
            audio.applyVolumes();
          })
        ),
        settingRow(
          'Music',
          slider(s.musicVolume, 0, 1, 0.05, (v) => {
            settings.set('musicVolume', v);
            audio.applyVolumes();
          })
        ),
        settingRow(
          'Sound effects',
          slider(s.sfxVolume, 0, 1, 0.05, (v) => {
            settings.set('sfxVolume', v);
            audio.applyVolumes();
            audio.play('ui-tap');
          })
        ),
      ])
    );

    /* ---- controls ------------------------------------------------- */
    body.append(
      sectionTitle('Controls'),
      el('div', { class: 'list' }, [
        settingRow(
          'Camera sensitivity',
          slider(s.cameraSensitivity, 0.4, 2, 0.1, (v) => settings.set('cameraSensitivity', v))
        ),
        settingRow('Invert horizontal', toggle(s.invertCameraX, (v) => settings.set('invertCameraX', v))),
        settingRow(
          'Haptics',
          toggle(s.haptics, (v) => {
            settings.set('haptics', v);
            if (v) haptics.fire('medium');
          }),
          haptics.available ? 'Vibration feedback on install and screws' : 'Not supported on this device'
        ),
        settingRow(
          'Left-handed layout',
          toggle(s.leftHanded, (v) => settings.set('leftHanded', v)),
          'Mirror the primary controls'
        ),
      ])
    );

    /* ---- accessibility -------------------------------------------- */
    body.append(
      sectionTitle('Accessibility'),
      el('div', { class: 'list' }, [
        settingRow(
          'Text size',
          slider(s.textScale, 0.85, 1.45, 0.05, (v) => settings.set('textScale', v))
        ),
        settingRow(
          'Colour-friendly palette',
          toggle(s.colorBlindSafe, (v) => settings.set('colorBlindSafe', v)),
          'Use blue/orange instead of green/red'
        ),
        settingRow(
          'Reduce motion',
          toggle(s.reduceMotion, (v) => settings.set('reduceMotion', v)),
          'Shorten animations and cinematics'
        ),
        settingRow(
          'Show tutorial hints',
          toggle(s.showTutorial, (v) => settings.set('showTutorial', v))
        ),
      ])
    );

    /* ---- data ----------------------------------------------------- */
    body.append(
      sectionTitle('Data'),
      el('div', { class: 'list' }, [
        settingRow(
          'Developer mode',
          toggle(s.debugMode, (v) => {
            settings.set('debugMode', v);
            toast(v ? 'Developer mode on' : 'Developer mode off', 'info');
            this.ui.refresh();
          }),
          'On-screen performance stats and debug tools'
        ),
      ]),
      el('div', { class: 'btn-row', style: { marginTop: '12px' } }, [
        button(
          'Reset all progress',
          () => {
            const content = el('div', {}, [
              el('div', { class: 'dim', text: 'This erases money, reputation, saved builds and settings. It cannot be undone.' }),
              el('div', { class: 'btn-row', style: { marginTop: '16px' } }, [
                button('Cancel', () => close()),
                button(
                  'Erase everything',
                  () => {
                    save.reset();
                    close();
                    toast('Progress reset', 'info');
                    this.ui.show('menu', { replace: true });
                  },
                  { class: 'danger' }
                ),
              ]),
            ]);
            const close = this.ui.openSheet('Are you sure?', content);
          },
          { class: 'danger' }
        ),
      ]),
      el('div', {
        class: 'faint',
        style: { fontSize: '0.7rem', textAlign: 'center', marginTop: '20px' },
        text: 'PC BUILDER — Build. Power. Perform.',
      })
    );

    root.append(
      el('div', { class: 'topbar' }, [
        iconButton('‹', () => this.ui.back(), 'Back'),
        el('h2', { text: 'Settings' }),
      ]),
      body
    );
  }

  onExit(): void {
    save.flush();
  }
}
