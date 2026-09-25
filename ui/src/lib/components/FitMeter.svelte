<script lang="ts">
	import type { Band } from '$lib/api';

	let {
		probability,
		band,
		baseRate,
		thresholds,
		compact = false
	}: {
		probability: number;
		band: Band;
		baseRate: number;
		thresholds: { watch: number; nurture: number; priority: number };
		compact?: boolean;
	} = $props();

	const position = $derived(Math.max(0, Math.min(1, probability)) * 100);
	const basePosition = $derived(baseRate * 100);
</script>

<div class="meter" class:compact title="Posterior probability the company is a qualified lead for this service line">
	<div class="track">
		<div class="zone zone-watch" style="width: {(thresholds.nurture - thresholds.watch) * 100}%"></div>
		<div
			class="zone zone-nurture"
			style="left: {thresholds.nurture * 100}%; width: {(thresholds.priority - thresholds.nurture) * 100}%"
		></div>
		<div class="zone zone-priority" style="left: {thresholds.priority * 100}%; right: 0"></div>
		<div class="base" style="left: {basePosition}%"></div>
		<div class="fill fill-{band}" style="width: {position}%"></div>
	</div>
	{#if !compact}
		<div class="ticks mono tiny">
			<span style="left: {basePosition}%" class="base-label">base {baseRate.toFixed(2)}</span>
			<span style="left: {thresholds.nurture * 100}%" class="tick-label">
				{(thresholds.nurture * 100).toFixed(0)}
			</span>
			<span style="left: {thresholds.priority * 100}%" class="tick-label">
				{(thresholds.priority * 100).toFixed(0)}
			</span>
		</div>
	{/if}
</div>

<style>
	.meter {
		width: 100%;
	}

	.track {
		position: relative;
		height: 8px;
		border-radius: 999px;
		background: var(--surface-sunken);
		border: 1px solid var(--line);
		overflow: hidden;
	}

	.compact .track {
		height: 6px;
	}

	.zone {
		position: absolute;
		top: 0;
		bottom: 0;
	}

	.zone-watch {
		left: 30%;
		background: rgb(0 0 0 / 0.025);
	}

	.zone-nurture {
		background: var(--orange-050);
	}

	.zone-priority {
		background: var(--orange-100);
	}

	.base {
		position: absolute;
		top: -2px;
		bottom: -2px;
		width: 1px;
		background: var(--ink-4);
	}

	.fill {
		position: absolute;
		left: 0;
		top: 0;
		bottom: 0;
		border-radius: 999px 0 0 999px;
		transition: width 220ms ease;
	}

	.fill-PRIORITY {
		background: var(--orange);
	}

	.fill-NURTURE {
		background: var(--orange-200);
	}

	.fill-WATCH {
		background: var(--line-strong);
	}

	.fill-THIN {
		background: var(--line-strong);
	}

	.fill-DISQUALIFIED {
		background: var(--danger-line);
	}

	.ticks {
		position: relative;
		height: 14px;
		margin-top: 3px;
		color: var(--ink-4);
	}

	.ticks span {
		position: absolute;
		transform: translateX(-50%);
		white-space: nowrap;
	}

	.base-label {
		color: var(--ink-3);
	}
</style>
