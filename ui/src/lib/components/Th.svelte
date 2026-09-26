<script lang="ts">
	import Icon from './Icon.svelte';

	let {
		id,
		label,
		sort,
		dir = 'asc',
		onsort,
		align = 'left',
		width = null,
		title = '',
		className = ''
	}: {
		id: string;
		label: string;
		sort: string;
		dir?: 'asc' | 'desc';
		onsort: (id: string) => void;
		align?: 'left' | 'right' | 'center';
		width?: string | null;
		title?: string;
		className?: string;
	} = $props();

	const active = $derived(sort === id);
	const ariaSort = $derived(active ? (dir === 'asc' ? 'ascending' : 'descending') : 'none');
</script>

<th
	scope="col"
	class="{align === 'right' ? 'c' : align === 'center' ? 'm' : ''} {className}"
	data-col={id}
	style:width={width ?? undefined}
	aria-sort={ariaSort}
>
	<button
		class="th-sort"
		type="button"
		title={title || `Sort by ${label.toLowerCase()}`}
		onclick={() => onsort(id)}
	>
		{label}
		<Icon name={active ? (dir === 'asc' ? 'arrowUp' : 'arrowDown') : 'sort'} size={12} />
	</button>
</th>
