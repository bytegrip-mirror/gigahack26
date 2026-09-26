<script lang="ts">
	let { size = 16, title = 'HubSpot' }: { size?: number; title?: string } = $props();
</script>

<svg
	class="block flex-none"
	width={size}
	height={size}
	viewBox="0 0 24 24"
	fill="none"
	stroke="currentColor"
	stroke-width="1.9"
	stroke-linecap="round"
	role={title ? 'img' : 'presentation'}
	aria-hidden={title ? undefined : 'true'}
	focusable="false"
>
	{#if title}<title>{title}</title>{/if}
	<circle cx="14.4" cy="13" r="5.4" />
	<path d="M14.4 7.6V4.9" />
	<path d="M9.9 17 6.2 20.3" />
	<circle cx="4.6" cy="21.5" r="1.5" fill="currentColor" stroke="none" />
	<circle cx="14.4" cy="2.9" r="1.5" fill="currentColor" stroke="none" />
</svg>
