import { save, type SavedPc } from './SaveManager';
import { bus, toast } from './EventBus';
import { audio } from './AudioManager';
import { ALL_COMPONENTS, getComponent } from '../data/catalog';
import type { Component } from '../data/types';
import { DESKS, getDesk, type Desk } from '../data/desks';
import {
  ACHIEVEMENTS,
  CHALLENGES,
  CUSTOMERS,
  JOBS,
  type JobTemplate,
  reputationLevel,
} from '../data/career';
import {
  type Build,
  buildCase,
  buildCost,
  createBuild,
  partsOf,
  totalRam,
  totalStorage,
} from '../sim/Build';
import type { BenchmarkResult } from '../sim/BenchmarkManager';
import { cableReport } from '../sim/CableManager';

/**
 * Owns money, reputation, inventory, jobs and achievements, and is the single
 * place that mutates the save (§54).
 */
class GameManagerImpl {
  /** The build currently on the bench. */
  current: Build = createBuild();
  /** Which mode the current build belongs to. */
  mode: 'career' | 'free' | 'challenge' = 'free';
  activeChallengeId: string | null = null;

  init(): void {
    const data = save.load();
    if (data.currentBuild) this.current = data.currentBuild;
    this.refreshJobOffers();
  }

  /* ---------------------------------------------------------------- */
  /* Money and reputation (§25, §28)                                   */
  /* ---------------------------------------------------------------- */

  get money(): number {
    return save.get().career.money;
  }

  get reputation(): number {
    return save.get().career.reputation;
  }

  get reputationLevelIndex(): number {
    return reputationLevel(this.reputation).index;
  }

  addMoney(amount: number): void {
    save.update((d) => {
      d.career.money = Math.max(0, d.career.money + amount);
      if (amount > 0) d.stats.totalEarned += amount;
    });
    if (amount > 0) audio.play('cash');
    bus.emit('money:change', { amount, total: this.money });
  }

  addReputation(amount: number): void {
    const before = this.reputationLevelIndex;
    save.update((d) => {
      d.career.reputation = Math.max(0, d.career.reputation + amount);
    });
    bus.emit('reputation:change', { amount, total: this.reputation });
    const after = this.reputationLevelIndex;
    if (after > before) {
      const level = reputationLevel(this.reputation);
      toast(`REPUTATION: ${level.name.toUpperCase()}`, 'good', 'New parts and better jobs unlocked.');
      this.refreshJobOffers();
      if (level.index >= 5) this.unlockAchievement('master-builder');
    }
  }

  /* ---------------------------------------------------------------- */
  /* Shop and inventory (§29)                                          */
  /* ---------------------------------------------------------------- */

  /** Parts the shop will sell at the player's current reputation. */
  shopCatalog(): Component[] {
    const level = this.reputationLevelIndex;
    return ALL_COMPONENTS.filter((c) => (c.reputationRequired ?? 0) <= level);
  }

  isLocked(c: Component): boolean {
    return (c.reputationRequired ?? 0) > this.reputationLevelIndex;
  }

  /** Parts the shop shows but will not sell yet, so progress feels visible. */
  availableLocked(): Component[] {
    return ALL_COMPONENTS.filter((c) => this.isLocked(c));
  }

  owned(componentId: string): number {
    return save.get().inventory.filter((id) => id === componentId).length;
  }

  canAfford(c: Component): boolean {
    return this.money >= c.price;
  }

  buy(c: Component): boolean {
    if (this.isLocked(c)) {
      toast('Locked', 'warn', `Reach ${['Rookie', 'Apprentice', 'Builder', 'Expert', 'Master Builder', 'Legend'][c.reputationRequired ?? 0]} to unlock this part.`);
      return false;
    }
    if (!this.canAfford(c)) {
      toast('Not enough money', 'warn', `${c.name} costs $${c.price}. You have $${Math.round(this.money)}.`);
      return false;
    }
    this.addMoney(-c.price);
    save.update((d) => d.inventory.push(c.id));
    toast(`Purchased ${c.name}`, 'good');
    return true;
  }

  sell(componentId: string): void {
    const c = getComponent(componentId);
    if (!c) return;
    const idx = save.get().inventory.indexOf(componentId);
    if (idx < 0) return;
    // Parts sell back at 60%, so hoarding has a cost.
    const refund = Math.round(c.price * 0.6);
    save.update((d) => d.inventory.splice(idx, 1));
    this.addMoney(refund);
    toast(`Sold ${c.name}`, 'info', `+$${refund}`);
  }

  /** Take one unit out of inventory when it goes into the machine. */
  consumeFromInventory(componentId: string): void {
    save.update((d) => {
      const i = d.inventory.indexOf(componentId);
      if (i >= 0) d.inventory.splice(i, 1);
    });
  }

  returnToInventory(componentId: string): void {
    save.update((d) => d.inventory.push(componentId));
  }

  /** In free build the whole catalog is on tap; in career you own what you buy. */
  availableParts(): Component[] {
    if (this.mode === 'free') return this.shopCatalog();
    const ids = save.get().inventory;
    const seen = new Map<string, Component>();
    for (const id of ids) {
      const c = getComponent(id);
      if (c) seen.set(id, c);
    }
    return [...seen.values()];
  }

  /* ---------------------------------------------------------------- */
  /* Workbenches — cosmetic progression, no effect on any build.       */
  /* ---------------------------------------------------------------- */

  get deskId(): string {
    return save.get().deskId;
  }

  get desk(): Desk {
    return getDesk(this.deskId);
  }

  ownsDesk(id: string): boolean {
    return save.get().ownedDesks.includes(id);
  }

  deskLocked(desk: Desk): boolean {
    return (desk.reputationRequired ?? 0) > this.reputationLevelIndex;
  }

  deskCatalog(): Desk[] {
    return DESKS;
  }

  buyDesk(desk: Desk): boolean {
    if (this.ownsDesk(desk.id)) return true;
    if (this.deskLocked(desk)) {
      toast('Locked', 'warn', `${desk.name} unlocks at a higher reputation.`);
      return false;
    }
    if (this.money < desk.price) {
      toast('Not enough money', 'warn', `${desk.name} costs $${desk.price}.`);
      return false;
    }
    this.addMoney(-desk.price);
    save.update((d) => d.ownedDesks.push(desk.id));
    toast(`Bought ${desk.name}`, 'good');
    return true;
  }

  /** Switch the active bench. Returns false if it is not owned. */
  useDesk(id: string): boolean {
    if (!this.ownsDesk(id)) return false;
    save.update((d) => {
      d.deskId = id;
    });
    return true;
  }

  /* ---------------------------------------------------------------- */
  /* Jobs (§26)                                                        */
  /* ---------------------------------------------------------------- */

  refreshJobOffers(): void {
    const d = save.get();
    const level = this.reputationLevelIndex;
    const eligible = JOBS.filter(
      (j) => j.minReputationLevel <= level && !d.career.completedJobs.includes(j.id)
    );
    save.update((data) => {
      data.career.availableJobs = eligible.slice(0, 4).map((j) => j.id);
    });
  }

  jobById(id: string): JobTemplate | undefined {
    return JOBS.find((j) => j.id === id);
  }

  customerFor(job: JobTemplate) {
    return CUSTOMERS.find((c) => c.id === job.customerId) ?? CUSTOMERS[0];
  }

  acceptJob(jobId: string): void {
    const job = this.jobById(jobId);
    if (!job) return;
    save.update((d) => {
      d.career.activeJobId = jobId;
    });
    this.mode = 'career';
    this.current = createBuild(`${job.title} — ${this.customerFor(job).name}`);
    this.current.jobId = jobId;
    this.persistBuild();
    toast(`Job accepted: ${job.title}`, 'good', `Budget ${'$' + job.budget}`);
  }

  get activeJob(): JobTemplate | undefined {
    const id = save.get().career.activeJobId;
    return id ? this.jobById(id) : undefined;
  }

  /**
   * Score a finished build against the job brief. Returns how much of the
   * payout the customer is willing to hand over, and why.
   */
  evaluateJob(job: JobTemplate, build: Build, result: BenchmarkResult): {
    passed: boolean;
    satisfaction: number;
    payout: number;
    reputation: number;
    notes: string[];
  } {
    const req = job.requirements;
    const notes: string[] = [];
    let met = 0;
    let total = 0;

    const check = (ok: boolean, label: string): void => {
      total += 1;
      if (ok) met += 1;
      else notes.push(label);
    };

    if (req.minRamGb !== undefined) {
      check(totalRam(build) >= req.minRamGb, `Wanted ${req.minRamGb}GB RAM, got ${totalRam(build)}GB.`);
    }
    if (req.minStorageGb !== undefined) {
      check(
        totalStorage(build) >= req.minStorageGb,
        `Wanted ${req.minStorageGb}GB storage, got ${totalStorage(build)}GB.`
      );
    }
    if (req.minOverallScore !== undefined) {
      check(
        result.overall >= req.minOverallScore,
        `Wanted a ${req.minOverallScore.toLocaleString()} score, got ${result.overall.toLocaleString()}.`
      );
    }
    if (req.minFpsIn) {
      const game = result.games.find((g) => g.id === req.minFpsIn!.gameId);
      check(
        (game?.fps ?? 0) >= req.minFpsIn.fps,
        `Wanted ${req.minFpsIn.fps} FPS in ${game?.name ?? 'the test'}, got ${game?.fps ?? 0}.`
      );
    }
    if (req.maxNoiseDb !== undefined) {
      check(result.noise <= req.maxNoiseDb, `Wanted under ${req.maxNoiseDb} dB, measured ${result.noise} dB.`);
    }
    if (req.maxCpuTemp !== undefined) {
      check(result.cpuTemp <= req.maxCpuTemp, `Wanted the CPU under ${req.maxCpuTemp}°C, measured ${result.cpuTemp}°C.`);
    }
    if (req.requireRgbZones !== undefined) {
      const zones = new Set(partsOf(build).filter((c) => c.rgb).map((c) => c.category)).size;
      check(zones >= req.requireRgbZones, `Wanted ${req.requireRgbZones} lit zones, counted ${zones}.`);
    }
    if (req.maxFormFactor) {
      const pcCase = buildCase(build);
      const order = ['ITX', 'mATX', 'ATX', 'EATX'];
      const ok = !!pcCase && order.indexOf(pcCase.supportedFormFactors.at(-1) ?? 'EATX') <= order.indexOf(req.maxFormFactor);
      check(ok, `Wanted a ${req.maxFormFactor} build.`);
    }
    if (req.minCableScore !== undefined) {
      const cables = cableReport(build).score;
      check(cables >= req.minCableScore, `Wanted tidier cabling — scored ${cables}/100.`);
    }

    const cost = buildCost(build);
    const overBudget = cost > job.budget;
    if (overBudget) notes.push(`Over budget by $${Math.round(cost - job.budget)}.`);

    const requirementScore = total === 0 ? 1 : met / total;
    const customer = this.customerFor(job);
    // Weight the customer's own priorities on top of the hard requirements.
    const p = customer.priorities;
    const weights = p.performance + p.quiet + p.looks + p.thermals + p.budget;
    const rgbZones = new Set(partsOf(build).filter((c) => c.rgb).map((c) => c.category)).size;
    const preference =
      (p.performance * clamp01(result.overall / 26000) +
        p.quiet * clamp01((46 - result.noise) / 22) +
        p.looks * clamp01(rgbZones / 5) +
        p.thermals * (result.thermalScore / 100) +
        p.budget * clamp01((job.budget - cost) / Math.max(1, job.budget))) /
      Math.max(0.001, weights);

    const satisfaction = clamp01(requirementScore * 0.68 + preference * 0.32) * (overBudget ? 0.72 : 1);
    const passed = requirementScore >= 0.999 && !overBudget;

    const multiplier = reputationLevel(this.reputation).payMultiplier;
    const payout = Math.round(job.reward * multiplier * (passed ? 1 : satisfaction * 0.55));
    const reputation = Math.round(
      passed ? job.reputationReward * (0.7 + satisfaction * 0.6) : -Math.round((1 - satisfaction) * 12)
    );

    return { passed, satisfaction, payout, reputation, notes };
  }

  completeJob(job: JobTemplate, payout: number, repChange: number): void {
    save.update((d) => {
      d.career.completedJobs.push(job.id);
      d.career.activeJobId = null;
      d.career.day += 1;
    });
    this.addMoney(payout);
    this.addReputation(repChange);
    this.refreshJobOffers();
  }

  /* ---------------------------------------------------------------- */
  /* Garage (§34)                                                      */
  /* ---------------------------------------------------------------- */

  saveToGarage(build: Build, result: BenchmarkResult): SavedPc {
    const pc: SavedPc = {
      id: `pc-${Date.now().toString(36)}`,
      name: build.name,
      build: JSON.parse(JSON.stringify(build)) as Build,
      result,
      cost: buildCost(build),
      builtAt: Date.now(),
    };
    save.update((d) => {
      d.savedPcs.unshift(pc);
      d.stats.buildsCompleted += 1;
      d.stats.bestScore = Math.max(d.stats.bestScore, result.overall);
      if (build.elapsedMs > 0) {
        d.stats.fastestBuildMs =
          d.stats.fastestBuildMs === null ? build.elapsedMs : Math.min(d.stats.fastestBuildMs, build.elapsedMs);
      }
    });
    return pc;
  }

  garage(): SavedPc[] {
    return save.get().savedPcs;
  }

  renamePc(id: string, name: string): void {
    save.update((d) => {
      const pc = d.savedPcs.find((p) => p.id === id);
      if (pc) {
        pc.name = name;
        pc.build.name = name;
      }
    });
  }

  deletePc(id: string): void {
    save.update((d) => {
      d.savedPcs = d.savedPcs.filter((p) => p.id !== id);
    });
  }

  duplicatePc(id: string): void {
    save.update((d) => {
      const pc = d.savedPcs.find((p) => p.id === id);
      if (!pc) return;
      const copy: SavedPc = JSON.parse(JSON.stringify(pc));
      copy.id = `pc-${Date.now().toString(36)}`;
      copy.name = `${pc.name} (copy)`;
      copy.builtAt = Date.now();
      d.savedPcs.unshift(copy);
    });
  }

  /* ---------------------------------------------------------------- */
  /* Challenges (§35) and achievements (§47)                           */
  /* ---------------------------------------------------------------- */

  startChallenge(id: string): void {
    const ch = CHALLENGES.find((c) => c.id === id);
    if (!ch) return;
    this.mode = 'challenge';
    this.activeChallengeId = id;
    this.current = createBuild(ch.title);
    this.persistBuild();
  }

  /** Did the finished build satisfy the active challenge's rule? */
  evaluateChallenge(build: Build, result: BenchmarkResult): boolean {
    const pcCase = buildCase(build);
    const cost = buildCost(build);
    switch (this.activeChallengeId) {
      case 'ch-500':
        return cost < 500;
      case 'ch-tiny':
        return !!pcCase && pcCase.supportedFormFactors.every((f) => f === 'ITX') && result.overall >= 10000;
      case 'ch-rgb':
        return partsOf(build).filter((c) => c.rgb).length >= 5;
      case 'ch-silent':
        return result.noise < 28 && result.cpuTemp < 70;
      case 'ch-nomistakes':
        return build.mistakes === 0;
      case 'ch-speed':
        return build.elapsedMs > 0 && build.elapsedMs < 5 * 60 * 1000;
      default:
        return false;
    }
  }

  completeChallenge(id: string): void {
    const ch = CHALLENGES.find((c) => c.id === id);
    if (!ch) return;
    if (save.get().career.completedChallenges.includes(id)) return;
    save.update((d) => d.career.completedChallenges.push(id));
    this.addMoney(ch.reward);
    this.addReputation(ch.reputationReward);
    toast(`CHALLENGE COMPLETE: ${ch.title}`, 'good', `+$${ch.reward}, +${ch.reputationReward} reputation`);
  }

  hasAchievement(id: string): boolean {
    return save.get().achievements.includes(id);
  }

  unlockAchievement(id: string): void {
    if (this.hasAchievement(id)) return;
    const a = ACHIEVEMENTS.find((x) => x.id === id);
    if (!a) return;
    save.update((d) => d.achievements.push(id));
    toast(`${a.icon}  ACHIEVEMENT: ${a.title}`, 'good', a.description);
  }

  /** Check every achievement whose condition depends on a finished build. */
  checkBuildAchievements(build: Build, result: BenchmarkResult): void {
    this.unlockAchievement('first-boot');
    if (result.cableScore >= 100) this.unlockAchievement('cable-master');
    if (result.grade === 'S+') this.unlockAchievement('overkill');
    if (result.noise < 28) this.unlockAchievement('silent-night');
    if (build.mistakes === 0) this.unlockAchievement('no-mistakes');
    if (build.elapsedMs > 0 && build.elapsedMs < 5 * 60 * 1000) this.unlockAchievement('speed-builder');
    if (buildCost(build) < 500 && result.overall >= 9000) this.unlockAchievement('budget-king');

    const rgbCapable = partsOf(build).filter((c) => c.category !== 'cpu' && c.category !== 'storage');
    if (rgbCapable.length >= 5 && rgbCapable.every((c) => c.rgb)) this.unlockAchievement('rgb-overload');

    const pcCase = buildCase(build);
    if (pcCase) {
      const mounted = build.parts.filter((p) => p.slot.startsWith('fan-')).length;
      const capacity =
        pcCase.fanMounts.front.count +
        pcCase.fanMounts.rear.count +
        pcCase.fanMounts.top.count +
        pcCase.fanMounts.bottom.count;
      if (capacity > 0 && mounted >= capacity) this.unlockAchievement('full-house');
    }
  }

  /* ---------------------------------------------------------------- */
  /* Build persistence                                                 */
  /* ---------------------------------------------------------------- */

  persistBuild(): void {
    save.update((d) => {
      d.currentBuild = this.current;
    });
  }

  newBuild(name: string, mode: 'career' | 'free' | 'challenge' = 'free'): void {
    this.mode = mode;
    this.current = createBuild(name);
    this.persistBuild();
  }

  get hasBuildInProgress(): boolean {
    return this.current.parts.length > 0;
  }
}

const clamp01 = (v: number): number => Math.max(0, Math.min(1, v));

export const game = new GameManagerImpl();
