<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title,
		subtitle,
		metrics,
		tools
	}: { title: string; subtitle?: Snippet; metrics?: Snippet; tools?: Snippet } = $props();
</script>

<header class="flex min-h-topbar flex-none flex-wrap items-center justify-between gap-x-5 gap-y-3 border-b border-ink bg-surface px-4 py-3">
	<div class="min-w-0">
		<h1 class="type-display text-title leading-tight">{title}</h1>
		{#if subtitle}<p class="num mt-0.5 type-sub">{@render subtitle()}</p>{/if}
	</div>
	{#if metrics}<div class="flex flex-wrap items-stretch">{@render metrics()}</div>{/if}
	{#if tools}<div class="flex flex-none items-center gap-2">{@render tools()}</div>{/if}
</header>
