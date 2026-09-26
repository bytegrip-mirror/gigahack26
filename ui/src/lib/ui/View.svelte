<script lang="ts">
	import type { Snippet } from 'svelte';

	let { children, class: layout = '' }: { children: Snippet; class?: string } = $props();
</script>

<div class={['flex h-full min-h-0 flex-col', layout]}>{@render children()}</div>
