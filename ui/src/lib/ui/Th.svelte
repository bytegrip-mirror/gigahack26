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
		title,
		class: layout = ''
	}: {
		id: string;
		label: string;
		sort: string;
		dir?: 'asc' | 'desc';
		onsort: (id: string) => void;
		align?: 'left' | 'right' | 'center';
		width?: string | null;
		title?: string;
		class?: string;
	} = $props();

	const active = $derived(sort === id);
	const ariaSort = $derived(active ? (dir === 'asc' ? 'ascending' : 'descending') : 'none');
</script>

<th scope="col" class={layout} data-col={id} style:width={width ?? undefined} aria-sort={ariaSort}>
	<button
		type="button"
		title={title ?? `Sort by ${label.toLowerCase()}`}
		onclick={() => onsort(id)}
		class={[
			'group flex h-full w-full items-center gap-1 uppercase',
			align === 'right' && 'justify-end',
			align === 'center' && 'justify-center',
			active ? 'text-ink' : 'text-inherit'
		]}
	>
		{label}
		<span class={active ? 'text-orange-deep' : 'text-rule-strong group-hover:text-ink-3'}>
			<Icon name={active ? (dir === 'asc' ? 'arrowUp' : 'arrowDown') : 'sort'} size="sm" />
		</span>
	</button>
</th>
