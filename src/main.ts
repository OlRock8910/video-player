import './styles/main.css';
import { SceneRoot } from './scene/SceneRoot';
import { UIManager } from './ui/UIManager';
import { MenuScreen } from './ui/MenuScreen';
import { WorkshopScreen } from './ui/WorkshopScreen';
import { ShopScreen } from './ui/ShopScreen';
import { CareerScreen } from './ui/CareerScreen';
import { GarageScreen } from './ui/GarageScreen';
import { ChallengesScreen } from './ui/ChallengesScreen';
import { SettingsScreen } from './ui/SettingsScreen';
import { ShowcaseScreen } from './ui/ShowcaseScreen';
import { game } from './core/GameManager';
import { save } from './core/SaveManager';
import { settings, recommendQuality } from './core/Settings';
import { audio } from './core/AudioManager';
import { bus } from './core/EventBus';
import { installDebugTools } from './core/DebugMode';

/**
 * Entry point. Boots the save, picks a quality preset for the device, builds
 * the scene and the screens, and starts the frame loop.
 */
async function boot(): Promise<void> {
  const loading = document.getElementById('loading')!;
  const loadingText = document.getElementById('loading-text')!;
  const setStatus = (text: string): void => {
    loadingText.textContent = text;
  };

  try {
    setStatus('Reading save');
    game.init();
    settings.apply();

    if (settings.get().autoQuality) {
      const preset = recommendQuality();
      settings.set('quality', preset);
    }

    setStatus('Building workshop');
    const canvas = document.getElementById('scene-canvas') as HTMLCanvasElement;
    const scene = new SceneRoot(canvas);

    setStatus('Preparing tools');
    const uiRoot = document.getElementById('ui-root')!;
    const ui = new UIManager(uiRoot);

    const workshop = new WorkshopScreen(scene, ui);
    ui.register(new MenuScreen(scene, ui));
    ui.register(workshop);
    ui.register(new ShopScreen(ui));
    ui.register(new CareerScreen(ui));
    ui.register(new GarageScreen(ui));
    ui.register(new ChallengesScreen(ui));
    ui.register(new SettingsScreen(ui, scene));
    ui.register(new ShowcaseScreen(scene, ui, () => workshop.sceneForShowcase));

    installDebugTools(scene, ui);

    // Audio needs a gesture; the first touch anywhere unlocks it (§43).
    const unlock = (): void => {
      audio.unlock();
      audio.applyVolumes();
      window.removeEventListener('pointerdown', unlock);
      window.removeEventListener('keydown', unlock);
    };
    window.addEventListener('pointerdown', unlock);
    window.addEventListener('keydown', unlock);

    scene.start();
    ui.show('menu');

    // Never lose a build to a backgrounded app (§48).
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        game.persistBuild();
        save.flush();
        audio.stopMusic();
        audio.stopAmbience();
      } else if (ui.activeScreen === 'menu') {
        audio.playMusic('menu');
      } else if (ui.activeScreen === 'workshop') {
        audio.playMusic('workshop');
      }
    });
    window.addEventListener('pagehide', () => {
      game.persistBuild();
      save.flush();
    });

    bus.on('settings:change', () => {
      scene.applyQuality();
      save.queue(800);
    });

    setStatus('Ready');
    loading.classList.add('done');
    setTimeout(() => loading.remove(), 500);
  } catch (err) {
    // A hard boot failure must still tell the player something useful (§50).
    console.error('[boot] failed', err);
    loading.innerHTML = '';
    const box = document.createElement('div');
    box.style.cssText =
      'max-width:420px;padding:24px;text-align:center;font-family:sans-serif;color:#e6ecf5';
    box.innerHTML = `
      <h2 style="letter-spacing:.12em">Could not start</h2>
      <p style="color:#8b9bb4;line-height:1.5">
        PC Builder needs WebGL. If your device supports it, try closing other apps and reopening.
      </p>
      <pre style="color:#5a6779;font-size:11px;white-space:pre-wrap;text-align:left">${
        err instanceof Error ? err.message : String(err)
      }</pre>`;
    loading.append(box);
  }
}

void boot();
