<script lang="ts">
	import { page } from '$app/state';
	import BoardView from '$lib/views/BoardView.svelte';

	const selected = $derived(page.url.searchParams.get('co'));
	const line = $derived(page.url.searchParams.get('line'));
</script>

<svelte:head><title>Board · Signal Desk</title></svelte:head>

{#key selected}
	<BoardView initialSelected={selected} initialLine={line} />
{/key}
