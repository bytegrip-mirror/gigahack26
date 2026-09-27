<script lang="ts">
	import { page } from '$app/state';
	import { session } from '$lib/session.svelte';
	import CompanyView from '$lib/views/CompanyView.svelte';

	const id = $derived(page.params.id ?? '');
	const line = $derived(page.url.searchParams.get('line') ?? session.serviceLine);
</script>

<svelte:head><title>Company · Lead Radar</title></svelte:head>

<CompanyView companyId={id} serviceLine={line} />
