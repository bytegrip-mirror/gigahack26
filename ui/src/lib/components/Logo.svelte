<script lang="ts">
	let { size = 20, title = 'Nudge' }: { size?: number; title?: string } = $props();
</script>

<!--
	Three bars, rising, the tallest in the accent — the same drawing as
	`static/favicon.svg`, which is the tab and was the only place it survived
	two changes of mark.

	It was replaced once for a radar and once for a signal, both of which
	argued that the product does more than draw a chart. True, and beside the
	point: this reads at 16 pixels, it is unmistakably ours, and it is the one
	the tab has been showing all along.
-->
<svg
	width={size}
	height={size}
	viewBox="0 0 19 20"
	role="img"
	aria-label={title}
	focusable="false"
>
	<rect x="0" y="12" width="5" height="8" fill="var(--color-ink)" />
	<rect x="7" y="7" width="5" height="13" fill="var(--color-ink)" />
	<rect x="14" y="2" width="5" height="18" fill="var(--color-orange)" />
</svg>
