<script lang="ts">
	import type { Snippet } from 'svelte';

	export type Band = 'PRIORITY' | 'NURTURE' | 'WATCH' | 'THIN' | 'DISQUALIFIED';
	type Tone = 'default' | 'orange' | 'wash' | 'accent' | 'oxblood' | 'ink' | 'bare' | 'dashed';

	let {
		tone = 'default',
		band = null,
		title,
		flag = false,
		children,
		class: layout = ''
	}: {
		tone?: Tone;
		band?: Band | string | null;
		title?: string;
		flag?: boolean;
		children: Snippet;
		class?: string;
	} = $props();

	const BAND_TONE: Record<Band, Tone | 'thin' | 'disqualified'> = {
		PRIORITY: 'orange',
		NURTURE: 'wash',
		WATCH: 'default',
		THIN: 'thin',
		DISQUALIFIED: 'disqualified'
	};

	const look = $derived(band ? (BAND_TONE[band as Band] ?? 'default') : tone);
</script>

<span
	{title}
	class={[
		'inline-flex items-center gap-1 rounded-sm border px-1 text-micro leading-normal font-bold tracking-normal whitespace-nowrap',
		look === 'default' && 'border-rule-strong bg-surface text-ink-2',
		look === 'bare' && 'border-transparent bg-transparent px-0 text-ink-3',
		look === 'orange' && 'border-orange bg-orange text-on-orange',
		look === 'wash' && 'border-orange-line bg-orange-wash text-orange-ink',
		look === 'accent' && 'border-accent-line bg-accent-wash text-accent',
		look === 'oxblood' && 'border-oxblood-line bg-oxblood-wash text-oxblood',
		look === 'ink' && 'border-ink bg-ink text-paper',
		look === 'thin' && 'border-rule bg-transparent text-ink-3',
		look === 'disqualified' && 'border-oxblood bg-transparent text-oxblood',
		look === 'dashed' && 'border-dashed border-ink-4 bg-transparent text-ink-2',
		layout
	]}
>
	{#if flag}<span class="size-1 flex-none bg-current"></span>{/if}
	{@render children()}
</span>
