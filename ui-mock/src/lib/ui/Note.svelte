<script lang="ts">
	import type { Snippet } from 'svelte';
	import Icon from './Icon.svelte';
	import type { IconName } from './icons';

	let {
		tone = 'default',
		icon,
		children
	}: { tone?: 'default' | 'warn'; icon?: IconName; children: Snippet } = $props();
</script>

<div class={['flex items-center gap-2 px-4 py-3 text-md', tone === 'warn' ? 'text-oxblood' : 'text-ink-3']}>
	{#if icon}<Icon name={icon} size="md" />{/if}
	{@render children()}
</div>
