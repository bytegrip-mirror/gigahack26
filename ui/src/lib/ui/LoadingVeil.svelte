<script lang="ts">
	import Spinner from './Spinner.svelte';

	let { show = false, label = 'Loading' }: { show?: boolean; label?: string } = $props();
</script>

{#if show}
	<div
		role="status"
		aria-live="polite"
		class="absolute inset-0 z-raised flex items-center justify-center gap-2 bg-veil text-base text-ink-3"
	>
		<Spinner />
		<span>{label}</span>
	</div>
{/if}
