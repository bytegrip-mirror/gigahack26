<script lang="ts">
	import type { HTMLButtonAttributes } from 'svelte/elements';
	import Icon from './Icon.svelte';
	import type { IconName } from './icons';

	type Props = {
		icon: IconName;
		label: string;
		size?: 'md' | 'sm';
		tone?: 'default' | 'inverse' | 'danger';
		pressed?: boolean;
		href?: string;
		target?: string;
		rel?: string;
		class?: string;
	} & Omit<HTMLButtonAttributes, 'class' | 'children'>;

	let {
		icon,
		label,
		size = 'md',
		tone = 'default',
		pressed = false,
		href,
		type = 'button',
		title,
		class: layout = '',
		...rest
	}: Props = $props();
</script>

<svelte:element
	this={href ? 'a' : 'button'}
	role={href ? 'link' : undefined}
	{href}
	type={href ? undefined : type}
	aria-label={label}
	title={title ?? label}
	{...rest}
	class={[
		'inline-flex flex-none items-center justify-center rounded-sm border transition-colors disabled:cursor-not-allowed disabled:opacity-40',
		size === 'md' ? 'size-ctl' : 'size-ctl-sm',
		pressed ? 'border-rule-strong bg-sunken text-ink' : 'border-transparent',
		tone === 'default' && 'text-ink-2 enabled:hover:bg-sunken enabled:hover:text-ink aria-expanded:border-rule-strong aria-expanded:bg-sunken aria-expanded:text-ink',
		tone === 'inverse' && 'text-ink-4 enabled:hover:bg-ink-hover enabled:hover:text-paper',
		tone === 'danger' && 'text-ink-3 enabled:hover:bg-oxblood-wash enabled:hover:text-oxblood',
		layout
	]}
>
	<Icon name={icon} size={size === 'md' ? 'lg' : 'sm'} />
</svelte:element>
