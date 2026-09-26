<script lang="ts">
	let { show = false, label = 'Loading' }: { show?: boolean; label?: string } = $props();
</script>

{#if show}
	<div class="veil" role="status" aria-live="polite">
		<span class="spinner"></span>
		<span class="veil-label">{label}</span>
	</div>
{/if}

<style>
	.veil {
		position: absolute;
		inset: 0;
		z-index: 5;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--sp-2);
		background: color-mix(in oklab, var(--paper) 78%, transparent);
		backdrop-filter: blur(1px);
		color: var(--ink-3);
		font-size: 13px;
	}

	.veil-label {
		font-size: var(--fs-base);
	}
</style>
