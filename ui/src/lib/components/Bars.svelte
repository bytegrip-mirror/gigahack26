<script lang="ts">
	let {
		values,
		labels = [],
		highlight = -1,
		title = ''
	}: { values: number[]; labels?: string[]; highlight?: number; title?: string } = $props();

	const peak = $derived(Math.max(1, ...values));
	const lit = $derived(highlight < 0 ? values.length - 1 : highlight);
</script>

<div class="bars" {title}>
	{#each values as value, index (index)}
		<div class="col">
			<span class="track">
				<span
					class="bar"
					class:lit={index === lit}
					style:height={value > 0 ? `max(2px, ${(value / peak) * 100}%)` : '0'}
				></span>
			</span>
			{#if labels[index]}<span class="tick">{labels[index]}</span>{/if}
		</div>
	{/each}
</div>

<style>
	.bars {
		display: flex;
		align-items: stretch;
		gap: var(--spacing-1);
		height: 100%;
		min-height: 64px;
	}

	.col {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: var(--spacing-1);
	}

	.track {
		flex: 1;
		display: flex;
		align-items: flex-end;
	}

	.bar {
		display: block;
		width: 100%;
		background: color-mix(in srgb, var(--color-orange) 28%, var(--color-surface));
	}

	.bar.lit {
		background: var(--color-orange);
	}

	.tick {
		font-size: var(--text-micro);
		color: var(--color-ink-3);
		text-align: center;
		white-space: nowrap;
		overflow: hidden;
	}
</style>
