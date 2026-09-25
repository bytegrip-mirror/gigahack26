<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title,
		subtitle = null,
		wide = false,
		onclose,
		children,
		footer
	}: {
		title: string;
		subtitle?: string | null;
		wide?: boolean;
		onclose: () => void;
		children: Snippet;
		footer?: Snippet;
	} = $props();

	let dialog = $state<HTMLDivElement | null>(null);

	$effect(() => {
		const onKey = (event: KeyboardEvent) => {
			if (event.key === 'Escape') onclose();
		};
		window.addEventListener('keydown', onKey);
		document.body.style.overflow = 'hidden';
		dialog?.focus();
		return () => {
			window.removeEventListener('keydown', onKey);
			document.body.style.overflow = '';
		};
	});
</script>

<div
	class="backdrop"
	role="presentation"
	onclick={(event) => {
		if (event.target === event.currentTarget) onclose();
	}}
>
	<div class="panel" class:wide role="dialog" aria-modal="true" aria-label={title} tabindex="-1" bind:this={dialog}>
		<header>
			<div class="stack">
				<h2>{title}</h2>
				{#if subtitle}<span class="tiny muted">{subtitle}</span>{/if}
			</div>
			<button class="btn btn-ghost" onclick={onclose} aria-label="Close">✕</button>
		</header>
		<div class="body scroll-thin">
			{@render children()}
		</div>
		{#if footer}
			<footer>{@render footer()}</footer>
		{/if}
	</div>
</div>

<style>
	.backdrop {
		position: fixed;
		inset: 0;
		background: rgb(30 22 12 / 0.42);
		backdrop-filter: blur(2px);
		display: flex;
		align-items: flex-start;
		justify-content: center;
		padding: 5vh 16px;
		z-index: 80;
		overflow: auto;
	}

	.panel {
		background: var(--surface);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-lg);
		width: 100%;
		max-width: 780px;
		display: flex;
		flex-direction: column;
		max-height: 88vh;
		border: 1px solid var(--line);
	}

	.panel.wide {
		max-width: 1080px;
	}

	header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 16px;
		padding: 16px 18px;
		border-bottom: 1px solid var(--line);
	}

	.body {
		padding: 18px;
		overflow: auto;
	}

	footer {
		padding: 12px 18px;
		border-top: 1px solid var(--line);
		display: flex;
		justify-content: flex-end;
		gap: 8px;
		background: var(--surface-sunken);
		border-radius: 0 0 var(--radius-lg) var(--radius-lg);
	}
</style>
