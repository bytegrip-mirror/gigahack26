<script lang="ts">
	import type { Snippet } from 'svelte';

	let { children }: { children: Snippet } = $props();
</script>

<div class="flex max-w-prose flex-col items-start gap-2 px-4 py-5 text-md text-ink-3">{@render children()}</div>
