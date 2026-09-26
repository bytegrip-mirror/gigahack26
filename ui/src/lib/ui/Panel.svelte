<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title,
		heading = 'section',
		tools,
		children,
		class: layout = ''
	}: {
		title?: string;
		heading?: 'page' | 'section' | 'label';
		tools?: Snippet;
		children: Snippet;
		class?: string;
	} = $props();
</script>

<section class={['flex flex-col gap-2 border-b border-rule px-4 py-3 last:border-b-0', layout]}>
	{#if title || tools}
		<div class="flex min-h-ctl-sm items-center justify-between gap-2">
			{#if title}
				<h2
					class={[
						heading === 'page' && 'type-display text-xl leading-snug',
						heading === 'section' && 'type-display text-md leading-snug',
						heading === 'label' && 'type-label'
					]}
				>
					{title}
				</h2>
			{/if}
			{#if tools}<div class="flex items-center gap-2">{@render tools()}</div>{/if}
		</div>
	{/if}
	{@render children()}
</section>
