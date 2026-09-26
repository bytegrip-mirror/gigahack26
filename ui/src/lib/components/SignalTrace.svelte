<script lang="ts">
	let { spark, title = '' }: { spark: number[]; title?: string } = $props();

	const peak = $derived(Math.max(...spark, 0));
	const height = 18;

	function bar(value: number): number {
		if (peak <= 0 || value <= 0) return 1;
		return Math.max(2, Math.round((value / peak) * height));
	}
</script>

<span class="trace" {title} aria-hidden="true">
	{#each spark as value, index (index)}
		<span class="bar" class:recent={index >= spark.length - 3} data-empty={value <= 0} style:height="{bar(value)}px"
		></span>
	{/each}
</span>

<style>
	.trace {
		display: flex;
		align-items: flex-end;
		gap: 3px;
		height: 18px;
	}

	.bar {
		width: 4px;
		background: var(--color-ink-4);
		border-radius: 1px 1px 0 0;
	}

	.bar.recent {
		background: var(--color-ink-2);
	}

	.bar[data-empty='true'] {
		background: var(--color-rule);
	}
</style>
