<script lang="ts">
	import { page } from '$app/state';
	import BoardView from '$lib/views/BoardView.svelte';

	const selected = $derived(page.url.searchParams.get('co'));
	const line = $derived(page.url.searchParams.get('line'));
</script>

<svelte:head><title>Companies · Nudge</title></svelte:head>

<BoardView initialSelected={selected} initialLine={line} />
