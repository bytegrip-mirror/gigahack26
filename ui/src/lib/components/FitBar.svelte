<script lang="ts">
	let { probability, tone = 'fair' }: { probability: number; tone?: string } = $props();

	const filled = $derived(`${Math.max(0, Math.min(1, probability)) * 100}%`);
</script>

<span class="fit-bar" aria-hidden="true">
	<span class="filled" data-tone={tone} style:width={filled}></span>
</span>

<style>
	.fit-bar {
		display: block;
		width: 100%;
		height: 4px;
		background: var(--color-ink-4);
		border-radius: 1px;
		overflow: hidden;
	}

	.filled {
		display: block;
		height: 100%;
		background: var(--color-orange-line);
	}

	.filled[data-tone='strong'] {
		background: var(--color-orange);
	}

	.filled[data-tone='weak'] {
		background: var(--color-ink-3);
	}
</style>
