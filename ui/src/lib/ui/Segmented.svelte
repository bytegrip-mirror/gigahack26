<script lang="ts" generics="T extends string">
	import { ToggleGroup } from 'bits-ui';

	type Option = { value: T; label: string; count?: number | string; disabled?: boolean; title?: string };

	let {
		value = $bindable(),
		options,
		label,
		size = 'md',
		fill = false,
		onchange,
		class: layout = ''
	}: {
		value: T;
		options: Option[];
		label: string;
		size?: 'md' | 'sm';
		fill?: boolean;
		onchange?: (value: T) => void;
		class?: string;
	} = $props();

	function pick(next: string) {
		if (!next) return;
		value = next as T;
		onchange?.(next as T);
	}
</script>

<ToggleGroup.Root
	type="single"
	{value}
	onValueChange={pick}
	aria-label={label}
	class={[
		fill ? 'flex w-full' : 'inline-flex',
		'overflow-hidden rounded-sm border border-rule-strong bg-surface',
		size === 'md' ? 'h-ctl' : 'h-ctl-sm',
		layout
	]}
>
	{#each options as option (option.value)}
		<ToggleGroup.Item
			value={option.value}
			disabled={option.disabled}
			title={option.title}
			class={['group inline-flex items-center gap-1 border-r border-rule px-2 text-base font-medium whitespace-nowrap text-ink-2 transition-colors last:border-r-0 enabled:hover:bg-sunken disabled:cursor-not-allowed disabled:opacity-40 data-[state=on]:bg-ink data-[state=on]:text-paper data-[state=on]:hover:bg-ink', fill && 'flex-1 justify-center']}
		>
			{option.label}
			{#if option.count !== undefined}
				<span class="num text-micro text-ink-3 group-data-[state=on]:text-orange">{option.count}</span>
			{/if}
		</ToggleGroup.Item>
	{/each}
</ToggleGroup.Root>
