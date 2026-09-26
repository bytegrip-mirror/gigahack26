<script lang="ts">
	import type { HTMLTextareaAttributes } from 'svelte/elements';

	type Props = {
		value?: string | null;
		resize?: boolean;
		ref?: HTMLTextAreaElement | null;
		class?: string;
	} & Omit<HTMLTextareaAttributes, 'class' | 'value'>;

	let { value = $bindable(), resize = true, ref = $bindable(null), class: layout = '', ...rest }: Props = $props();
</script>

<textarea
	bind:this={ref}
	bind:value
	{...rest}
	class={[
		'block w-full rounded-sm border-2 border-rule-strong bg-surface px-2 py-1 font-ui text-md leading-relaxed transition-colors placeholder:text-ink-4',
		'focus:border-ink focus:outline-none disabled:opacity-40',
		resize ? 'resize-y' : 'resize-none',
		layout
	]}
></textarea>
