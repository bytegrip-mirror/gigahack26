<script lang="ts">
	import { page } from '$app/state';
	import BoardView from '$lib/views/BoardView.svelte';

	const selected = $derived(page.url.searchParams.get('co'));
</script>

<svelte:head><title>Board · Signal Desk</title></svelte:head>

{#key selected}
	<BoardView initialSelected={selected} />
{/key}
