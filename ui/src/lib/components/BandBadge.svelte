<script lang="ts">
	import type { Band } from '$lib/api';

	let { band, label, title = '' }: { band: Band; label: string; title?: string } = $props();
</script>

<span class="band band-{band}" {title}>{label}</span>
