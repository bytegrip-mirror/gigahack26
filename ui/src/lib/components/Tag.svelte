<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		variant = 'default',
		band = null,
		title = '',
		flag = false,
		children
	}: {
		variant?: 'default' | 'orange' | 'wash' | 'accent' | 'oxblood' | 'ink' | 'bare';
		band?: string | null;
		title?: string;
		flag?: boolean;
		children: Snippet;
	} = $props();

	const tone = $derived(band === null ? `tag-${variant}` : '');
</script>

<span class="tag {tone}" class:tag-flag={flag} data-band={band ?? undefined} {title}>
	{@render children()}
</span>
