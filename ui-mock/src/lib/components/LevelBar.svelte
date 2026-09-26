<script lang="ts">
	let {
		value,
		was = null,
		threshold = null,
		size = 'sm'
	}: { value: number; was?: number | null; threshold?: number | null; size?: 'sm' | 'lg' } = $props();

	const MAX = 3;

	function width(amount: number): number {
		return Math.min(100, (Math.abs(amount) / MAX) * 100);
	}
</script>

<span class={['relative block bg-sunken', size === 'lg' ? 'h-2' : 'h-1']}>
	{#if was !== null}
		<span class="absolute inset-y-0 left-0 block bg-rule-strong" style:width={`${width(was)}%`}></span>
	{/if}
	<span
		class={[
			'absolute left-0 block',
			was !== null ? 'inset-y-0.5' : 'inset-y-0',
			value < 0 ? 'bg-oxblood' : threshold !== null && value >= threshold ? 'bg-orange' : threshold !== null ? 'bg-ink-3' : 'bg-ink'
		]}
		style:width={`${width(value)}%`}
	></span>
	{#if was !== null}
		<span class="absolute -inset-y-1 block w-0.5 bg-ink-3" style:left={`${width(was)}%`}></span>
	{/if}
	{#if threshold !== null}
		<span class="absolute -inset-y-0.5 block w-px bg-ink" style:left={`${(threshold / MAX) * 100}%`}></span>
	{/if}
</span>
