<script lang="ts">
	let {
		probability,
		band,
		baseRate,
		thresholds,
		showScale = false
	}: {
		probability: number;
		band: string;
		baseRate: number;
		thresholds: { watch: number; nurture: number; priority: number };
		showScale?: boolean;
	} = $props();

	const pct = (value: number) => `${Math.max(0, Math.min(1, value)) * 100}%`;
</script>

<div class="meter">
	<div class="track">
		<span class="zone" style="left:{pct(thresholds.nurture)};right:{pct(1 - thresholds.priority)}"></span>
		<span class="base" style="left:{pct(baseRate)}"></span>
		<span class="fill" data-band={band} style="width:{pct(probability)}"></span>
	</div>
	{#if showScale}
		<div class="scale">
			<span class="label" style="left:{pct(baseRate)}">base {baseRate.toFixed(2)}</span>
			<span class="label" style="left:{pct(thresholds.priority)}">
				{thresholds.priority.toFixed(2)}
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
		height: 5px;
		background: var(--sunken);
		border-top: 1px solid var(--rule);
		border-bottom: 1px solid var(--rule);
	}

	.zone {
		position: absolute;
		top: 0;
		bottom: 0;
		background: var(--orange-wash);
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
		background: var(--ink-4);
		transition: width 180ms var(--ease);
	}

	.fill[data-band='PRIORITY'] {
		background: var(--orange);
	}

	.fill[data-band='NURTURE'] {
		background: var(--orange-line);
	}

	.fill[data-band='DISQUALIFIED'] {
		background: var(--oxblood-line);
	}

	.scale {
		position: relative;
		height: 13px;
		margin-top: 2px;
	}

	.scale .label {
		position: absolute;
		transform: translateX(-50%);
		font-size: 9.5px;
		letter-spacing: 0.06em;
		color: var(--ink-4);
		white-space: nowrap;
	}
</style>
