<script lang="ts">
	import Icon from './Icon.svelte';

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
</script>

<div class="pager">
	<span class="num">{from}–{to}</span>
	<span>of <span class="num">{total}</span> {unit}</span>
	<span class="spacer"></span>
	<label class="page-size">
		<span class="sr-only">Rows per page</span>
		<select
			class="select select-sm"
			value={size}
			onchange={(event) => onsize(Number(event.currentTarget.value))}
		>
			{#each sizes as option (option)}
				<option value={option}>{option === 0 ? 'All' : option}</option>
			{/each}
		</select>
	</label>
	<div class="steps">
		<button
			class="icon-btn icon-btn-sm"
			type="button"
			aria-label="Previous page"
			title="Previous page"
			disabled={page <= 1}
			onclick={() => onpage(page - 1)}
		>
			<Icon name="chevronLeft" size={14} />
		</button>
		<span class="num page-of">{page} / {pages}</span>
		<button
			class="icon-btn icon-btn-sm"
			type="button"
			aria-label="Next page"
			title="Next page"
			disabled={page >= pages}
			onclick={() => onpage(page + 1)}
		>
			<Icon name="chevronRight" size={14} />
		</button>
	</div>
</div>

<style>
	.page-size {
		display: flex;
		align-items: center;
	}

	.steps {
		display: flex;
		align-items: center;
		gap: 2px;
	}

	.page-of {
		min-width: 44px;
		text-align: center;
		font-size: var(--fs-sm);
	}
</style>
