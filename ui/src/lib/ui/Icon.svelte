<script lang="ts">
	import { PATHS, type IconName } from './icons';

	export type IconSize = 'sm' | 'md' | 'lg' | 'xl';

	const PIXELS: Record<IconSize, number> = { sm: 13, md: 15, lg: 17, xl: 22 };

	let {
		name,
		size = 'md',
		title = '',
		filled = false
	}: { name: IconName; size?: IconSize; title?: string; filled?: boolean } = $props();

	const paths = $derived(PATHS[name]);
</script>

<svg
	class="block flex-none"
	width={PIXELS[size]}
	height={PIXELS[size]}
	viewBox="0 0 24 24"
	fill={filled ? 'currentColor' : 'none'}
	stroke="currentColor"
	stroke-width="1.7"
	stroke-linecap="round"
	stroke-linejoin="round"
	role={title ? 'img' : 'presentation'}
	aria-hidden={title ? undefined : 'true'}
	focusable="false"
>
	{#if title}<title>{title}</title>{/if}
	{#each paths as d (d)}
		<path {d} />
	{/each}
</svg>
