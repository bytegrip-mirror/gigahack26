<script lang="ts">
	import { Dialog } from 'bits-ui';
	import type { Snippet } from 'svelte';
	import IconButton from './IconButton.svelte';

	let {
		open = $bindable(false),
		kind = 'dialog',
		title,
		subtitle,
		label,
		closeLabel = 'Close',
		tools,
		footer,
		children,
		onclose
	}: {
		open?: boolean;
		kind?: 'dialog' | 'sheet' | 'palette';
		title?: string;
		subtitle?: Snippet;
		label?: string;
		closeLabel?: string;
		tools?: Snippet;
		footer?: Snippet;
		children: Snippet;
		onclose?: () => void;
	} = $props();
</script>

<Dialog.Root
	bind:open
	onOpenChange={(next) => {
		if (!next) onclose?.();
	}}
>
	<Dialog.Portal>
		<Dialog.Overlay class="fixed inset-0 z-overlay bg-scrim" />
		<Dialog.Content
			aria-label={title ? undefined : label}
			class={[
				'fixed z-overlay flex min-h-0 flex-col bg-surface outline-none',
				kind === 'dialog' && 'inset-x-4 top-1/2 mx-auto h-11/12 max-w-dialog -translate-y-1/2 border border-ink',
				kind === 'sheet' && 'inset-y-0 right-0 w-full max-w-sheet border-l border-ink',
				kind === 'palette' && 'inset-x-4 top-1/8 mx-auto max-h-3/4 max-w-palette border border-ink'
			]}
		>
			{#if title}
				<header class="flex flex-none items-start justify-between gap-3 border-b border-ink p-3">
					<div class="min-w-0">
						<Dialog.Title class="type-display text-xl leading-snug">{title}</Dialog.Title>
						{#if subtitle}<Dialog.Description class="mt-0.5 type-sub">{@render subtitle()}</Dialog.Description>{/if}
					</div>
					<div class="flex flex-none items-center gap-2">
						{@render tools?.()}
						<Dialog.Close>
							{#snippet child({ props })}
								<IconButton {...props} icon="close" label={closeLabel} />
							{/snippet}
						</Dialog.Close>
					</div>
				</header>
			{/if}
			<div class="relative flex min-h-0 flex-1 flex-col">{@render children()}</div>
			{#if footer}
				<footer class="flex flex-none items-center gap-2 border-t border-rule p-3">{@render footer()}</footer>
			{/if}
		</Dialog.Content>
	</Dialog.Portal>
</Dialog.Root>
