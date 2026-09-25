<script lang="ts">
	let {
		value,
		label,
		hint = '',
		tone = null,
		title = ''
	}: {
		value: string | number;
		label: string;
		hint?: string;
		tone?: 'hot' | 'good' | 'bad' | null;
		title?: string;
	} = $props();
</script>

<div class="metric" {title}>
	<span class="metric-val num" data-tone={tone ?? undefined}>{value}</span>
	<span class="metric-lab">{label}</span>
	{#if hint}<span class="metric-hint num">{hint}</span>{/if}
</div>
