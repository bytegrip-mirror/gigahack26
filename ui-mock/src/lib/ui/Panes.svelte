<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		children,
		scroll = false,
		class: layout = ''
	}: { children: Snippet; scroll?: boolean; class?: string } = $props();
</script>

<div class={['grid min-h-0 flex-1 grid-cols-1', scroll && 'scroll', layout]}>{@render children()}</div>
