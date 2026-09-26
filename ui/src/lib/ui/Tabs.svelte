<script lang="ts" generics="T extends string">
	import { Tabs } from 'bits-ui';
	import type { Snippet } from 'svelte';

	type Tab = { value: T; label: string; count?: number | string };

	let {
		value = $bindable(),
		tabs,
		label,
		trailing,
		children,
		class: layout = ''
	}: {
		value: T;
		tabs: Tab[];
		label: string;
		trailing?: Snippet;
		children: Snippet<[T]>;
		class?: string;
	} = $props();
</script>

<Tabs.Root bind:value={value as string} class={['flex min-h-0 flex-col', layout]}>
	<div class="flex flex-none items-center gap-2 border-b border-rule bg-surface px-4">
		<Tabs.List aria-label={label} class="flex gap-4">
			{#each tabs as tab (tab.value)}
				<Tabs.Trigger
					value={tab.value}
					class="-mb-px inline-flex h-bar items-center gap-1 border-b-2 border-transparent text-base font-medium text-ink-3 transition-colors hover:text-ink data-[state=active]:border-orange data-[state=active]:text-ink"
				>
					{tab.label}
					{#if tab.count !== undefined}<span class="num text-micro text-ink-4">{tab.count}</span>{/if}
				</Tabs.Trigger>
			{/each}
		</Tabs.List>
		{#if trailing}<div class="ml-auto flex items-center gap-2">{@render trailing()}</div>{/if}
	</div>
	{#each tabs as tab (tab.value)}
		<Tabs.Content value={tab.value} class="min-h-0 flex-1 outline-none">
			{#if value === tab.value}{@render children(tab.value)}{/if}
		</Tabs.Content>
	{/each}
</Tabs.Root>
