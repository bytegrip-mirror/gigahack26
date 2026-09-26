<script lang="ts">
	import type { Snippet } from 'svelte';

	let { children, label = 'Filters' }: { children: Snippet; label?: string } = $props();
</script>

<div
	role="toolbar"
	aria-label={label}
	class="flex min-h-bar flex-none flex-wrap items-center gap-x-2 gap-y-1 border-b border-rule bg-surface px-4 py-2"
>
	{@render children()}
</div>
