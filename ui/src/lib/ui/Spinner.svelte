<script lang="ts">
	let { label }: { label?: string } = $props();
</script>

<span
	role={label ? 'status' : undefined}
	aria-label={label}
	class="block size-3 flex-none animate-spin rounded-full border border-rule-strong border-t-orange"
></span>
