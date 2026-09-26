<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		label,
		hint,
		for: target,
		children,
		class: layout = ''
	}: { label: string; hint?: string | Snippet; for?: string; children: Snippet; class?: string } = $props();
</script>

<div class={['flex flex-col gap-1', layout]}>
	<label for={target} class="type-label">{label}</label>
	{@render children()}
	{#if typeof hint === 'function'}
		<p class="text-sm leading-normal text-ink-4">{@render hint()}</p>
	{:else if hint}
		<p class="text-sm leading-normal text-ink-4">{hint}</p>
	{/if}
</div>
