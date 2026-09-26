<script lang="ts">
	import type { HTMLInputAttributes } from 'svelte/elements';
	import Icon from './Icon.svelte';
	import type { IconName } from './icons';

	type Props = {
		value?: string | number | null;
		size?: 'md' | 'sm';
		variant?: 'default' | 'display';
		icon?: IconName;
		ref?: HTMLInputElement | null;
		class?: string;
	} & Omit<HTMLInputAttributes, 'class' | 'size' | 'value'>;

	let {
		value = $bindable(),
		size = 'md',
		variant = 'default',
		icon,
		ref = $bindable(null),
		class: layout = '',
		...rest
	}: Props = $props();
</script>

<div
	class={[
		'flex w-full items-center gap-1 rounded-sm border-2 border-rule-strong bg-surface px-2 text-ink-3 transition-colors',
		'focus-within:border-ink has-disabled:opacity-40',
		size === 'md' ? 'h-ctl' : 'h-ctl-sm',
		layout
	]}
>
	{#if icon}<Icon name={icon} size="sm" />{/if}
	<input
		bind:this={ref}
		bind:value
		{...rest}
		class={[
			'h-full min-w-0 flex-1 bg-transparent text-ink outline-none placeholder:text-ink-4',
			variant === 'display' ? 'font-display text-lg font-bold' : 'font-ui font-bold',
			variant === 'default' && (size === 'md' ? 'text-md' : 'text-base')
		]}
	/>
</div>
