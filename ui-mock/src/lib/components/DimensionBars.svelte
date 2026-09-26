<script lang="ts">
	import { DIMENSIONS, DIMENSION_LABEL } from '$lib/mock/engine';
	import type { Dimension } from '$lib/mock/types';
	import LevelBar from './LevelBar.svelte';

	let { levels, thresholds }: { levels: Record<Dimension, number>; thresholds: Record<Dimension, number> } = $props();
</script>

<div class="grid grid-cols-5 gap-2">
	{#each DIMENSIONS as dimension (dimension)}
		{@const value = levels[dimension]}
		{@const lit = thresholds[dimension]}
		<div class="flex min-w-0 flex-col gap-0.5">
			<span class="flex items-baseline justify-end gap-1" title={DIMENSION_LABEL[dimension]}>
				<span class={['num text-micro', value < 0 ? 'text-oxblood' : value >= lit ? 'font-semibold text-ink' : 'text-ink-3']}>{value.toFixed(1)}</span>
			</span>
			<LevelBar {value} threshold={lit} />
		</div>
	{/each}
</div>
