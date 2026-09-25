<script lang="ts">
	import { page } from '$app/state';
	import BoardView from '$lib/views/BoardView.svelte';

	const id = $derived(page.params.id ?? null);
</script>

<svelte:head><title>Company · Signal Desk</title></svelte:head>

<BoardView initialSelected={id} />
