<script lang="ts">
	import NotificationBell from '$lib/components/NotificationBell.svelte';
	import {
		enrichPeople,
		extractPeople,
		fetchEnrichmentProvider,
		fetchPeople,
		movePerson,
		type PersonRow,
		type PersonSignal
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
	import Tag from '$lib/components/Tag.svelte';

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

	function signalsOf(person: PersonRow): PersonSignal[] {
		return person.signals ?? [];
	}

	function strongest(person: PersonRow): PersonSignal | null {
		return signalsOf(person)[0] ?? null;
	}

	const STRENGTH_WORD: Record<string, string> = { STRONG: 'Strong', MEDIUM: 'Medium', SMALL: 'Small' };

	/** The palette reserves orange for the score and oxblood for rejection, so a
	 * strength is said in weight and not in a colour that already means something. */
	const STRENGTH_VARIANT: Record<string, 'default' | 'wash' | 'bare'> = {
		STRONG: 'default',
		MEDIUM: 'wash',
		SMALL: 'bare'
	};

	function strengthWord(strength: string | undefined): string {
		const key = (strength ?? '').toUpperCase();
		return STRENGTH_WORD[key] ?? key;
	}

	function strengthVariant(strength: string | undefined): 'default' | 'wash' | 'bare' {
		return STRENGTH_VARIANT[(strength ?? '').toUpperCase()] ?? 'bare';
	}

	const KIND_WORD: Record<string, string> = {
		BLOG: 'Blog post',
		X_POST: 'Post on X',
		LINKEDIN_POST: 'LinkedIn post',
		CONFERENCE: 'Conference',
		PRESS: 'Press',
		PODCAST: 'Podcast',
		NOTE: 'Note'
	};

	function kindWord(kind: string | undefined): string {
		const key = (kind ?? '').toUpperCase();
		return KIND_WORD[key] ?? key;
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
		const all = session.allLines;
		if (!line) return;
		// Asking for one line at a time and joining them, the same union the board
		// makes: the people endpoint has no "every line" form, and the subtitle
		// above this list names the scope, so the list has to match it.
		const lines = all ? (workspace.lines.length > 0 ? workspace.lines : [line]) : [line];
		let cancelled = false;
		loading = true;
		Promise.all(lines.map((each) => fetchPeople(each).catch(() => [])))
			.then((lists) => {
				if (cancelled) return;
				const seen = new Set<string>();
				rows = lists.flat().filter((person) => (seen.has(person.id) ? false : seen.add(person.id)));
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
				['name', 'role', 'company', 'domain', 'fit', 'signal', 'signal_strength', 'signal_from', 'seen_in'],
				matched.map((row) => {
					const top = strongest(row);
					return [
						row.name,
						row.role,
						row.company,
						row.domain ?? '',
						row.probability.toFixed(2),
						top?.text ?? '',
						top ? strengthWord(top.strength) : '',
						top?.publisher ?? '',
						row.publishedAt ?? ''
					];
				})
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
				{session.scopeLabel()} · signals from people. Who to reach, what they have said publicly, and how
				strong the evidence behind it is.
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
			<NotificationBell />
		</div>
	</header>

	<div class="toolbar">
		<label class="search-field">
			<span class="sr-only">Filter by name, role or company</span>
			<Icon name="search" size={14} />
			<input class="input input-sm" type="search" placeholder="Name, role, company" bind:value={query} />
		</label>
	</div>

	<div class="toolbar">
		<div class="tabs" role="tablist" aria-label="Which people">
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
				{:else if query.trim()}
					<p>Nobody matches “{query}”.</p>
				{:else}
					<!-- Nobody was filtered out by a search, so it is the stage that did
					     it — the message used to read "Nobody matches ""." -->
					<p>Nobody in this list is at that stage yet.</p>
				{/if}
			</div>
		{:else}
			<table class="tbl">
				<thead>
					<tr>
						<th scope="col" class="c-rank">#</th>
						<th scope="col" class="c-person">Person</th>
						<th scope="col" class="c-stage">Stage</th>
						<th scope="col" class="c-fit">Fit</th>
						<th scope="col" class="c-when">Said</th>
						<th scope="col" class="c-src">Where</th>
						<th scope="col" class="c-signal">Signals</th>
					</tr>
				</thead>
				<tbody>
					{#each matched as person, index (person.id)}
						{@const top = strongest(person)}
						<tr onclick={() => (window.location.href = `/companies/${person.companyId}?line=${encodeURIComponent(person.serviceLine)}`)}>
							<!-- Position in the list as it is shown, the same as the board's
							     rank column: the list is already ordered, and a number makes
							     "the fourth one down" something two people can both mean. -->
							<td class="num c-rank">{index + 1}</td>
							<td class="c-person">
								<span class="p-name">{person.name}</span>
								<span class="co-line">
									<CompanyMark name={person.company} domain={person.domain} />
									<span class="co">{person.company}</span>
								</span>
								<span class="sub dim">{person.role}</span>
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
							<td class="c-signal">
								{#if top}
									<span class="sig-text">{top.text}</span>
									<span class="sig-meta">
										<Tag variant={strengthVariant(top.strength)}>{strengthWord(top.strength)}</Tag>
										<span class="sub dim">
											{kindWord(top.kind)}{#if signalsOf(person).length > 1} · {signalsOf(person).length} signals{/if}{#if top.publishedAt} · {date(top.publishedAt)}{/if}
										</span>
									</span>
								{:else}
									<span class="dim">Nothing said yet</span>
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
	 * The table fills the pane. Capping it stopped the headings spreading, but it
	 * left the rest of the pane as a band of nothing, which reads as a table that
	 * failed to load rather than one that is deliberately narrow. Only the person
	 * column is left unsized, so it takes the room the others do not use and the
	 * headings stay where they are however wide the window gets.
	 */
	/*
	 * Centred, and declared once for the table rather than once per column.
	 *
	 * Each column used to carry its own `vertical-align: top`, which is correct
	 * until somebody adds a column and does not know to copy it — and it had
	 * already happened: the headers for Said and Where never got theirs and fell
	 * back to the browser's default of `middle`, sitting six pixels below the
	 * other five. One rule for the whole table is what made that class of bug
	 * impossible; the value in it is now `middle`, so a row reads on its centre
	 * line the way the board does.
	 */
	.tbl th,
	.tbl td {
		vertical-align: middle;
	}

	/* The same shape as the board's rank column, so the two lists read alike. */
	.c-rank {
		width: 42px;
		font-size: var(--text-base);
		color: var(--color-ink-3);
	}

	.c-fit {
		width: 96px;
	}

	.c-stage {
		width: 190px;
	}

	.p-stage {
		display: inline-block;
		font-size: var(--text-micro);
		font-weight: 700;
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

	.c-signal {
		width: 340px;
	}

	.sig-text {
		display: block;
		font-size: var(--text-base);
		line-height: 1.4;
		color: var(--color-ink-2);
	}

	.sig-meta {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		margin-top: 4px;
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
		margin-top: 2px;
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

	/*
	 * The whole pane, centred. The shared `.empty` is a small message inside a
	 * panel and caps itself at 60ch; this rule overrode its alignment, padding and
	 * colour but not that cap, so the box stayed 486px wide in a 1368px pane and
	 * centred its text on 475 instead of on the middle of the screen.
	 */
	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: var(--spacing-2);
		height: 100%;
		max-width: none;
		padding: var(--spacing-5);
		text-align: center;
		color: var(--color-ink-3);
	}

	/*
	 * Columns drop in a fixed order rather than squeezing, and the signal is the
	 * one that must survive to a projector: the document a person was read out of
	 * and the date it was published are on the signal itself.
	 */
	@media (max-width: 1180px) {
		.c-when,
		.c-src {
			display: none;
		}
	}
</style>
