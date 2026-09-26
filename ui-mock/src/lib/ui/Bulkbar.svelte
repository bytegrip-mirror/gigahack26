<script lang="ts">
	import type { Snippet } from 'svelte';
	import IconButton from './IconButton.svelte';

	let {
		count,
		onclear,
		children
	}: { count: number; onclear: () => void; children: Snippet } = $props();
</script>

<div class="flex min-h-bar flex-none items-center gap-2 border-b border-ink bg-ink px-4 py-2 text-paper">
	<span class="text-md font-bold"><span class="num text-orange">{count}</span> selected</span>
	{@render children()}
	<IconButton class="ml-auto" icon="close" label="Clear the selection" tone="inverse" onclick={onclear} />
</div>
