<script lang="ts">
	let {
		value,
		label,
		hint = '',
		tone = null,
		title,
		compact = false
	}: {
		value: string | number;
		label: string;
		hint?: string;
		tone?: 'hot' | 'good' | 'bad' | null;
		title?: string;
		compact?: boolean;
	} = $props();
</script>

<div
	{title}
	class={[
		'flex flex-col gap-px',
		compact ? 'min-w-0' : 'min-w-topbar border-l border-rule px-4 first:border-l-0 first:pl-0'
	]}
>
	<span
		class={[
			'num leading-tight font-medium tracking-tight',
			compact ? 'text-lg' : 'text-xl',
			tone === 'hot' && 'text-orange-ink',
			tone === 'good' && 'text-accent',
			tone === 'bad' && 'text-oxblood'
		]}>{value}</span
	>
	<span class="type-label whitespace-nowrap">{label}</span>
	{#if hint}<span class="num text-micro text-ink-4">{hint}</span>{/if}
</div>
