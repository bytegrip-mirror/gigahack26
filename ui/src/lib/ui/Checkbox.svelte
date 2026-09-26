<script lang="ts">
	import { Checkbox } from 'bits-ui';
	import type { Snippet } from 'svelte';
	import Icon from './Icon.svelte';

	let {
		checked = $bindable(false),
		indeterminate = false,
		disabled = false,
		label,
		onchange,
		children: text,
		class: layout = ''
	}: {
		checked?: boolean;
		indeterminate?: boolean;
		disabled?: boolean;
		label?: string;
		onchange?: (checked: boolean) => void;
		children?: Snippet;
		class?: string;
	} = $props();
</script>

<label class={['inline-flex items-start gap-2 text-base text-ink-2', disabled ? 'cursor-not-allowed opacity-40' : 'cursor-pointer', layout]}>
	<Checkbox.Root
		bind:checked
		{indeterminate}
		{disabled}
		aria-label={text ? undefined : label}
		onCheckedChange={(next) => onchange?.(next)}
		class="mt-0.5 inline-flex size-4 flex-none items-center justify-center rounded-sm border border-rule-strong bg-surface text-on-orange transition-colors data-[state=checked]:border-orange data-[state=checked]:bg-orange data-[state=indeterminate]:border-orange data-[state=indeterminate]:bg-orange"
	>
		{#snippet children({ checked, indeterminate })}
			{#if indeterminate}
				<span class="block h-0.5 w-2 bg-on-orange"></span>
			{:else if checked}
				<Icon name="check" size="sm" />
			{/if}
		{/snippet}
	</Checkbox.Root>
	{#if text}<span>{@render text()}</span>{/if}
</label>
