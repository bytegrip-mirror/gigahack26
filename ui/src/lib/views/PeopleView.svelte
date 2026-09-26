<script lang="ts">
	import {
		enrichPeople,
		extractPeople,
		fetchEnrichmentProvider,
		fetchPeople,
		movePerson,
		setPersonEmail,
		type PersonRow
	} from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { age, date, fitTone, fitWord, host, safeHref } from '$lib/format';
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
	let provider = $state<{ provider: string; available: boolean } | null>(null);
	let stage = $state('NEW');
	let busyOn = $state<string | null>(null);
	let mailFor = $state<{ id: string; value: string } | null>(null);

	async function saveEmail(id: string, value: string) {
		busyOn = id;
		try {
			await setPersonEmail(id, value);
			await load();
			mailFor = null;
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busyOn = null;
		}
	}

	const STAGES = [
		{ id: 'NEW', label: 'To contact' },
		{ id: '' , label: 'Anyone' },
		{ id: 'CONTACTED', label: 'Contacted' },
		{ id: 'REPLIED', label: 'Replied' },
		{ id: 'QUALIFIED', label: 'Qualified' },
		{ id: 'REJECTED', label: 'Rejected' }
	];

	const NEXT: Record<string, string> = {
		NEW: 'CONTACTED',
		CONTACTED: 'REPLIED',
		REPLIED: 'QUALIFIED'
	};

	async function advance(person: PersonRow) {
		const next = NEXT[person.status];
		if (!next) return;
		busyOn = person.id;
		try {
			await movePerson(person.id, next);
			await load();
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busyOn = null;
		}
	}

	async function setStage(person: PersonRow, status: string) {
		busyOn = person.id;
		try {
			await movePerson(person.id, status);
			await load();
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busyOn = null;
		}
	}

	function daysSince(iso: string | null): number | null {
		return iso ? Math.max(0, (Date.now() - new Date(iso).getTime()) / 86_400_000) : null;
	}

	const serviceLine = $derived(session.serviceLine);
	const thresholds = $derived({
		watch: workspace.meta?.scoring.watchThreshold ?? 0.28,
		nurture: workspace.meta?.scoring.nurtureThreshold ?? 0.45,
		priority: workspace.meta?.scoring.priorityThreshold ?? 0.55
	});

	let ticket = 0;

	async function load() {
		if (!serviceLine) return;
		const mine = ++ticket;
		const result = await fetchPeople(serviceLine);
		if (mine !== ticket) return;
		rows = result;
	}

	$effect(() => {
		fetchEnrichmentProvider()
			.then((result) => (provider = result))
			.catch(() => (provider = null));
	});

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
		const list = (needle
			? rows.filter((row) => `${row.name} ${row.role} ${row.company}`.toLowerCase().includes(needle))
			: [...rows]
		).filter((row) => stage === '' || row.status === stage);
		list.sort((a, b) => {
			if (sort === 'name') return a.name.localeCompare(b.name);
			if (sort === 'role') return a.role.localeCompare(b.role) || a.name.localeCompare(b.name);
			return b.probability - a.probability || a.company.localeCompare(b.company) || a.name.localeCompare(b.name);
		});
		return list;
	});

	const companies = $derived(new Set(rows.map((row) => row.companyId)).size);

	async function runEnrich() {
		busy = true;
		try {
			const result = await enrichPeople(serviceLine, 25);
			await load();
			toasts.push(
				result.enriched === 0
					? `Nothing new came back from ${result.provider}.`
					: `${result.provider} filled in ${result.enriched} more.`,
				result.enriched === 0 ? 'warn' : 'success'
			);
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = false;
		}
	}

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
		},
		{
			id: 'provider',
			label: provider?.available
				? `Profiles from ${provider.provider}`
				: 'No people-data provider configured',
			icon: 'shield' as const,
			disabled: true,
			separatorBefore: true
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
			<button
				class="btn"
				onclick={runEnrich}
				disabled={busy || !provider?.available}
				title={provider?.available
					? `Ask ${provider.provider} for the profiles, headlines and links behind these names`
					: 'No licensed people-data provider is configured, so there is nothing to ask. Set ENRICHMENT_PROVIDER and ENRICHMENT_API_KEY to turn this on.'}
			>
				Fill in profiles
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
		<div class="seg" role="tablist" aria-label="Which people">
			{#each STAGES as option (option.id)}
				<button role="tab" aria-selected={stage === option.id} onclick={() => (stage = option.id)}>
					{option.label}
				</button>
			{/each}
		</div>
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
						<th scope="col" class="c-stage">Stage</th>
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
								{#if mailFor?.id === person.id}
									<span class="mail-edit" role="presentation" onclick={(event) => event.stopPropagation()}>
										<input
											class="input input-sm"
											bind:value={mailFor.value}
											placeholder="name@company.com"
											onkeydown={(event) => {
												if (event.key === 'Enter') saveEmail(person.id, mailFor?.value ?? '');
												if (event.key === 'Escape') mailFor = null;
											}}
										/>
										<button class="btn btn-sm btn-ink" disabled={busyOn === person.id} onclick={() => saveEmail(person.id, mailFor?.value ?? '')}>
											Save
										</button>
									</span>
								{:else if person.email}
									<button
										class="mail"
										title="Click to correct it"
										onclick={(event) => {
											event.stopPropagation();
											mailFor = { id: person.id, value: person.email ?? '' };
										}}
									>
										{person.email}
									</button>
								{:else}
									<button
										class="mail-add"
										title="Add an address for this person"
										onclick={(event) => {
											event.stopPropagation();
											mailFor = { id: person.id, value: '' };
										}}
									>
										no address
									</button>
								{/if}
							</td>
							<td class="c-co">
								<span class="co-line">
									<CompanyMark name={person.company} domain={person.domain} />
									<span class="co">{person.company}</span>
								</span>
							</td>
							<td class="c-stage" onclick={(event) => event.stopPropagation()}>
								{#if person.status === 'NEW'}
									<button
										class="btn btn-sm"
										disabled={busyOn === person.id}
										title={`Mark ${person.name} contacted`}
										onclick={() => advance(person)}
									>
										<Icon name="mail" size={13} /> To contact
									</button>
								{:else}
									<span class="p-stage" data-stage={person.status}>{person.statusLabel}</span>
									<span class="sub dim">
										{#if person.contactCount > 0}
											{person.contactCount}×{#if person.lastContactedAt} · {age(daysSince(person.lastContactedAt) ?? 0)}{/if}
										{/if}
									</span>
									{#if NEXT[person.status]}
										<button
											class="btn btn-sm btn-quiet"
											disabled={busyOn === person.id}
											onclick={() => advance(person)}
										>
											{STAGES.find((entry) => entry.id === NEXT[person.status])?.label}
											<Icon name="chevronRight" size={12} />
										</button>
									{/if}
								{/if}
							</td>
							<td class="c-fit">
								<span class="fitval dsp" data-tone={fitTone(person.probability, thresholds)}>
									{fitWord(person.probability, thresholds)}
								</span>
							</td>
							<td class="c-when num">{person.publishedAt ? date(person.publishedAt) : '—'}</td>
							<td class="c-src">
								{#if person.enrichedAt && !person.linkedinUrl}
									<span class="dim">no profile found</span>
								{:else if safeHref(person.linkedinUrl)}
									<a
										class="link"
										href={safeHref(person.linkedinUrl)}
										target="_blank"
										rel="noreferrer noopener"
										onclick={(event) => event.stopPropagation()}
									>
										{person.headline ?? 'profile'}
									</a>
								{:else if safeHref(person.sourceUrl)}
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
									<span class="dim">{person.publisher}</span>
								{/if}
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

	/*
	 * The table stretched to the whole window and the columns spread with it, so
	 * the headings sat hundreds of pixels apart and stopped reading as one row of
	 * labels over their own data. It stops at the width the columns actually need
	 * and the rest of the pane stays empty.
	 */
	.pane table.tbl {
		max-width: 1080px;
	}

	.c-person {
		width: 30%;
		vertical-align: top;
	}

	.c-fit {
		width: 96px;
		vertical-align: top;
	}

	.c-co {
		width: 260px;
	}

	.c-stage {
		width: 190px;
		vertical-align: top;
	}

	.p-stage {
		display: inline-block;
		font-size: var(--text-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		padding: 1px 5px;
		border: 1px solid var(--color-rule-strong);
		color: var(--color-ink-3);
	}

	.p-stage[data-stage='QUALIFIED'] {
		border-color: var(--color-accent);
		color: var(--color-accent);
	}

	.p-stage[data-stage='REJECTED'] {
		border-color: var(--color-oxblood-line);
		color: var(--color-oxblood);
	}

	.p-stage[data-stage='CONTACTED'],
	.p-stage[data-stage='REPLIED'] {
		border-color: var(--color-orange-line);
		color: var(--color-orange-ink);
	}

	.c-stage .btn {
		display: inline-flex;
		margin-top: 4px;
	}

	.toolbar .spacer {
		margin-left: auto;
	}

	.c-when {
		width: 120px;
		color: var(--color-ink-3);
	}

	.c-src {
		width: 190px;
	}

	.p-name {
		display: block;
		font-family: var(--font-display);
		font-size: var(--text-lg);
		font-weight: 500;
		line-height: 1.2;
	}

	.mail {
		display: block;
		margin-top: 2px;
		font-family: var(--font-ui);
		font-size: var(--text-sm);
		color: var(--color-ink-2);
		text-decoration: underline;
		text-underline-offset: 2px;
		text-decoration-color: var(--color-rule-strong);
		cursor: pointer;
	}

	.mail:hover {
		text-decoration-color: var(--color-orange);
		color: var(--color-orange-ink);
	}

	.mail-add {
		display: block;
		margin-top: 2px;
		font-size: var(--text-micro);
		letter-spacing: 0.04em;
		color: var(--color-ink-4);
		cursor: pointer;
	}

	.mail-add:hover {
		color: var(--color-orange-ink);
	}

	.mail-edit {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		margin-top: var(--spacing-1);
	}

	.mail-edit .input {
		max-width: 200px;
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
