<script lang="ts">
	import Button from '$lib/ui/Button.svelte';
	import SignalEditor from './SignalEditor.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import type { Dimension, Direction, Fade, Rubric, Trigger } from '$lib/mock/types';

	type Signal = Trigger & { rubric: Rubric; fade: Fade };

	let {
		list,
		direction,
		dimension,
		newDimension = 'NEED',
		title,
		empty,
		hits,
		lockDirection = false,
		dimensionLabel
	}: {
		list: Trigger[];
		direction: Direction;
		dimension?: Dimension;
		newDimension?: Dimension;
		title: string;
		empty: string;
		hits?: Map<string, number>;
		lockDirection?: boolean;
		dimensionLabel: Record<Dimension, string>;
	} = $props();

	let open = $state<string | null>(null);

	const shown = $derived(
		list.filter((signal) => signal.direction === direction && (!dimension || signal.dimension === dimension)) as Signal[]
	);

	function add() {
		open = icpStore.addSignal(list, dimension ?? newDimension, direction);
	}

	function remove(id: string) {
		const index = list.findIndex((signal) => signal.id === id);
		if (index >= 0) list.splice(index, 1);
	}
</script>

<section class="flex flex-col" aria-label={title}>
	<div class="flex min-h-ctl-sm items-center justify-between gap-2 pb-1">
		<h4 class="type-label">{title} <span class="num">{shown.length}</span></h4>
		<Button size="sm" icon="plus" onclick={add}>Add</Button>
	</div>
	{#each shown as signal (signal.id)}
		<SignalEditor
			{signal}
			{lockDirection}
			{dimensionLabel}
			showDimension={!dimension}
			hits={hits ? (hits.get(signal.id) ?? 0) : undefined}
			open={open === signal.id}
			ontoggle={() => (open = open === signal.id ? null : signal.id)}
			onremove={() => remove(signal.id)}
		/>
	{:else}
		<p class="border-t border-rule py-2 type-sub">{empty}</p>
	{/each}
</section>
