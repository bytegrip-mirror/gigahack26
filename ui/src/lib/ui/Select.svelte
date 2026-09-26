<script lang="ts" generics="T extends string | number">
	import { Select } from 'bits-ui';
	import Icon from './Icon.svelte';

	type Option = { value: T; label: string; disabled?: boolean };

	let {
		value = $bindable(),
		options,
		size = 'md',
		label,
		placeholder = 'Choose',
		disabled = false,
		id,
		onchange,
		class: layout = ''
	}: {
		value: T;
		options: Option[];
		size?: 'md' | 'sm';
		label: string;
		placeholder?: string;
		disabled?: boolean;
		id?: string;
		onchange?: (value: T) => void;
		class?: string;
	} = $props();

	const items = $derived(options.map((option) => ({ ...option, value: String(option.value) })));
	const current = $derived(options.find((option) => String(option.value) === String(value)));

	function pick(next: string) {
		const option = options.find((candidate) => String(candidate.value) === next);
		if (!option) return;
		value = option.value;
		onchange?.(option.value);
	}
</script>

<Select.Root type="single" value={String(value)} onValueChange={pick} {items} {disabled}>
	<Select.Trigger
		{id}
		aria-label={label}
		class={[
			'inline-flex w-full items-center justify-between gap-2 rounded-sm border border-rule-strong bg-surface pl-2 pr-1 text-left font-ui transition-colors',
			'focus:border-orange focus:outline-none focus:ring-2 focus:ring-orange-wash disabled:cursor-not-allowed disabled:opacity-40 data-[state=open]:border-ink-3',
			size === 'md' ? 'h-ctl text-md' : 'h-ctl-sm text-base',
			layout
		]}
	>
		<span class={['truncate', !current && 'text-ink-4']}>{current?.label ?? placeholder}</span>
		<span class="text-ink-3"><Icon name="chevronDown" size="sm" /></span>
	</Select.Trigger>
	<Select.Portal>
		<Select.Content
			sideOffset={4}
			class="z-menu animate-rise max-h-(--bits-select-content-available-height) min-w-(--bits-select-anchor-width) overflow-y-auto border border-ink bg-surface py-1"
		>
			<Select.Viewport>
				{#each items as item (item.value)}
					<Select.Item
						value={item.value}
						label={item.label}
						disabled={item.disabled}
						class="flex cursor-default items-center gap-2 px-3 py-1 text-base text-ink outline-none select-none data-disabled:opacity-40 data-highlighted:bg-sunken"
					>
						{#snippet children({ selected })}
							<span class="grow">{item.label}</span>
							{#if selected}<span class="text-orange-deep"><Icon name="check" size="sm" /></span>{/if}
						{/snippet}
					</Select.Item>
				{/each}
			</Select.Viewport>
		</Select.Content>
	</Select.Portal>
</Select.Root>
