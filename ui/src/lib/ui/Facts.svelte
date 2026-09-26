<script lang="ts">
	type Fact = { label: string; value: string | number; title?: string };

	let { facts }: { facts: Fact[] } = $props();
</script>

<dl class="grid-kv gap-x-3 gap-y-1 text-base">
	{#each facts as fact (fact.label)}
		<dt class="text-ink-3">{fact.label}</dt>
		<dd class="num text-right" title={fact.title}>{fact.value}</dd>
	{/each}
</dl>
