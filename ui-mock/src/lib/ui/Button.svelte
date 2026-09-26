<script lang="ts">
	import type { HTMLButtonAttributes } from 'svelte/elements';
	import type { Snippet } from 'svelte';
	import Icon from './Icon.svelte';
	import type { IconName } from './icons';

	type Props = {
		variant?: 'default' | 'primary' | 'ink' | 'quiet' | 'inverse';
		size?: 'md' | 'sm';
		icon?: IconName;
		trailing?: IconName;
		href?: string;
		target?: string;
		rel?: string;
		class?: string;
		children?: Snippet;
	} & Omit<HTMLButtonAttributes, 'class' | 'children'>;

	let {
		variant = 'default',
		size = 'md',
		icon,
		trailing,
		href,
		type = 'button',
		class: layout = '',
		children,
		...rest
	}: Props = $props();
</script>

<svelte:element
	this={href ? 'a' : 'button'}
	role={href ? 'link' : undefined}
	{href}
	type={href ? undefined : type}
	{...rest}
	class={[
		'inline-flex items-center justify-center gap-1 rounded-sm border-2 font-ui font-bold whitespace-nowrap transition-colors',
		'disabled:cursor-not-allowed disabled:opacity-40 aria-disabled:cursor-not-allowed aria-disabled:opacity-40 hover:no-underline',
		size === 'md' && 'h-ctl px-3 text-base',
		size === 'sm' && 'h-ctl-sm px-2 text-sm',
		variant === 'default' && 'border-ink bg-surface text-ink enabled:hover:bg-ink enabled:hover:text-surface',
		variant === 'primary' && 'border-orange bg-orange text-on-orange enabled:hover:border-ink enabled:hover:bg-ink enabled:hover:text-surface',
		variant === 'ink' && 'border-ink bg-ink text-paper enabled:hover:border-ink-2 enabled:hover:bg-ink-2',
		variant === 'quiet' && 'border-transparent bg-transparent text-ink-2 enabled:hover:bg-sunken',
		variant === 'inverse' && 'border-ink-line bg-transparent text-paper enabled:hover:border-ink-4 enabled:hover:bg-ink-hover',
		layout
	]}
>
	{#if icon}<Icon name={icon} size={size === 'sm' ? 'sm' : 'md'} />{/if}
	{@render children?.()}
	{#if trailing}<Icon name={trailing} size="sm" />{/if}
</svelte:element>
