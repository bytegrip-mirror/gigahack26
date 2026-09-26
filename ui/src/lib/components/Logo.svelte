<script lang="ts">
	let { size = 20, title = 'Signal Desk' }: { size?: number; title?: string } = $props();
</script>

<svg
	width={size}
	height={size}
	viewBox="0 0 19 20"
	role="img"
	aria-label={title}
	focusable="false"
>
	<rect x="0" y="12" width="5" height="8" fill="var(--ink)" />
	<rect x="7" y="7" width="5" height="13" fill="var(--ink)" />
	<rect x="14" y="2" width="5" height="18" fill="var(--orange)" />
</svg>
