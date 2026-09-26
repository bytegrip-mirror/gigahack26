<script lang="ts">
	import { Command, DropdownMenu, Popover } from 'bits-ui';
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
		block = false,
		searchable = false,
		disabled = false,
		title = '',
		tone = 'default',
		class: layout = ''
	}: {
		items: MenuItem[];
		onselect: (id: string) => void;
		label?: string;
		icon?: IconName;
		text?: string;
		size?: 'sm' | 'md';
		align?: 'start' | 'end';
		block?: boolean;
		searchable?: boolean;
		disabled?: boolean;
		title?: string;
		tone?: 'default' | 'inverse';
		class?: string;
	} = $props();

	let open = $state(false);

	const available = $derived(items.some((item) => !item.disabled));

	function choose(id: string) {
		open = false;
		onselect(id);
	}

	const triggerClass = $derived([
		'inline-flex items-center rounded-sm transition-colors disabled:cursor-not-allowed disabled:opacity-40',
		text
			? [
					'gap-1 border-2 font-bold',
					tone === 'default' &&
						'border-ink bg-surface text-ink enabled:hover:bg-ink enabled:hover:text-surface data-[state=open]:bg-ink data-[state=open]:text-surface',
					tone === 'inverse' &&
						'border-ink-line bg-transparent text-paper enabled:hover:border-ink-4 enabled:hover:bg-ink-hover data-[state=open]:bg-ink-hover',
					size === 'sm' ? 'h-ctl-sm px-2 text-sm' : 'h-ctl px-3 text-base',
					block ? 'w-full justify-between' : 'justify-center'
				]
			: [
					'flex-none justify-center border border-transparent',
					tone === 'default' &&
						'text-ink-2 enabled:hover:bg-sunken enabled:hover:text-ink data-[state=open]:border-rule-strong data-[state=open]:bg-sunken data-[state=open]:text-ink',
					tone === 'inverse' && 'text-ink-4 enabled:hover:bg-ink-hover enabled:hover:text-paper data-[state=open]:bg-ink-hover',
					size === 'sm' ? 'size-ctl-sm' : 'size-ctl'
				],
		layout
	]);

	const contentClass =
		'z-menu flex animate-rise max-h-(--bits-dropdown-menu-content-available-height) min-w-menu-min max-w-menu flex-col overflow-y-auto border-2 border-ink bg-surface py-1 outline-none';
	const itemClass =
		'flex w-full cursor-default items-center gap-2 px-3 py-1 text-left text-base whitespace-nowrap text-ink outline-none select-none data-disabled:cursor-not-allowed data-disabled:opacity-40 data-highlighted:bg-sunken';
	const dangerClass = 'text-oxblood data-highlighted:bg-oxblood-wash';
</script>

{#snippet trigger()}
	{#if text}
		{#if icon}<Icon name={icon} size={size === 'sm' ? 'sm' : 'md'} />{/if}
		<span class={['truncate', block && 'grow text-left']}>{text}</span>
		<Icon name="chevronDown" size="sm" />
	{:else}
		<Icon name={icon ?? 'more'} size={size === 'sm' ? 'sm' : 'lg'} />
	{/if}
{/snippet}

{#snippet row(item: MenuItem)}
	{#if item.icon}<span class={item.danger ? 'text-oxblood' : 'text-ink-3'}><Icon name={item.icon} size="md" /></span>{/if}
	<span class="grow">{item.label}</span>
	{#if item.hint}<span class="num pl-4 text-micro text-ink-4">{item.hint}</span>{/if}
	{#if item.checked}<span class="text-ink-4"><Icon name="check" size="sm" /></span>{/if}
{/snippet}

{#if searchable}
	<Popover.Root bind:open>
		<Popover.Trigger
			{disabled}
			aria-label={text ? undefined : label}
			title={title || (text ? undefined : label)}
			class={triggerClass}
		>
			{@render trigger()}
		</Popover.Trigger>
		<Popover.Portal>
			<Popover.Content align={align} sideOffset={4} class={contentClass}>
				<Command.Root label={label} class="flex min-h-0 flex-col">
					<div class="sticky top-0 flex items-center gap-1 border-b border-rule bg-surface px-2 pb-2 text-ink-3">
						<Icon name="search" size="sm" />
						<Command.Input
							placeholder="Filter"
							class="h-ctl-sm w-full bg-transparent text-base text-ink outline-none placeholder:text-ink-4"
						/>
					</div>
					<Command.List class="pt-1">
						<Command.Empty class="px-3 py-1 type-label text-ink-4">Nothing matches</Command.Empty>
						{#each items as item (item.id)}
							{#if item.separatorBefore}<div class="my-1 h-px bg-rule"></div>{/if}
							<Command.Item
								value={item.id}
								keywords={[item.label, item.hint ?? '']}
								disabled={item.disabled}
								onSelect={() => choose(item.id)}
								class={[itemClass, item.danger && dangerClass]}
							>
								{@render row(item)}
							</Command.Item>
						{/each}
					</Command.List>
				</Command.Root>
			</Popover.Content>
		</Popover.Portal>
	</Popover.Root>
{:else}
	<DropdownMenu.Root bind:open>
		<DropdownMenu.Trigger
			{disabled}
			aria-label={text ? undefined : label}
			title={title || (text ? undefined : label)}
			class={triggerClass}
		>
			{@render trigger()}
		</DropdownMenu.Trigger>
		<DropdownMenu.Portal>
			<DropdownMenu.Content align={align} sideOffset={4} class={contentClass} aria-label={label}>
				{#each items as item (item.id)}
					{#if item.separatorBefore}<DropdownMenu.Separator class="my-1 h-px bg-rule" />{/if}
					<DropdownMenu.Item
						disabled={item.disabled}
						onSelect={() => choose(item.id)}
						class={[itemClass, item.danger && dangerClass]}
					>
						{@render row(item)}
					</DropdownMenu.Item>
				{/each}
				{#if !available}<p class="px-3 py-1 type-label text-ink-4">Nothing available</p>{/if}
			</DropdownMenu.Content>
		</DropdownMenu.Portal>
	</DropdownMenu.Root>
{/if}
