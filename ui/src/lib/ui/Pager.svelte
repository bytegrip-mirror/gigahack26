<script lang="ts">
	import IconButton from './IconButton.svelte';
	import Select from './Select.svelte';

	let {
		total,
		page,
		size,
		onsize,
		onpage,
		unit = 'rows',
		sizes = [50, 100, 250, 0]
	}: {
		total: number;
		page: number;
		size: number;
		onsize: (size: number) => void;
		onpage: (page: number) => void;
		unit?: string;
		sizes?: number[];
	} = $props();

	const effective = $derived(size === 0 ? Math.max(total, 1) : size);
	const pages = $derived(Math.max(1, Math.ceil(total / effective)));
	const from = $derived(total === 0 ? 0 : (page - 1) * effective + 1);
	const to = $derived(Math.min(total, page * effective));
	const options = $derived(sizes.map((option) => ({ value: option, label: option === 0 ? 'All' : String(option) })));
</script>

<div class="flex flex-none items-center gap-3 border-t border-rule bg-surface px-4 py-2 text-sm text-ink-3">
	<span class="num">{from}–{to}</span>
	<span>of <span class="num">{total}</span> {unit}</span>
	<Select class="ml-auto w-auto" size="sm" label="Rows per page" value={size} {options} onchange={onsize} />
	<div class="flex items-center gap-0.5">
		<IconButton icon="chevronLeft" label="Previous page" size="sm" disabled={page <= 1} onclick={() => onpage(page - 1)} />
		<span class="num min-w-bar text-center">{page} / {pages}</span>
		<IconButton icon="chevronRight" label="Next page" size="sm" disabled={page >= pages} onclick={() => onpage(page + 1)} />
	</div>
</div>
