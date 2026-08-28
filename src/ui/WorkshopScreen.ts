import * as THREE from 'three';
import type { Screen, UIManager } from './UIManager';
import { button, chip, clearNode, el, iconButton, money, row, sectionTitle } from './dom';
import { game } from '../core/GameManager';
import { save } from '../core/SaveManager';
import { audio } from '../core/AudioManager';
import { haptics } from '../core/HapticsManager';
import { bus, toast } from '../core/EventBus';
import type { SceneRoot } from '../scene/SceneRoot';
import { BuildScene } from '../scene/BuildScene';
import { InteractionManager, type HeldPart } from '../scene/InteractionManager';
import type { CableKind, Component, Slot } from '../data/types';
import { isCase, isFan } from '../data/types';
import { getComponent } from '../data/catalog';
import {
  advanceState,
  buildCost,
  missingCables,
  partInSlot,
  recomputeState,
  requiredCables,
  screwsFor,
} from '../sim/Build';
import { auditBuild, canInstall, slotLabel, standoffsNeeded } from '../sim/CompatibilityManager';
import { BUILD_STEPS, currentStep, stepProgress, type BuildStep } from '../sim/BuildSteps';
import { CABLE_LABELS, cableReport } from '../sim/CableManager';
import { powerBreakdown } from '../sim/PowerManager';
import { thermalReport } from '../sim/ThermalManager';
import { runBenchmark } from '../sim/BenchmarkManager';
import { runPost } from '../sim/PostManager';
import { showPowerOnSequence } from './PowerOnSequence';
import { showBuildReport } from './BuildReport';
import { openRgbPanel } from './RgbPanel';

/**
 * The building workshop (§4-§21). Owns the 3D build scene, the step pipeline,
 * the parts tray and every install interaction.
 */
export class WorkshopScreen implements Screen {
  readonly id = 'workshop' as const;

  private hud!: HTMLElement;
  private stepLabel!: HTMLElement;
  private stepText!: HTMLElement;
  private progressFill!: HTMLElement;
  private trayScroll!: HTMLElement;
  private holdControls!: HTMLElement;
  private tooltip!: HTMLElement;

  private buildScene = new BuildScene();
  private interaction: InteractionManager | null = null;
  private detachFrame: (() => void) | null = null;
  private startedAt = 0;
  private cinematicActive = false;

  constructor(
    private scene: SceneRoot,
    private ui: UIManager
  ) {
    this.scene.buildRoot.add(this.buildScene.root);
  }

  /* ---------------------------------------------------------------- */
  /* Lifecycle                                                         */
  /* ---------------------------------------------------------------- */

  render(root: HTMLElement): void {
    this.hud = el('div', { id: 'workshop-hud' });

    this.stepLabel = el('div', { class: 'step-label' });
    this.stepText = el('div', { class: 'step-text' });
    this.progressFill = el('div', { class: 'progress-fill' });

    const steps = el('div', { class: 'hud-steps panel' }, [
      this.stepLabel,
      this.stepText,
      el('div', { class: 'progress-track' }, [this.progressFill]),
    ]);

    const top = el('div', { class: 'hud-top' }, [
      iconButton('‹', () => this.leave(), 'Back to menu'),
      steps,
      iconButton('☰', () => this.openBuildSheet(), 'Build details'),
    ]);

    this.trayScroll = el('div', { class: 'tray-scroll' });
    this.holdControls = el('div', { class: 'hold-controls' });
    const tray = el('div', { class: 'hud-tray' }, [this.holdControls, this.trayScroll]);

    this.tooltip = el('div', { class: 'tooltip' });

    this.hud.append(top, tray);
    root.append(this.hud, this.tooltip);
    this.refreshHud();
  }

  onEnter(): void {
    this.scene.setWorkshopVisible(true);
    audio.playMusic('workshop');
    this.startedAt = performance.now();

    // Rebuild the 3D scene from the saved build so a restart resumes exactly
    // where the player left off (§48).
    this.buildScene.reset();
    if (game.current.parts.length > 0) {
      this.buildScene.restore(game.current);
    }
    this.buildScene.setRgbProfile(game.current.rgb);

    this.interaction = new InteractionManager(
      this.buildScene,
      this.scene.cameraController,
      this.buildScene.root,
      {
        onSelect: (info) => this.handleSelect(info),
        onDropOnTarget: (held) => this.handleDrop(held),
        onDropAway: (held) => this.handleDropAway(held),
        onScrewDriven: (slot, index) => this.handleScrewDriven(slot, index),
        onPanelScrewOut: () => this.handlePanelScrewOut(),
        onHeldMoved: () => this.refreshHoldControls(),
        onPowerButton: () => this.attemptPowerOn(),
      }
    );

    this.scene.cameraController.setLimits(1.2, 30);
    this.frameCase();

    this.detachFrame = this.scene.onFrame((dt, elapsed) => {
      this.buildScene.update(dt, elapsed);
      this.interaction?.update(dt);
    });

    this.refreshHud();
    this.announceStep(currentStep(game.current), true);
  }

  onExit(): void {
    this.detachFrame?.();
    this.detachFrame = null;
    this.interaction?.dispose();
    this.interaction = null;
    this.commitElapsed();
    game.persistBuild();
    save.flush();
  }

  onBack(): boolean {
    if (this.interaction?.heldPart) {
      this.interaction.cancelHeld();
      this.refreshHoldControls();
      return true;
    }
    return false;
  }

  private leave(): void {
    this.commitElapsed();
    game.persistBuild();
    this.ui.show('menu');
  }

  /** Pull back far enough to see the whole chassis on a portrait screen. */
  private frameCase(): void {
    const layout = this.buildScene.caseLayout;
    const size = layout ? Math.max(layout.height, layout.depth) : 5;
    this.scene.cameraController.frame(new THREE.Vector3(0, size * 0.46, 0), size, {
      phi: Math.PI * 0.44,
      margin: 1.24,
    });
  }

  private commitElapsed(): void {
    if (this.startedAt === 0) return;
    game.current.elapsedMs += performance.now() - this.startedAt;
    this.startedAt = performance.now();
  }

  /* ---------------------------------------------------------------- */
  /* HUD                                                               */
  /* ---------------------------------------------------------------- */

  private refreshHud(): void {
    const build = game.current;
    const step = currentStep(build);
    this.stepLabel.textContent = step.label;
    this.stepText.textContent = step.instruction;
    this.progressFill.style.width = `${Math.round(stepProgress(build) * 100)}%`;
    this.refreshTray(step);
    this.refreshHoldControls();
    this.interaction?.setAllowedSlots(step.slots(build));
  }

  /** The tray offers exactly what the current step needs (§41). */
  private refreshTray(step: BuildStep): void {
    clearNode(this.trayScroll);
    const build = game.current;

    // Non-part steps get action buttons instead of components.
    if (step.id === 'open-panel') {
      this.trayScroll.append(
        this.actionCard('Remove panel', 'Slide it off once the screws are out', () => this.tryOpenPanel())
      );
      return;
    }
    if (step.id === 'standoffs') {
      const mobo = build.parts.find((p) => p.slot === 'mobo-tray');
      const needed = standoffsNeeded(
        mobo ? (getComponent(mobo.componentId) as { formFactor?: string }).formFactor ?? 'ATX' : 'ATX'
      );
      this.trayScroll.append(
        this.actionCard(
          `Place standoff (${build.standoffsPlaced}/${needed})`,
          'Tap to fit the next one',
          () => this.placeStandoff(needed)
        )
      );
      return;
    }
    if (step.id === 'paste') {
      this.trayScroll.append(
        this.actionCard('Apply paste', 'Tap to squeeze a blob', () => this.applyPaste()),
        this.actionCard('Wipe clean', 'Start the application again', () => this.wipePaste())
      );
      return;
    }
    if (step.id === 'cables') {
      for (const cable of requiredCables(build)) {
        const connected = build.connectedCables.includes(cable);
        this.trayScroll.append(
          this.actionCard(
            CABLE_LABELS[cable],
            connected ? 'Connected' : 'Tap to connect',
            () => this.toggleCable(cable),
            connected
          )
        );
      }
      return;
    }
    if (step.id === 'cable-management') {
      for (const cable of requiredCables(build)) {
        const routed = build.routedCables.includes(cable);
        this.trayScroll.append(
          this.actionCard(
            CABLE_LABELS[cable],
            routed ? 'Routed behind tray' : 'Tap to route',
            () => this.toggleRoute(cable),
            routed
          )
        );
      }
      this.trayScroll.append(
        this.actionCard(`Cable tie (${build.cableTies})`, 'Tap to add one', () => this.addTie())
      );
      return;
    }
    if (step.id === 'power-on') {
      this.trayScroll.append(
        this.actionCard('PRESS POWER', 'Or tap the button on the case', () => this.attemptPowerOn())
      );
      return;
    }

    // Component steps.
    if (!step.category) return;
    const parts = game.availableParts().filter((c) => c.category === step.category);
    if (parts.length === 0) {
      this.trayScroll.append(
        this.actionCard('No parts owned', 'Buy one in the shop', () => this.ui.show('shop'))
      );
      return;
    }

    for (const part of parts) {
      const slots = step.slots(build);
      const targetSlot = slots.find((s) => !partInSlot(build, s)) ?? slots[0];
      const check = targetSlot ? canInstall(build, part, targetSlot) : { ok: false, issues: [] };
      const node = el(
        'button',
        {
          class: `tray-item ${check.ok ? '' : 'blocked'}`,
          on: {
            click: () => this.pickUp(part, targetSlot),
          },
        },
        [
          el('div', { class: 'n', text: part.name }),
          el('div', { class: 'b', text: `${part.category.toUpperCase()} · $${part.price}` }),
        ]
      );
      this.trayScroll.append(node);
    }

    if (step.optional) {
      this.trayScroll.append(
        this.actionCard('Skip this step', 'This hardware is optional', () => this.skipStep(step))
      );
    }
  }

  private actionCard(title: string, sub: string, onClick: () => void, done = false): HTMLElement {
    return el(
      'button',
      {
        class: 'tray-item',
        style: done ? { borderColor: 'rgba(0,229,160,0.45)' } : {},
        on: {
          click: () => {
            audio.play('ui-tap');
            haptics.fire('light');
            onClick();
          },
        },
      },
      [el('div', { class: 'n', text: title }), el('div', { class: 'b', text: sub })]
    );
  }

  /** Rotate / cancel controls, shown only while something is in hand (§10). */
  private refreshHoldControls(): void {
    clearNode(this.holdControls);
    const held = this.interaction?.heldPart;
    if (!held) return;

    const aligned = this.interaction?.isHeldAligned() ?? true;
    const needsAlignment = held.component.category === 'cpu' || held.component.category === 'ram';

    if (needsAlignment) {
      this.holdControls.append(
        el('div', {
          class: `chip ${aligned ? 'active' : ''}`,
          text: aligned ? '✓ ALIGNED' : '⚠ CHECK ORIENTATION',
          style: aligned ? {} : { borderColor: 'rgba(255,176,63,0.5)', color: 'var(--warn)' },
        })
      );
      this.holdControls.append(chip('⟳ ROTATE', false, () => this.interaction?.rotateHeld()));
    }
    this.holdControls.append(
      chip(held.overTarget ? '✓ DROP TO INSTALL' : 'DRAG INTO PLACE', held.overTarget, () => {
        if (held.overTarget) this.handleDrop(held);
      })
    );
    this.holdControls.append(
      chip('✕ PUT BACK', false, () => {
        this.interaction?.cancelHeld();
        this.refreshHoldControls();
      })
    );
  }

  /** Move the camera in for the step, then hand control back (§5). */
  private announceStep(step: BuildStep, immediate = false): void {
    if (step.focus) {
      const p = this.buildScene.worldAnchor(step.focus);
      this.scene.cameraController.frame(p, 2.6);
    } else if (step.id === 'open-panel' || step.id === 'choose-case') {
      this.frameCase();
    }
    if (!immediate) {
      toast(step.label, 'info', step.instruction);
    }
  }

  /* ---------------------------------------------------------------- */
  /* Installing                                                        */
  /* ---------------------------------------------------------------- */

  private pickUp(component: Component, slot: Slot): void {
    if (!this.interaction) return;

    // The case is not carried into a slot; it is simply set on the bench.
    if (isCase(component)) {
      this.installCase(component);
      return;
    }
    const check = canInstall(game.current, component, slot);
    if (!check.ok) {
      const blocker = check.issues.find((i) => i.severity === 'blocker');
      if (blocker) {
        game.current.mistakes += 1;
        toast(`❌ ${blocker.title}`, 'error', blocker.detail);
        bus.emit('build:rejected', { issue: blocker });
        if (blocker.focus && !blocker.focus.startsWith('cable-')) {
          this.buildScene.highlightSlot(blocker.focus as Slot);
        }
        return;
      }
    }
    this.interaction.pickUp(component, slot);
    this.buildScene.highlightSlot(slot);
    const p = this.buildScene.worldAnchor(slot);
    this.scene.cameraController.frame(p, 2.8);
    this.refreshHoldControls();
  }

  private installCase(component: Component): void {
    this.buildScene.setCase(component);
    game.current.parts = game.current.parts.filter((p) => p.slot !== 'case');
    game.current.parts.push({
      componentId: component.id,
      slot: 'case',
      screwsDriven: 0,
      screwsRequired: 0,
    });
    if (game.mode !== 'free') game.consumeFromInventory(component.id);
    game.current.state = recomputeState(game.current);
    game.persistBuild();
    audio.play('metal');
    haptics.fire('medium');
    toast('Case on the bench', 'good', "Let's build your first PC.");
    this.refreshHud();
    this.announceStep(currentStep(game.current));
  }

  private handleDrop(held: HeldPart): void {
    if (!this.interaction) return;
    const build = game.current;

    // Orientation gate (§10, §13) — refuse, explain, and let them try again.
    if (!this.interaction.isHeldAligned()) {
      build.mistakes += 1;
      const what = held.component.category === 'cpu' ? 'CPU' : 'Memory';
      toast(`⚠ ${what} orientation incorrect`, 'warn', 'Rotate it until the key lines up with the slot.');
      audio.play('error');
      haptics.fire('error');
      return;
    }

    const check = canInstall(build, held.component, held.slot);
    const blocker = check.issues.find((i) => i.severity === 'blocker');
    if (blocker) {
      build.mistakes += 1;
      toast(`❌ ${blocker.title}`, 'error', blocker.detail);
      return;
    }

    // Seat it: the part animates from where the player let go into the slot.
    const built = held.built;
    const component = held.component;
    const slot = held.slot;
    const screwsRequired = screwsFor(component);

    this.interaction.drop();
    this.buildScene.install(component, slot, built, () => {
      this.playSeatFeedback(component);
    });

    build.parts.push({
      componentId: component.id,
      slot,
      screwsDriven: 0,
      screwsRequired,
      ...(isFan(component) ? { airflow: this.defaultAirflow(slot) } : {}),
    });
    if (game.mode !== 'free') game.consumeFromInventory(component.id);

    build.state = recomputeState(build);
    game.persistBuild();
    bus.emit('build:installed', { componentId: component.id, slot });

    // Non-blocking advice, shown after the success feedback.
    for (const issue of check.issues) {
      if (issue.severity === 'blocker') continue;
      setTimeout(
        () => toast(issue.severity === 'warning' ? `⚠ ${issue.title}` : issue.title, issue.severity === 'warning' ? 'warn' : 'info', issue.detail),
        420
      );
    }

    if (screwsRequired > 0) {
      setTimeout(
        () => toast('Hold each screw to drive it', 'info', `${screwsRequired} screws to fit.`),
        620
      );
    }

    this.buildScene.highlightSlot(null);
    this.refreshHud();
    this.maybeAdvanceStep();
  }

  private playSeatFeedback(component: Component): void {
    switch (component.category) {
      case 'ram':
        audio.play('ram-click');
        haptics.fire('light');
        break;
      case 'gpu':
        audio.play('pcie-click');
        haptics.fire('medium');
        break;
      case 'cpu':
        audio.play('cpu-click');
        haptics.fire('light');
        break;
      case 'psu':
      case 'motherboard':
        audio.play('metal');
        haptics.fire('medium');
        break;
      default:
        audio.play('plastic');
        haptics.fire('light');
    }
  }

  /** Front and bottom pull air in; rear and top push it out (§17). */
  private defaultAirflow(slot: Slot): 'intake' | 'exhaust' {
    return slot.startsWith('fan-front') || slot.startsWith('fan-bottom') ? 'intake' : 'exhaust';
  }

  private handleDropAway(held: HeldPart): void {
    // Dropping short of the slot is not a failure — the part stays in hand.
    toast('Not in place yet', 'info', `Drag it onto ${slotLabel(held.slot)}.`);
    haptics.fire('tick');
  }

  private handleScrewDriven(slot: Slot, _index: number): void {
    const part = partInSlot(game.current, slot);
    if (!part) return;
    part.screwsDriven = Math.min(part.screwsRequired, part.screwsDriven + 1);
    bus.emit('build:screw', { slot, driven: part.screwsDriven, required: part.screwsRequired });
    game.persistBuild();
    if (part.screwsDriven >= part.screwsRequired) {
      toast('Secured', 'good');
      this.maybeAdvanceStep();
    }
    this.refreshHud();
  }

  private handlePanelScrewOut(): void {
    if (this.buildScene.panelScrewsRemoved) {
      toast('REMOVE SIDE PANEL', 'info', 'All four screws are out — slide the panel away.');
    }
    this.refreshHud();
  }

  private tryOpenPanel(): void {
    if (!this.buildScene.builtCase) {
      toast('No case yet', 'warn', 'Set a case on the bench first.');
      return;
    }
    if (!this.buildScene.panelScrewsRemoved) {
      toast('Screws still in', 'warn', 'Press and hold each thumbscrew until it backs out.');
      const first = this.buildScene.panelScrews.findIndex((s) => s.progress > 0.02);
      if (first >= 0) haptics.fire('error');
      return;
    }
    this.cinematicActive = true;
    this.scene.cameraController.setLocked(true);
    audio.play('glass');
    this.buildScene.openPanel(() => {
      game.current.panelRemoved = true;
      game.current.state = recomputeState(game.current);
      game.persistBuild();
      this.cinematicActive = false;
      this.scene.cameraController.setLocked(false);
      // Move the camera inside the case (§8).
      const focus = this.buildScene.worldAnchor('mobo-tray');
      this.scene.cameraController.frame(focus, 3.6);
      toast('Time to build.', 'good');
      this.refreshHud();
      this.announceStep(currentStep(game.current));
    });
  }

  private placeStandoff(needed: number): void {
    const build = game.current;
    if (build.standoffsPlaced >= needed) return;
    this.buildScene.placeStandoff(build.standoffsPlaced, needed);
    build.standoffsPlaced += 1;
    audio.play('screw-done');
    haptics.fire('tick');
    game.persistBuild();
    this.refreshHud();
    if (build.standoffsPlaced >= needed) {
      toast('Standoffs in', 'good', 'The board can go in now.');
      this.maybeAdvanceStep();
    }
  }

  /* ---------------------------------------------------------------- */
  /* Thermal paste (§11)                                               */
  /* ---------------------------------------------------------------- */

  private applyPaste(): void {
    const build = game.current;
    const blobs = this.buildScene.pasteBlobCount;
    if (blobs >= 8) {
      toast('That is plenty', 'warn', 'Any more and it will squeeze out the sides.');
      return;
    }
    this.buildScene.addPasteBlob(
      0.5 + (Math.random() - 0.5) * 0.35,
      0.5 + (Math.random() - 0.5) * 0.35,
      0.4 + Math.random() * 0.4
    );
    audio.play('paste');
    haptics.fire('tick');

    const count = this.buildScene.pasteBlobCount;
    build.paste = count <= 1 ? 'sparse' : count <= 4 ? 'good' : 'excessive';
    bus.emit('build:paste', { quality: build.paste });
    game.persistBuild();

    if (count === 3) toast('Thermal paste: GOOD', 'good', 'That is the right amount.');
    if (count === 6) toast('Thermal paste: EXCESSIVE', 'warn', 'It will still work, just messier.');
    this.refreshHud();
  }

  private wipePaste(): void {
    this.buildScene.clearPaste();
    game.current.paste = 'none';
    game.persistBuild();
    toast('Wiped clean', 'info');
    this.refreshHud();
  }

  /* ---------------------------------------------------------------- */
  /* Cables (§15, §18)                                                 */
  /* ---------------------------------------------------------------- */

  private toggleCable(cable: CableKind): void {
    const build = game.current;
    const i = build.connectedCables.indexOf(cable);
    if (i >= 0) {
      build.connectedCables.splice(i, 1);
      build.routedCables = build.routedCables.filter((c) => c !== cable);
      this.buildScene.cables.remove(cable);
      audio.play('plastic');
    } else {
      build.connectedCables.push(cable);
      this.buildScene.connectCable(cable, build.routedCables.includes(cable));
      audio.play('cable-click');
      haptics.fire('light');
    }
    bus.emit('build:cable', { cable, connected: i < 0 });
    build.state = recomputeState(build);
    game.persistBuild();
    this.refreshHud();
    if (missingCables(build).length === 0 && requiredCables(build).length > 0) {
      this.maybeAdvanceStep();
    }
  }

  private toggleRoute(cable: CableKind): void {
    const build = game.current;
    if (!build.connectedCables.includes(cable)) {
      toast('Not connected yet', 'warn', 'Connect the cable before routing it.');
      return;
    }
    const i = build.routedCables.indexOf(cable);
    if (i >= 0) build.routedCables.splice(i, 1);
    else build.routedCables.push(cable);
    this.buildScene.setCableRouted(cable, i < 0);
    audio.play('cable-click');
    haptics.fire('tick');
    game.persistBuild();
    this.refreshHud();
  }

  private addTie(): void {
    game.current.cableTies += 1;
    audio.play('plastic');
    haptics.fire('tick');
    game.persistBuild();
    const r = cableReport(game.current);
    toast(`Cable score: ${r.score}/100`, r.score >= 85 ? 'good' : 'info', r.label);
    this.refreshHud();
  }

  private skipStep(step: BuildStep): void {
    // Optional steps are skipped by marking them satisfied for the session.
    toast(`Skipped ${step.label.split('—')[1]?.trim() ?? step.id}`, 'info');
    this.skipped.add(step.id);
    this.refreshHud();
    this.announceStep(this.nextStep());
  }

  private skipped = new Set<string>();

  private nextStep(): BuildStep {
    const build = game.current;
    return (
      BUILD_STEPS.find((s) => !s.isComplete(build) && !this.skipped.has(s.id)) ??
      BUILD_STEPS[BUILD_STEPS.length - 1]
    );
  }

  private maybeAdvanceStep(): void {
    const next = this.nextStep();
    this.refreshHud();
    this.announceStep(next);
  }

  /* ---------------------------------------------------------------- */
  /* Selection tooltip (§6)                                            */
  /* ---------------------------------------------------------------- */

  private handleSelect(info: { componentId?: string; slot?: Slot; objectName?: string }): void {
    if (!info.componentId && !info.objectName) {
      this.tooltip.classList.remove('show');
      this.buildScene.highlightSlot(null);
      return;
    }

    const comp = info.componentId ? getComponent(info.componentId) : undefined;
    let title = 'COMPONENT';
    let detail = '';

    if (comp) {
      title = comp.name.toUpperCase();
      detail = comp.blurb;
      if (info.slot) {
        const part = partInSlot(game.current, info.slot);
        if (part && part.screwsRequired > part.screwsDriven) {
          detail += `  ·  ${part.screwsRequired - part.screwsDriven} screw(s) still to fit.`;
        }
        if (part?.airflow) {
          detail += `  ·  Set to ${part.airflow.toUpperCase()} — tap again to flip.`;
          this.flipAirflow(info.slot);
        }
      }
    } else if (info.objectName === 'cpu-socket') {
      title = 'CPU SOCKET';
      detail = 'Waiting for CPU';
    } else if (info.objectName?.startsWith('ram-slot')) {
      title = 'DIMM SLOT';
      detail = 'Waiting for memory';
    } else if (info.objectName === 'pcie-slot') {
      title = 'PCIE X16';
      detail = 'Waiting for a graphics card';
    } else if (info.objectName === 'side-panel') {
      title = 'SIDE PANEL';
      detail = game.current.panelRemoved ? 'Removed' : 'Remove the four thumbscrews first';
    } else if (info.objectName === 'screwdriver') {
      title = 'SCREWDRIVER';
      detail = 'Press and hold any screw to drive it.';
    } else {
      this.tooltip.classList.remove('show');
      return;
    }

    clearNode(this.tooltip);
    this.tooltip.append(el('div', { class: 't', text: title }), el('div', { class: 'd', text: detail }));
    // Park it above the tray, out of the way of the model.
    this.tooltip.style.left = '50%';
    this.tooltip.style.transform = 'translateX(-50%)';
    this.tooltip.style.bottom = '150px';
    this.tooltip.style.top = 'auto';
    this.tooltip.classList.add('show');
    if (info.slot) this.buildScene.highlightSlot(info.slot);
  }

  /** Tapping an installed fan flips its direction (§17). */
  private flipAirflow(slot: Slot): void {
    const part = partInSlot(game.current, slot);
    if (!part?.airflow) return;
    part.airflow = part.airflow === 'intake' ? 'exhaust' : 'intake';
    const built = this.buildScene.placedAt(slot);
    if (built) built.built.group.rotation.y += Math.PI;
    audio.play('ui-tap');
    haptics.fire('tick');
    game.persistBuild();
  }

  /* ---------------------------------------------------------------- */
  /* Build sheet                                                       */
  /* ---------------------------------------------------------------- */

  private openBuildSheet(): void {
    const build = game.current;
    const power = powerBreakdown(build);
    const thermal = thermalReport(build);
    const cables = cableReport(build);
    const audit = auditBuild(build);

    const content = el('div', { class: 'screen-scroll', style: { padding: '0 0 12px' } }, [
      sectionTitle('Installed'),
      el(
        'div',
        { class: 'list' },
        build.parts.length === 0
          ? [el('div', { class: 'row' }, [el('span', { class: 'k', text: 'Nothing yet' })])]
          : build.parts.map((p) => {
              const c = getComponent(p.componentId);
              return row(c?.name ?? p.componentId, slotLabel(p.slot));
            })
      ),

      sectionTitle('Power'),
      el('div', { class: 'list' }, [
        row('CPU', `${power.cpu} W`),
        row('GPU', `${power.gpu} W`),
        row('Motherboard', `${power.motherboard} W`),
        row('Memory', `${power.memory} W`),
        row('Storage', `${power.storage} W`),
        row('Fans & cooling', `${power.fans} W`),
        row('Total', `${power.total} W`),
        row('Recommended PSU', `${power.recommended} W`),
        row('Installed PSU', power.supply ? `${power.supply} W` : '—'),
      ]),

      sectionTitle('Thermals & acoustics'),
      el('div', { class: 'list' }, [
        row('CPU', `${thermal.cpuTemp} °C`),
        row('GPU', `${thermal.gpuTemp} °C`),
        row('Case', `${thermal.caseTemp} °C`),
        row('Airflow', `${Math.round(thermal.airflow.score * 100)}%`),
      ]),

      sectionTitle('Cable management'),
      el('div', { class: 'list' }, [
        row('Score', `${cables.score}/100 — ${cables.label}`),
        ...cables.notes.map((n) => row('', el('span', { class: 'k', text: n }))),
      ]),

      sectionTitle('Cost'),
      el('div', { class: 'list' }, [row('Parts total', money(buildCost(build)))]),

      audit.issues.length > 0 ? sectionTitle('Warnings') : null,
      ...audit.issues.map((i) =>
        el('div', { class: 'row' }, [
          el('span', {
            class: `k ${i.severity === 'blocker' ? 'bad' : i.severity === 'warning' ? 'warn' : ''}`,
            text: `${i.title} — ${i.detail}`,
          }),
        ])
      ),

      el('div', { class: 'btn-row', style: { marginTop: '16px' } }, [
        button('RGB', () => openRgbPanel(this.ui, game.current, () => this.buildScene.setRgbProfile(game.current.rgb))),
        button('Diagnostics', () => this.openDiagnostics()),
      ]),
    ]);

    this.ui.openSheet(build.name, content);
  }

  /** Inspect mode for troubleshooting a build that will not boot (§23). */
  private openDiagnostics(): void {
    const post = runPost(game.current);
    const content = el('div', { class: 'screen-scroll', style: { padding: '0 0 12px' } }, [
      el('div', { class: 'list' }, post.lines.map((l) => row(l.label, el('span', {
        class: `v ${l.status === 'OK' ? 'good' : l.status === 'WARN' ? 'warn' : 'bad'}`,
        text: l.value,
      })))),
      post.diagnostic
        ? el('div', {}, [
            sectionTitle(post.diagnostic.title),
            el(
              'div',
              { class: 'list' },
              post.diagnostic.checks.map((c) => el('div', { class: 'row' }, [el('span', { class: 'k', text: `• ${c}` })]))
            ),
          ])
        : el('div', { class: 'row' }, [el('span', { class: 'k good', text: 'No faults found — this build should POST.' })]),
    ]);
    this.ui.openSheet('Diagnostics', content);
  }

  /* ---------------------------------------------------------------- */
  /* Power on (§21, §22, §24)                                          */
  /* ---------------------------------------------------------------- */

  private attemptPowerOn(): void {
    if (this.cinematicActive) return;
    const build = game.current;
    const audit = auditBuild(build);
    const blockers = audit.issues.filter((i) => i.severity === 'blocker');
    if (blockers.length > 0) {
      toast(`❌ ${blockers[0].title}`, 'error', blockers[0].detail);
      if (blockers[0].focus && !blockers[0].focus.startsWith('cable-')) {
        this.buildScene.highlightSlot(blockers[0].focus as Slot);
      }
      return;
    }

    this.commitElapsed();
    this.cinematicActive = true;
    advanceState(build, 'BUILD_COMPLETE');
    advanceState(build, 'POWERING_ON');
    game.persistBuild();

    showPowerOnSequence({
      ui: this.ui,
      scene: this.scene,
      buildScene: this.buildScene,
      build,
      onComplete: () => {
        this.cinematicActive = false;
        this.finishBuild();
      },
    });
  }

  private finishBuild(): void {
    const build = game.current;
    const post = runPost(build);

    if (!post.success) {
      // A failed boot is recoverable: the player goes back and fixes it (§23).
      advanceState(build, 'POST');
      build.state = 'CABLES_CONNECTED';
      this.buildScene.setPowered(false);
      this.scene.setMonitor(false);
      audio.stopAmbience();
      game.persistBuild();
      this.openDiagnostics();
      toast('Build did not POST', 'error', post.diagnostic?.title ?? 'Check the diagnostics.');
      return;
    }

    advanceState(build, 'BENCHMARK');
    const result = runBenchmark(build);
    game.checkBuildAchievements(build, result);

    showBuildReport({
      ui: this.ui,
      build,
      result,
      onDone: () => {
        game.saveToGarage(build, result);

        if (game.mode === 'career' && game.activeJob) {
          const job = game.activeJob;
          const outcome = game.evaluateJob(job, build, result);
          game.completeJob(job, outcome.payout, outcome.reputation);
          toast(
            outcome.passed ? `${job.title} delivered` : `${job.title} — customer unhappy`,
            outcome.passed ? 'good' : 'warn',
            `${outcome.payout >= 0 ? '+' : ''}${money(outcome.payout)}  ·  ${outcome.reputation >= 0 ? '+' : ''}${outcome.reputation} rep${
              outcome.notes.length ? `  ·  ${outcome.notes[0]}` : ''
            }`
          );
        } else if (game.mode === 'challenge' && game.activeChallengeId) {
          if (game.evaluateChallenge(build, result)) {
            game.completeChallenge(game.activeChallengeId);
          } else {
            toast('Challenge not met', 'warn', 'Have another go with different parts.');
          }
        }

        game.newBuild('New Build', game.mode === 'challenge' ? 'free' : game.mode);
        this.skipped.clear();
        this.buildScene.reset();
        save.flush();
        this.ui.show('menu');
      },
      onShowcase: () => {
        this.ui.show('showcase');
      },
    });
  }

  /** Exposed so the showcase screen can reuse the built model. */
  get sceneForShowcase(): BuildScene {
    return this.buildScene;
  }
}
