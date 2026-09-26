<script lang="ts">
	import { PATHS, type IconName } from './icons';

	let {
		name,
		size = 15,
		title = '',
		width = 1.7,
		filled = false
	}: { name: IconName; size?: number; title?: string; width?: number; filled?: boolean } = $props();

	const paths = $derived(PATHS[name]);
</script>

<svg
	class="icon"
	width={size}
	height={size}
	viewBox="0 0 24 24"
	fill={filled ? 'currentColor' : 'none'}
	stroke="currentColor"
	stroke-width={width}
	stroke-linecap="round"
	stroke-linejoin="round"
	role={title ? 'img' : 'presentation'}
	aria-hidden={title ? undefined : 'true'}
	focusable="false"
>
	{#if title}<title>{title}</title>{/if}
	{#each paths as d (d)}
		<path d={d} />
	{/each}
</svg>
