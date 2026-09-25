<script lang="ts">
	import type { Chip } from '$lib/api';
	import { odds, theme, tier, host } from '$lib/format';

	let {
		chip,
		onopen,
		dense = false
	}: { chip: Chip; onopen?: (chip: Chip) => void; dense?: boolean } = $props();

	const negative = $derived(chip.contribution < 0);
</script>

{#snippet body()}
	<span class="tier">{tier(chip.tier)}</span>
	<span class="theme">{theme(chip.theme)}</span>
	<span class="amount mono">{odds(chip.contribution)}</span>
	{#if !dense && chip.quote}<span class="quote">“{chip.quote}”</span>{/if}
	{#if !dense}<span class="source tiny">{host(chip.sourceUrl)}</span>{/if}
{/snippet}

{#if onopen}
	<button class="signal" class:dense class:negative title={chip.question} onclick={() => onopen(chip)}>
		{@render body()}
	</button>
{:else}
	<div class="signal" class:dense class:negative title={chip.question}>
		{@render body()}
	</div>
{/if}

<style>
	.signal {
		display: flex;
		align-items: baseline;
		gap: 8px;
		width: 100%;
		text-align: left;
		border: 1px solid var(--orange-200);
		background: var(--orange-050);
		border-radius: var(--radius-sm);
		padding: 6px 9px;
		font-size: 12.5px;
		line-height: 1.45;
		color: var(--ink-2);
	}

	button.signal:hover {
		border-color: var(--orange);
		background: var(--orange-100);
	}

	.signal.negative {
		border-color: var(--danger-line);
		background: var(--danger-bg);
	}

	.signal.dense {
		width: auto;
		padding: 3px 8px;
		gap: 6px;
	}

	.tier {
		font-weight: 650;
		font-size: 11px;
		text-transform: uppercase;
		letter-spacing: 0.05em;
		color: var(--orange-ink);
		flex: none;
	}

	.negative .tier {
		color: var(--danger);
	}

	.theme {
		font-weight: 550;
		flex: none;
	}

	.amount {
		flex: none;
		color: var(--ink-3);
	}

	.quote {
		flex: 1;
		min-width: 0;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
		font-style: italic;
		color: var(--ink-2);
	}

	.source {
		flex: none;
		color: var(--ink-4);
	}
</style>
