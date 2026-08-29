import * as THREE from 'three';
import type { UIManager } from './UIManager';
import { el } from './dom';
import { PC_STAND_X, PC_STAND_Z, type SceneRoot } from '../scene/SceneRoot';
import type { BuildScene } from '../scene/BuildScene';
import type { Build } from '../sim/Build';
import { audio } from '../core/AudioManager';
import { haptics } from '../core/HapticsManager';
import { settings } from '../core/Settings';
import { runPost } from '../sim/PostManager';
import { noiseDb } from '../sim/ThermalManager';

export interface PowerOnOptions {
  ui: UIManager;
  scene: SceneRoot;
  buildScene: BuildScene;
  build: Build;
  onComplete: () => void;
}

/**
 * The power-on moment (§21). A scripted beat sheet: click, LED, twitch, pause,
 * spin-up, lighting, glow, camera orbit, monitor, POST.
 *
 * Every step is a timed callback so the whole thing can be scaled down for
 * reduce-motion without restructuring it.
 */
export function showPowerOnSequence(opts: PowerOnOptions): void {
  const { ui, scene, buildScene, build } = opts;
  const fast = settings.get().reduceMotion;
  const scale = fast ? 0.28 : 1;

  const caption = el('div', {
    style: {
      position: 'absolute',
      left: '0',
      right: '0',
      bottom: '18%',
      textAlign: 'center',
      fontSize: '0.82rem',
      letterSpacing: '0.32em',
      textTransform: 'uppercase',
      color: '#8b9bb4',
      opacity: '0',
      transition: 'opacity 0.5s ease',
    },
  });

  const overlayContent = el('div', { style: { position: 'absolute', inset: '0', pointerEvents: 'none' } }, [
    caption,
  ]);
  const closeOverlay = ui.openFull(overlayContent);
  // The cinematic plays over the live 3D scene, so keep the veil transparent.
  (overlayContent.parentElement as HTMLElement).style.background = 'transparent';
  (overlayContent.parentElement as HTMLElement).style.backdropFilter = 'none';

  const say = (text: string): void => {
    caption.textContent = text;
    caption.style.opacity = '1';
  };

  const timers: number[] = [];
  const at = (ms: number, fn: () => void): void => {
    timers.push(window.setTimeout(fn, ms * scale));
  };

  scene.cameraController.setLocked(true);
  const layout = buildScene.caseLayout;
  const caseSize = layout ? Math.max(layout.height, layout.depth) : 5;
  const casePos = new THREE.Vector3(PC_STAND_X, caseSize * 0.46, PC_STAND_Z);
  scene.cameraController.frame(casePos, caseSize, { phi: Math.PI * 0.44, margin: 1.3 });

  // 1. Silence, then the switch.
  say('Press the power button');
  at(400, () => {
    audio.play('power-button');
    haptics.fire('firm');
    say('');
  });

  // 2. The PSU relay clunks and the power LED comes up.
  at(900, () => {
    audio.play('relay');
    if (buildScene.builtCase) {
      buildScene.builtCase.powerLed.emissive.setHex(0x00d0ff);
      buildScene.builtCase.powerLed.emissiveIntensity = 2.6;
    }
  });

  // 3. Fans twitch, then stop. The detail everyone recognises.
  at(1150, () => {
    buildScene.twitchFans();
    audio.play('plastic');
  });

  // 4. A beat of nothing.
  at(1750, () => {
    audio.play('psu-hum');
  });

  // 5. Everything spins up together.
  at(2100, () => {
    buildScene.setPowered(true);
    audio.play('fan-spinup');
    audio.startAmbience(noiseDb(build));
    haptics.fire('boot');
    say('');
  });

  // 6. Lighting comes alive zone by zone.
  at(2700, () => {
    audio.play('rgb-on');
    buildScene.setRgbProfile(build.rgb);
  });

  // 7. Slow orbit around the finished machine.
  at(3000, () => {
    scene.cameraController.frame(casePos, caseSize, { phi: Math.PI * 0.4, margin: 1.12 });
    const orbit = scene.onFrame((dt) => {
      scene.cameraController.orbitBy(dt * 0.32);
    });
    timers.push(window.setTimeout(() => orbit(), 3400 * scale));
  });

  // 8. The monitor wakes and POST runs.
  at(4400, () => {
    scene.setMonitor(true, 0x0a2a18);
    audio.play('post-beep');
    showPostScreen(ui, build, () => {
      for (const t of timers) window.clearTimeout(t);
      closeOverlay();
      scene.cameraController.setLocked(false);
      opts.onComplete();
    });
    closeOverlay();
  });
}

/**
 * The fake BIOS/POST screen (§22). Lines appear one at a time with a beep, and
 * a failure stops the list where the fault is.
 */
export function showPostScreen(ui: UIManager, build: Build, onDone: () => void): void {
  const result = runPost(build);
  const lines = el('div', { class: 'post-screen' });

  const container = el(
    'div',
    {
      style: {
        position: 'absolute',
        inset: '0',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: '0 6vw',
        background: '#04070a',
      },
    },
    [
      el('div', {
        class: 'post-screen',
        style: { paddingBottom: '4px', color: '#9fe8c0', fontWeight: '700', letterSpacing: '0.18em' },
        text: 'PC BUILDER BIOS  v2.4',
      }),
      el('div', {
        style: {
          height: '1px',
          background: 'rgba(159,232,192,0.24)',
          margin: '0 24px 6px',
        },
      }),
      lines,
    ]
  );

  const close = ui.openFull(container);

  let i = 0;
  const step = (): void => {
    if (i >= result.lines.length) {
      window.setTimeout(finish, 620);
      return;
    }
    const line = result.lines[i];
    const colour =
      line.status === 'OK' ? '#8ce8b0' : line.status === 'WARN' ? '#ffd23f' : '#ff6b7d';
    lines.append(
      el('div', { class: 'post-line' }, [
        el('span', { text: line.label }),
        el('span', { class: 'post-dots', text: ' '.padEnd(2, ' ') + '.'.repeat(40) }),
        el('span', { text: `${line.value}  ${line.status}`, style: { color: colour } }),
      ])
    );
    audio.play(line.status === 'FAIL' ? 'error' : 'post-beep');
    i += 1;
    window.setTimeout(step, settings.get().reduceMotion ? 60 : 300);
  };

  const finish = (): void => {
    const ok = result.success;
    lines.append(
      el('div', {
        class: 'post-line',
        style: {
          marginTop: '18px',
          fontWeight: '700',
          letterSpacing: '0.2em',
          color: ok ? '#8ce8b0' : '#ff6b7d',
        },
        text: ok ? 'SYSTEM READY' : `HALTED — ${result.failure.toUpperCase().replace(/-/g, ' ')}`,
      })
    );
    if (ok) {
      audio.play('success');
      haptics.fire('success');
    } else {
      audio.play('error');
      haptics.fire('error');
    }
    window.setTimeout(
      () => {
        close();
        onDone();
      },
      settings.get().reduceMotion ? 400 : 1500
    );
  };

  window.setTimeout(step, 420);
}
