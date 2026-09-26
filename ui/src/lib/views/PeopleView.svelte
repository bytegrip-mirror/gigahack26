<script lang="ts">
	import { extractPeople, fetchPeople, type PersonRow } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { date, fitTone, fitWord, host, safeHref } from '$lib/format';
	import { csv, save } from '$lib/export';
	import CompanyMark from '$lib/components/CompanyMark.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import Metric from '$lib/components/Metric.svelte';

	const SORTS = [
		{ id: 'company', label: 'Best company first' },
		{ id: 'name', label: 'Name, A to Z' },
		{ id: 'role', label: 'Role, A to Z' }
	];

	let rows = $state<PersonRow[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let query = $state('');
	let sort = $state('company');
	let busy = $state(false);

	const serviceLine = $derived(session.serviceLine);
	const thresholds = $derived({
		watch: workspace.meta?.scoring.watchThreshold ?? 0.28,
		nurture: workspace.meta?.scoring.nurtureThreshold ?? 0.45,
		priority: workspace.meta?.scoring.priorityThreshold ?? 0.55
	});

	async function load() {
		if (!serviceLine) return;
		rows = await fetchPeople(serviceLine);
	}

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		let cancelled = false;
		loading = true;
		fetchPeople(line)
			.then((result) => {
				if (cancelled) return;
				rows = result;
				error = null;
			})
			.catch((cause) => {
				if (!cancelled) error = (cause as Error).message;
			})
			.finally(() => {
				if (!cancelled) loading = false;
			});
		return () => {
			cancelled = true;
		};
	});

	const matched = $derived.by(() => {
		const needle = query.trim().toLowerCase();
		const list = needle
			? rows.filter((row) => `${row.name} ${row.role} ${row.company}`.toLowerCase().includes(needle))
			: [...rows];
		list.sort((a, b) => {
			if (sort === 'name') return a.name.localeCompare(b.name);
			if (sort === 'role') return a.role.localeCompare(b.role) || a.name.localeCompare(b.name);
			return b.probability - a.probability || a.company.localeCompare(b.company) || a.name.localeCompare(b.name);
		});
		return list;
	});

	const companies = $derived(new Set(rows.map((row) => row.companyId)).size);

	async function runExtract(reset = false) {
		busy = true;
		try {
			const result = await extractPeople(reset);
			await load();
			toasts.push(
				result.added === 0
					? `Nothing new. ${result.total} people found so far.`
					: `Read the documents again: ${result.added} more people, ${result.total} in total.`,
				'success'
			);
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = false;
		}
	}

	function exportPeople() {
		save(
			`people-${serviceLine.toLowerCase().replace(/\s+/g, '-')}.csv`,
			csv(
				['name', 'role', 'company', 'domain', 'fit', 'latest_signal', 'publisher', 'source'],
				matched.map((row) => [
					row.name,
					row.role,
					row.company,
					row.domain ?? '',
					row.probability.toFixed(2),
					row.publishedAt ?? '',
					row.publisher,
					row.sourceUrl
				])
			)
		);
		toasts.push(`Exported ${matched.length} people.`, 'success');
	}

	const overflow = $derived([
		{ id: 'export', label: 'Export these people as CSV', icon: 'download' as const },
		{
			id: 'extract',
			label: 'Read the documents again for names',
			icon: 'refresh' as const,
			separatorBefore: true
		},
		{
			id: 'rebuild',
			label: 'Throw the list away and read again',
			icon: 'trash' as const,
			hint: 'after a change to how names are read'
		}
	]);

	async function runOverflow(id: string) {
		if (id === 'export') exportPeople();
		else if (id === 'extract') await runExtract();
		else if (id === 'rebuild') await runExtract(true);
	}
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">People</h1>
			<p class="sub">
				{serviceLine} · the people our documents name, and what they do. Every one is a name a page printed next
				to a job title — nobody here has been guessed at.
			</p>
		</div>
		<div class="topbar-tools">
			{#if rows.length > 0}
				<div class="metrics">
					<Metric value={rows.length} label="People" />
					<Metric value={companies} label="Companies" />
				</div>
			{/if}
			<button class="btn btn-primary" onclick={() => runExtract()} disabled={busy}>
				{#if busy}<span class="spinner"></span>{/if}
				<Icon name="search" size={15} />
				Read for names
			</button>
			<Menu items={overflow} onselect={runOverflow} label="More on people" />
		</div>
	</header>

	<div class="toolbar">
		<label class="search">
			<span class="sr-only">Filter by name, role or company</span>
			<Icon name="search" size={14} />
			<input class="input input-sm" placeholder="Name, role, company" bind:value={query} />
		</label>
		<span class="spacer"></span>
		<span class="sub dim num">{matched.length} of {rows.length} shown</span>
		<Menu
			text={SORTS.find((entry) => entry.id === sort)?.label ?? 'Sort'}
			icon="sort"
			size="sm"
			align="end"
			label="Sort the people"
			items={SORTS.map((entry) => ({ id: entry.id, label: entry.label, checked: sort === entry.id }))}
			onselect={(id) => (sort = id)}
		/>
	</div>

	<section class="pane scroll" aria-label="People">
		<LoadingVeil show={loading} label="Reading the documents" />

		{#if error}
			<p class="note warn"><Icon name="alert" size={15} /> {error}</p>
		{:else if matched.length === 0 && !loading}
			<div class="empty">
				<Icon name="inbox" size={22} />
				{#if rows.length === 0}
					<p>
						No document has named anybody at these companies yet. Press <strong>Read for names</strong> to
						look through the documents we already hold.
					</p>
				{:else}
					<p>Nobody matches “{query}”.</p>
				{/if}
			</div>
		{:else}
			<table class="tbl">
				<thead>
					<tr>
						<th scope="col" class="c-person">Person</th>
						<th scope="col">Company</th>
						<th scope="col" class="c-fit">Fit</th>
						<th scope="col" class="c-when">Said</th>
						<th scope="col" class="c-src">Where</th>
					</tr>
				</thead>
				<tbody>
					{#each matched as person (person.id)}
						<tr onclick={() => (window.location.href = `/companies/${person.companyId}?line=${encodeURIComponent(person.serviceLine)}`)}>
							<td class="c-person">
								<span class="p-name">{person.name}</span>
								<span class="sub dim">{person.role}</span>
							</td>
							<td>
								<span class="co-line">
									<CompanyMark name={person.company} domain={person.domain} />
									<span class="co">{person.company}</span>
								</span>
							</td>
							<td class="c-fit">
								<span class="fitval dsp" data-tone={fitTone(person.probability, thresholds)}>
									{fitWord(person.probability, thresholds)}
								</span>
							</td>
							<td class="c-when num">{person.publishedAt ? date(person.publishedAt) : '—'}</td>
							<td class="c-src">
								{#if safeHref(person.sourceUrl)}
									<a
										class="link"
										href={safeHref(person.sourceUrl)}
										target="_blank"
										rel="noreferrer noopener"
										onclick={(event) => event.stopPropagation()}
									>
										{host(person.sourceUrl)}
									</a>
								{:else}
									<span class="dim">no link on file</span>
								{/if}
								<span class="sub dim">{person.publisher}</span>
							</td>
						</tr>
					{/each}
				</tbody>
			</table>
		{/if}
	</section>
</div>

<style>
	.topbar .metrics {
		margin-right: var(--spacing-4);
	}

	.toolbar .spacer {
		margin-left: auto;
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		flex: 0 1 280px;
		padding-left: var(--spacing-2);
		border: 1px solid var(--color-rule-strong);
		border-radius: 2px;
		background: var(--color-surface);
		color: var(--color-ink-3);
	}

	.search .input {
		border: 0;
		background: transparent;
		padding-left: 0;
		box-shadow: none;
	}

	.pane {
		position: relative;
		flex: 1;
		min-height: 0;
		background: var(--color-surface);
	}

	tbody tr {
		cursor: pointer;
	}

	tbody tr:hover {
		background: var(--color-paper);
	}

	.c-person {
		width: 26%;
		vertical-align: top;
	}

	.c-fit {
		width: 96px;
		vertical-align: top;
	}

	.c-when {
		width: 110px;
		color: var(--color-ink-3);
	}

	.c-src {
		width: 200px;
	}

	.p-name {
		display: block;
		font-family: var(--font-display);
		font-size: var(--text-lg);
		font-weight: 500;
		line-height: 1.2;
	}

	.co-line {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		min-width: 0;
	}

	.co {
		font-size: var(--text-md);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.fitval {
		display: block;
		font-size: var(--text-lg);
		line-height: 1.2;
		color: var(--color-ink-2);
	}

	.fitval[data-tone='strong'] {
		color: var(--color-orange-ink);
	}

	.fitval[data-tone='weak'] {
		color: var(--color-ink-3);
	}

	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: var(--spacing-2);
		height: 100%;
		padding: var(--spacing-5);
		text-align: center;
		color: var(--color-ink-3);
	}
</style>
