<script lang="ts">
	import Icon from './Icon.svelte';
	import type { IconName } from './icons';
	import type { MenuItem } from './menu';

	let {
		items,
		onselect,
		label = 'More actions',
		icon,
		text = '',
		size = 'md',
		align = 'end',
		stretch = false,
		searchable = false,
		disabled = false,
		title = ''
	}: {
		items: MenuItem[];
		onselect: (id: string) => void;
		label?: string;
		icon?: IconName;
		text?: string;
		size?: 'sm' | 'md';
		align?: 'start' | 'end';
		stretch?: boolean;
		searchable?: boolean;
		disabled?: boolean;
		title?: string;
	} = $props();

	let needle = $state('');
	let field = $state<HTMLInputElement | null>(null);

	let open = $state(false);
	let at = $state({ top: 0, left: 0, min: 0 });
	let trigger = $state<HTMLButtonElement | null>(null);
	let panel = $state<HTMLDivElement | null>(null);

	const options = $derived(items.filter((item) => !item.disabled));

	const shown = $derived.by(() => {
		const term = needle.trim().toLowerCase();
		if (!term) return items;
		return items.filter(
			(item) => item.label.toLowerCase().includes(term) || (item.hint ?? '').toLowerCase().includes(term)
		);
	});

	function place() {
		const node = trigger;
		if (!node) return;
		const box = node.getBoundingClientRect();
		const width = panel?.offsetWidth ?? 200;
		const height = panel?.offsetHeight ?? 40;
		const left =
			align === 'end'
				? Math.min(Math.max(8, box.right - width), window.innerWidth - width - 8)
				: Math.min(Math.max(8, box.left), window.innerWidth - width - 8);
		const below = box.bottom + 4;
		const top = below + height > window.innerHeight - 8 ? Math.max(8, box.top - height - 4) : below;
		at = { top, left, min: width };
	}

	function show() {
		if (disabled) return;
		needle = '';
		open = true;
	}

	function hide(refocus = false) {
		open = false;
		if (refocus) trigger?.focus();
	}

	function choose(id: string) {
		hide();
		onselect(id);
	}

	$effect(() => {
		if (!open) return;
		requestAnimationFrame(() => {
			place();
			if (searchable) field?.focus();
		});
		const close = () => hide();
		window.addEventListener('scroll', close, true);
		return () => window.removeEventListener('scroll', close, true);
	});

	$effect(() => {
		void shown.length;
		if (open) requestAnimationFrame(place);
	});

	function onKey(event: KeyboardEvent) {
		if (!open) return;
		const nodes = [...(panel?.querySelectorAll<HTMLButtonElement>('.menu-item:not(:disabled)') ?? [])];
		const index = nodes.indexOf(document.activeElement as HTMLButtonElement);
		if (event.key === 'Escape') {
			event.preventDefault();
			if (searchable && needle) needle = '';
			else hide(true);
		} else if (searchable && event.key.length === 1 && document.activeElement !== field) {
			field?.focus();
		} else if (event.key === 'ArrowDown') {
			event.preventDefault();
			nodes[Math.min(nodes.length - 1, index + 1)]?.focus();
		} else if (event.key === 'ArrowUp') {
			event.preventDefault();
			nodes[Math.max(0, index - 1)]?.focus();
		} else if (event.key === 'Home') {
			event.preventDefault();
			nodes[0]?.focus();
		} else if (event.key === 'End') {
			event.preventDefault();
			nodes[nodes.length - 1]?.focus();
		}
	}
</script>

<svelte:window onkeydown={onKey} onresize={() => hide()} />

<button
	bind:this={trigger}
	class="menu-trigger {text ? (size === 'sm' ? 'btn btn-sm' : 'btn') : size === 'sm' ? 'icon-btn icon-btn-sm' : 'icon-btn'}"
	class:stretch
	type="button"
	aria-haspopup="menu"
	aria-expanded={open}
	aria-label={text ? undefined : label}
	title={title || (text ? undefined : label)}
	{disabled}
	onclick={(event) => {
		event.stopPropagation();
		open ? hide() : show();
	}}
>
	{#if text}
		{#if icon}<Icon name={icon} size={size === 'sm' ? 14 : 15} />{/if}
		<span class="trigger-text">{text}</span>
		<Icon name="chevronDown" size={13} />
	{:else}
		<Icon name={icon ?? 'more'} size={size === 'sm' ? 14 : 16} />
	{/if}
</button>

{#if open}
	<div class="menu-scrim" role="presentation" onclick={() => hide()}></div>
	<div
		bind:this={panel}
		class="menu"
		role="menu"
		aria-label={label}
		style:top="{at.top}px"
		style:left="{at.left}px"
		style:min-width="{at.min}px"
	>
		{#if searchable}
			<div class="menu-search">
				<Icon name="search" size={14} />
				<input
					bind:this={field}
					class="input input-sm"
					placeholder="Filter"
					autocomplete="off"
					spellcheck="false"
					bind:value={needle}
					onkeydown={(event) => {
						if (event.key === 'Enter') {
							event.preventDefault();
							const first = shown.find((item) => !item.disabled);
							if (first) choose(first.id);
						} else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
							event.preventDefault();
							const options = panel?.querySelectorAll<HTMLButtonElement>('.menu-item:not(:disabled)') ?? [];
							(options[event.key === 'ArrowDown' ? 0 : options.length - 1] as HTMLButtonElement | undefined)?.focus();
						}
					}}
				/>
			</div>
		{/if}

		{#each shown as item (item.id)}
			{#if item.separatorBefore}<div class="menu-sep"></div>{/if}
			<button
				class="menu-item"
				type="button"
				role="menuitem"
				data-danger={item.danger ? 'true' : undefined}
				disabled={item.disabled}
				onclick={() => choose(item.id)}
			>
				{#if item.icon}<Icon name={item.icon} size={15} />{/if}
				<span>{item.label}</span>
				{#if item.hint}<span class="hint num">{item.hint}</span>{/if}
				{#if item.checked}<span class="hint"><Icon name="check" size={13} /></span>{/if}
			</button>
		{/each}
		{#if options.length === 0}
			<p class="menu-label">Nothing available</p>
		{:else if shown.length === 0}
			<p class="menu-label">Nothing matches “{needle}”</p>
		{/if}
	</div>
{/if}

<style>
	.menu-search {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		padding: var(--spacing-1) var(--spacing-2) var(--spacing-2);
		margin-bottom: var(--spacing-1);
		border-bottom: 1px solid var(--color-rule);
		color: var(--color-ink-3);
		position: sticky;
		top: 0;
		background: var(--color-surface);
	}

	.menu-search .input {
		border-color: transparent;
		padding-left: 0;
	}

	.menu-search .input:focus {
		box-shadow: none;
	}

	.trigger-text {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.menu-trigger.stretch {
		width: 100%;
	}

	.menu-trigger.stretch .trigger-text {
		flex: 1;
		text-align: left;
	}</style>
