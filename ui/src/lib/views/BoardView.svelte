<script lang="ts">
	import { untrack } from 'svelte';
	import { pushState } from '$app/navigation';
	import {
		fetchBoard,
		recordLead,
		rescore,
		type BoardRow,
		type Chip,
		type Engine,
		type Move,
		type Polarity,
		type Rescore,
		type WeightTier
	} from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { ageShort, duration, fit, shortNumber, signed, theme } from '$lib/format';
	import { regionLabel } from '$lib/regions';
	import { copy, csv, save } from '$lib/export';
	import Tag from '$lib/components/Tag.svelte';
	import ScoreMeter from '$lib/components/ScoreMeter.svelte';
	import CaptureSheet from '$lib/components/CaptureSheet.svelte';
	import Scorecard from '$lib/views/Scorecard.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import type { MenuItem } from '$lib/components/menu';
	import Metric from '$lib/components/Metric.svelte';
	import Pager from '$lib/components/Pager.svelte';
	import Th from '$lib/components/Th.svelte';

	let { initialSelected = null }: { initialSelected?: string | null } = $props();

	const FILTERS = [
		{ id: 'all', label: 'All' },
		{ id: 'hot', label: 'Hot' },
		{ id: 'priority', label: 'Priority' },
		{ id: 'rejected', label: 'Rejected' },
		{ id: 'thin', label: 'Thin' }
	];

	const STAGES = [
		{ id: '', label: 'Any stage' },
		{ id: 'worked', label: 'Worked' },
		{ id: 'untouched', label: 'Untouched' },
		{ id: 'CONTACTED', label: 'Contacted' },
		{ id: 'REPLIED', label: 'Replied' },
		{ id: 'QUALIFIED', label: 'Qualified' },
		{ id: 'REJECTED', label: 'Rejected' }
	];

	const ASKS = [
		'Is the company building automation capability in-house rather than buying it?',
		'Has the company announced a cost-reduction programme with a quantified headcount target?',
		'Is the company hiring process-mining or process-excellence roles?',
		'Has the company appointed a new security leader in the last two quarters?'
	];

	const BAND_ORDER: Record<string, number> = {
		PRIORITY: 0,
		NURTURE: 1,
		WATCH: 2,
		THIN: 3,
		DISQUALIFIED: 4
	};

	const ALL_LINES = '__all__';

	type ScopedRow = BoardRow & { serviceLine: string; fitRank: number };

	const BAND_FILTERS: Record<string, (band: string) => boolean> = {
		all: () => true,
		hot: (band) => band === 'PRIORITY' || band === 'NURTURE',
		priority: (band) => band === 'PRIORITY',
		rejected: (band) => band === 'DISQUALIFIED',
		thin: (band) => band === 'THIN'
	};

	const LEAD_STAGES = [
		{ id: 'CONTACTED', label: 'Contacted' },
		{ id: 'REPLIED', label: 'Replied' },
		{ id: 'QUALIFIED', label: 'Qualified' },
		{ id: 'REJECTED', label: 'Rejected' }
	];

	let rows = $state<ScopedRow[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);

	let filter = $state('all');
	let stage = $state('');
	let query = $state('');
	let selected = $state<string | null>(untrack(() => initialSelected));

	let sort = $state('rank');
	let dir = $state<'asc' | 'desc'>('asc');
	let size = $state(50);
	let page = $state(1);
	let picked = $state<Set<string>>(new Set());
	let busy = $state(false);

	let askOpen = $state(false);
	let questionText = $state('');
	let weightTier = $state<WeightTier>('HIGH');
	let polarity = $state<Polarity>('POSITIVE');
	let engine = $state<Engine>('local');
	let rescoring = $state(false);
	let elapsed = $state(0);
	let outcome = $state<Rescore | null>(null);

	let capture = $state<{ id: string; focus: { start: number; end: number } | null } | null>(null);

	const meta = $derived(workspace.meta);
	const serviceLine = $derived(session.serviceLine);
	const filtered = $derived(session.region !== 'world' || stage !== '' || query !== '');
	const allLines = $derived(session.allLines);
	const scopeItems = $derived([
		{
			id: ALL_LINES,
			label: 'All service lines',
			hint: String(Object.values(workspace.counts).reduce((sum, count) => sum + count, 0)),
			checked: allLines
		},
		...workspace.lines.map((line) => ({
			id: line,
			label: line,
			hint: workspace.counts[line] === undefined ? '' : String(workspace.counts[line]),
			checked: !allLines && line === serviceLine
		}))
	]);

	const movements = $derived.by(() => {
		const map = new Map<string, Move>();
		outcome?.moves.forEach((move) => map.set(move.companyId, move));
		return map;
	});

	const visible = $derived(rows.filter((row) => (BAND_FILTERS[filter] ?? BAND_FILTERS.all)(row.band)));

	const counts = $derived.by(() => {
		const tally: Record<string, number> = {
			ALL: rows.length,
			PRIORITY: 0,
			NURTURE: 0,
			WATCH: 0,
			THIN: 0,
			DISQUALIFIED: 0
		};
		for (const row of rows) tally[row.band] = (tally[row.band] ?? 0) + 1;
		tally.HOT = tally.PRIORITY + tally.NURTURE;
		return tally;
	});

	const sorted = $derived.by(() => {
		const list = [...visible];
		const sign = dir === 'asc' ? 1 : -1;
		list.sort((a, b) => {
			switch (sort) {
				case 'name':
					return sign * a.name.localeCompare(b.name);
				case 'fit':
					return sign * (a.probability - b.probability);
				case 'band':
					return sign * ((BAND_ORDER[a.band] ?? 9) - (BAND_ORDER[b.band] ?? 9));
				case 'themes':
					return sign * (a.themeCount - b.themeCount || a.probability - b.probability);
				case 'docs':
					return sign * (a.evidenceCount - b.evidenceCount);
				case 'newest':
					return (
						sign *
						((a.newestSignalAgeDays ?? Number.POSITIVE_INFINITY) -
							(b.newestSignalAgeDays ?? Number.POSITIVE_INFINITY))
					);
				default:
					return sign * (a.fitRank - b.fitRank);
			}
		});
		return list;
	});

	const effective = $derived(size === 0 ? Math.max(sorted.length, 1) : size);
	const pages = $derived(Math.max(1, Math.ceil(sorted.length / effective)));
	const current = $derived(sorted.slice((Math.min(page, pages) - 1) * effective, Math.min(page, pages) * effective));

	async function loadBoard(): Promise<ScopedRow[]> {
		const options = {
			filter: 'all',
			sort: 'score',
			q: query,
			stage,
			region: session.region
		};
		const lines = allLines ? (workspace.lines.length > 0 ? workspace.lines : [serviceLine]) : [serviceLine];
		const results = await Promise.all(
			lines.map((line) => fetchBoard({ serviceLine: line, ...options }).then((list) => ({ line, list })))
		);
		const merged: ScopedRow[] = results.flatMap(({ line, list }) =>
			list.map((row) => ({ ...row, serviceLine: line, fitRank: row.rank }))
		);
		if (allLines) {
			const best = new Map<string, ScopedRow>();
			for (const row of merged) {
				const held = best.get(row.id);
				if (!held || row.probability > held.probability) best.set(row.id, row);
			}
			const list = [...best.values()].sort(
				(a, b) =>
					(a.band === 'DISQUALIFIED' ? 1 : 0) - (b.band === 'DISQUALIFIED' ? 1 : 0) ||
					b.probability - a.probability ||
					a.name.localeCompare(b.name)
			);
			list.forEach((row, index) => (row.fitRank = index + 1));
			return list;
		}
		return merged;
	}

	$effect(() => {
		const line = serviceLine;
		void allLines;
		const search = query;
		const activeStage = stage;
		const activeRegion = session.region;
		if (!line) return;

		let cancelled = false;
		loading = true;
		const timer = setTimeout(
			() => {
				loadBoard()
					.then((result) => {
						if (cancelled) return;
						rows = result;
						loading = false;
						error = null;
						picked = new Set();
					})
					.catch((cause) => {
						if (cancelled) return;
						loading = false;
						error = (cause as Error).message;
					});
			},
			search ? 200 : 0
		);
		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
	});

	$effect(() => {
		void filter;
		void query;
		void stage;
		void session.region;
		void sort;
		void dir;
		void size;
		page = 1;
	});

	$effect(() => {
		if (!rescoring) return;
		const started = performance.now();
		const timer = setInterval(() => (elapsed = performance.now() - started), 90);
		return () => clearInterval(timer);
	});

	function select(id: string | null) {
		selected = id;
		pushState(id ? `/?co=${id}` : '/', {});
	}

	function sortBy(id: string) {
		if (sort === id) {
			dir = dir === 'asc' ? 'desc' : 'asc';
			return;
		}
		sort = id;
		dir = id === 'name' || id === 'rank' ? 'asc' : 'desc';
	}

	function togglePick(id: string) {
		const next = new Set(picked);
		next.has(id) ? next.delete(id) : next.add(id);
		picked = next;
	}

	const allPicked = $derived(current.length > 0 && current.every((row) => picked.has(row.id)));
	const somePicked = $derived(current.some((row) => picked.has(row.id)));

	function lineOf(id: string): string {
		return rows.find((row) => row.id === id)?.serviceLine ?? serviceLine;
	}

	function reasons(row: BoardRow): Chip[] {
		const seen = new Map<string, Chip>();
		for (const chip of row.chips) {
			const held = seen.get(chip.questionKey);
			if (!held || Math.abs(chip.contribution) > Math.abs(held.contribution)) {
				seen.set(chip.questionKey, chip);
			}
		}
		return [...seen.values()]
			.sort((a, b) => Math.abs(b.contribution) - Math.abs(a.contribution))
			.slice(0, 3);
	}

	function partial(node: HTMLInputElement, value: boolean) {
		node.indeterminate = value;
		return {
			update: (next: boolean) => {
				node.indeterminate = next;
			}
		};
	}

	function toggleAll() {
		const next = new Set(picked);
		if (allPicked) current.forEach((row) => next.delete(row.id));
		else current.forEach((row) => next.add(row.id));
		picked = next;
	}

	function clearFilters() {
		query = '';
		stage = '';
		session.setRegion('world');
	}

	async function mark(ids: string[], status: string, label: string) {
		if (ids.length === 0 || !serviceLine) return;
		busy = true;
		const results = await Promise.allSettled(
			ids.map((companyId) => recordLead({ companyId, serviceLine: lineOf(companyId), status }))
		);
		busy = false;
		const done = results.filter((result) => result.status === 'fulfilled').length;
		toasts.push(
			done === ids.length
				? `Marked ${done} ${done === 1 ? 'company' : 'companies'} ${label}.`
				: `Marked ${done} of ${ids.length} ${label}. The rest failed.`,
			done === ids.length ? 'success' : 'warn'
		);
		rows = await loadBoard();
		workspace.loadCounts();
	}

	function exportRows(list: ScopedRow[], name: string) {
		save(
			name,
			csv(
				[
					'rank',
					'service_line',
					'company',
					'domain',
					'country',
					'industry',
					'size',
					'fit',
					'band',
					'themes',
					'documents',
					'newest_signal',
					'strongest_quote',
					'publisher',
					'lead_stage'
				],
				list.map((row) => [
					allLines ? row.fitRank : row.rank,
					row.serviceLine,
					row.name,
					row.domain,
					row.country,
					row.industry,
					row.sizeBand,
					fit(row.probability),
					row.band,
					row.themes.join(' | '),
					row.evidenceCount,
					ageShort(row.newestSignalAgeDays),
					row.strongest?.quote ?? '',
					row.strongest?.publisher ?? '',
					row.leadStatus ?? 'NEW'
				])
			)
		);
		toasts.push(`Exported ${list.length} rows.`, 'success');
	}

	async function runAsk() {
		const text = questionText.trim();
		if (text.length < 12 || rescoring) return;
		rescoring = true;
		elapsed = 0;
		outcome = null;
		try {
			const result = await rescore({
				serviceLine,
				question: text,
				questionId: null,
				engine,
				tier: weightTier,
				polarity
			});
			outcome = result;
			rows = allLines
				? await loadBoard()
				: result.board.map((row) => ({ ...row, serviceLine, fitRank: row.rank }));
			elapsed = result.tookMs;
			if (filter !== 'all') filter = 'all';
			toasts.push(
				`${result.movedRows} rows moved in ${duration(result.tookMs)}. The question is now policy.`,
				'success'
			);
			workspace.reload();
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			rescoring = false;
		}
	}

	async function copyLink() {
		const ok = await copy(window.location.href);
		toasts.push(ok ? 'Link copied.' : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
	}

	const secondary: MenuItem[] = $derived([
		{ id: 'export-all', label: 'Export the board as CSV', icon: 'download', hint: shortNumber(sorted.length) },
		{ id: 'copy-link', label: 'Copy a link to this view', icon: 'link' },
		{
			id: 'clear',
			label: 'Clear filters',
			icon: 'filter',
			disabled: !filtered,
			separatorBefore: true
		},
		{ id: 'rescore', label: 'Re-score on the local engine', icon: 'refresh' }
	]);

	async function runSecondary(id: string) {
		if (id === 'export-all')
			exportRows(sorted, `board-${(allLines ? 'all' : serviceLine).toLowerCase().replace(/\s+/g, '-')}.csv`);
		else if (id === 'copy-link') copyLink();
		else if (id === 'clear') clearFilters();
		else if (id === 'rescore') {
			try {
				const result = await rescore({ serviceLine, engine: 'local' });
				rows = allLines
					? await loadBoard()
					: result.board.map((row) => ({ ...row, serviceLine, fitRank: row.rank }));
				toasts.push(`Re-scored ${shortNumber(result.documentsScanned)} documents in ${duration(result.tookMs)}.`, 'success');
				workspace.reload();
			} catch (cause) {
				toasts.push((cause as Error).message, 'error');
			}
		}
	}

	function rowMenu(row: BoardRow): MenuItem[] {
		return [
			{ id: 'open', label: 'Open scorecard', icon: 'eye' },
			row.strongest
				? { id: 'capture', label: 'Open the quoted capture', icon: 'external' }
				: { id: 'capture', label: 'No quoted capture', icon: 'external', disabled: true },
			{ id: 'link', label: 'Copy link to this company', icon: 'link' },
			{ id: 'export', label: 'Export this row', icon: 'download' },
			...LEAD_STAGES.map((entry, index) => ({
				id: `lead-${entry.id}`,
				label: `Mark ${entry.label.toLowerCase()}`,
				icon: row.leadStatus === entry.id ? ('check' as const) : undefined,
				separatorBefore: index === 0
			}))
		];
	}

	async function runRow(row: ScopedRow, id: string) {
		if (id === 'open') select(row.id);
		else if (id === 'capture' && row.strongest) capture = { id: row.strongest.evidenceId, focus: { start: row.strongest.spanStart, end: row.strongest.spanEnd } };
		else if (id === 'link') {
			const ok = await copy(`${window.location.origin}/?co=${row.id}`);
			toasts.push(ok ? `Link to ${row.name} copied.` : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
		} else if (id === 'export') exportRows([row], `${row.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}.csv`);
		else if (id.startsWith('lead-')) {
			const status = id.slice(5);
			const label = LEAD_STAGES.find((entry) => entry.id === status)?.label.toLowerCase() ?? '';
			await mark([row.id], status, label);
		}
	}

	const bulkItems: MenuItem[] = LEAD_STAGES.map((entry, index) => ({
		id: entry.id,
		label: `Mark ${entry.label.toLowerCase()}`,
		icon: 'check',
		separatorBefore: index === 0
	}));
</script>

<div class="view" class:split={selected !== null}>
	<div class="main">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Board</h1>
			<p class="sub num">
				{shortNumber(rows.length)} companies · {regionLabel(meta?.regions ?? [], session.region)}{#if stage}
					· {STAGES.find((s) => s.id === stage)?.label}{/if}
			</p>
		</div>

		{#if meta}
			<div class="metrics">
				<Metric value={shortNumber(counts.PRIORITY ?? 0)} label="Priority" tone="hot" title="Companies above the Priority line, in view" />
				<Metric value={shortNumber(counts.HOT ?? 0)} label="Hot" title="Priority and Nurture together, in view" />
				<Metric value={shortNumber(meta.captures)} label="Captures" title="Stored documents the board was scored against" />
			</div>
		{/if}

		<div class="topbar-tools">
			<button class="btn btn-primary" onclick={() => (askOpen = !askOpen)} aria-expanded={askOpen}>
				<Icon name="target" size={15} />
				{askOpen ? 'Close' : 'Ask a question'}
			</button>
			<Menu items={secondary} onselect={runSecondary} label="More board actions" />
		</div>
	</header>

	{#if askOpen || outcome}
		<div class="ask">
			{#if !outcome}
				<div class="ask-input">
					<label class="label" for="ask">Question the corpus has not been scored against</label>
					<textarea
						id="ask"
						class="textarea"
						rows="2"
						bind:value={questionText}
						placeholder="Is the company consolidating shared service centres into a captive operation?"
					></textarea>
				</div>

				<div class="ask-opts">
					<label class="field">
						<span class="label">Weight</span>
						<select class="select select-sm" bind:value={weightTier}>
							<option value="HIGH">High · 1.5</option>
							<option value="MEDIUM">Medium · 0.7</option>
							<option value="LOW">Low · 0.3</option>
						</select>
					</label>
					<label class="field">
						<span class="label">Polarity</span>
						<select class="select select-sm" bind:value={polarity}>
							<option value="POSITIVE">Positive</option>
							<option value="NEGATIVE">Counter</option>
							<option value="DISQUALIFYING">Disqualifying</option>
						</select>
					</label>
					<label class="field">
						<span class="label">Engine</span>
						<select class="select select-sm" bind:value={engine}>
							<option value="local">Local rubric</option>
							<option value="deepseek" disabled={!meta?.languageModelAvailable}>
								Model{meta?.languageModelAvailable ? '' : ' — off'}
							</option>
						</select>
					</label>
					<Menu
						text="Examples"
						size="sm"
						align="start"
						label="Example questions"
						items={ASKS.map((ask, index) => ({ id: String(index), label: ask }))}
						onselect={(id) => (questionText = ASKS[Number(id)])}
					/>
					<button
						class="btn btn-primary"
						onclick={runAsk}
						disabled={rescoring || questionText.trim().length < 12}
					>
						{#if rescoring}<span class="spinner"></span>{/if}
						{rescoring ? duration(elapsed) : 'Score the board'}
					</button>
				</div>

				{#if rescoring}
					<p class="label pulsing ask-note">
						Classifying {shortNumber(meta?.captures ?? 0)} documents
					</p>
				{:else}
					<p class="sub dim ask-note">Scored on the corpus as it stands. No model call is needed.</p>
				{/if}
			{:else}
				<div class="result">
					<div class="metrics">
						<Metric value={outcome.movedRows} label="Rows moved" tone="hot" />
						<Metric value={duration(outcome.tookMs)} label="Elapsed" />
						<Metric value={shortNumber(outcome.documentsScanned)} label="Documents" />
						<Metric value={shortNumber(outcome.signalsProduced)} label="Signals" />
					</div>
					<p class="result-q dsp">“{outcome.question}”</p>
					<button class="btn btn-sm" onclick={() => { outcome = null; questionText = ''; }}>
						Reset the board
					</button>
				</div>
			{/if}
		</div>
	{/if}

	{#if picked.size > 0}
		<div class="bulkbar">
			<span class="count"><span class="num">{picked.size}</span> selected</span>
			<span class="bulk-hint">·</span>
			<Menu items={bulkItems} onselect={(id) => mark([...picked], id, LEAD_STAGES.find((s) => s.id === id)?.label.toLowerCase() ?? '')} text="Set stage" size="sm" align="start" label="Set lead stage for the selection" />
			<button class="btn btn-sm" onclick={() => exportRows(sorted.filter((row) => picked.has(row.id)), 'selection.csv')}>
				<Icon name="download" size={14} /> Export
			</button>
			<button
				class="btn btn-sm"
				onclick={async () => {
					const ok = await copy([...picked].map((id) => `${window.location.origin}/?co=${id}`).join('\n'));
					toasts.push(ok ? `Copied ${picked.size} links.` : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
				}}
			>
				<Icon name="link" size={14} /> Copy links
			</button>
			{#if busy}<span class="spinner"></span>{/if}
			<span class="spacer"></span>
			<button class="icon-btn" aria-label="Clear the selection" title="Clear the selection" onclick={() => (picked = new Set())}>
				<Icon name="close" size={16} />
			</button>
		</div>
	{:else}
		<div class="toolbar">
			<Menu
				text={session.scopeLabel()}
				icon="layers"
				align="start"
				searchable
				label="Service line"
				title="What the board is ranked for"
				items={scopeItems}
				onselect={(line) => (line === ALL_LINES ? session.setAll() : workspace.select(line))}
			/>
			{#if workspace.switching}<span class="spinner"></span>{/if}
			<span class="sep"></span>

			<div class="seg" role="tablist" aria-label="Filter">
				{#each FILTERS as option (option.id)}
					<button role="tab" aria-selected={filter === option.id} onclick={() => (filter = option.id)}>
						{option.label}
					</button>
				{/each}
			</div>

			<label class="search">
				<span class="sr-only">Filter by name, country or industry</span>
				<Icon name="search" size={14} />
				<input class="input input-sm" placeholder="Name, country, industry" bind:value={query} />
			</label>

			<span class="spacer"></span>

			<label class="sr-only" for="stage">Lead stage</label>
			<select id="stage" class="select select-sm" bind:value={stage}>
				{#each STAGES as option (option.id)}
					<option value={option.id}>{option.label}</option>
				{/each}
			</select>

			{#if filtered}
				<button class="btn btn-sm btn-quiet" onclick={clearFilters}>Clear</button>
			{/if}
		</div>
	{/if}

		<section class="list scroll" aria-label="Ranked companies">
			<LoadingVeil show={loading} label="Scoring the board" />
			{#if error}
				<p class="note warn"><Icon name="alert" size={15} /> {error}</p>
			{:else if visible.length === 0 && !loading}
				<div class="empty">
					<Icon name="inbox" size={22} />
					<p>Nothing matches this filter. Widen the region, clear the stage, or ask a different question.</p>
				</div>
			{:else}
				<table class="grid">
					<thead>
						<tr>
							<th class="c-chk" scope="col">
								<span class="chk">
									<input
										type="checkbox"
										checked={allPicked}
										use:partial={!allPicked && somePicked}
										aria-label="Select every company on this page"
										onchange={toggleAll}
									/>
								</span>
							</th>
							<Th id="rank" label="#" sort={sort} {dir} onsort={sortBy} align="right" width="58px" title="Fit rank — the order the ledger produces" />
							<Th id="name" label="Company" sort={sort} {dir} onsort={sortBy} width="21%" />
							<Th id="fit" label="Fit" sort={sort} {dir} onsort={sortBy} align="right" width="128px" title="Probability this company is worth calling" />
							<Th id="band" label="Band" sort={sort} {dir} onsort={sortBy} align="center" width="96px" />
							<th class="c-sig" scope="col">Top evidence</th>
							<Th id="themes" label="Themes" sort={sort} {dir} onsort={sortBy} width="142px" className="c-th" />
							<Th id="docs" label="Docs" sort={sort} {dir} onsort={sortBy} align="right" width="56px" className="c-doc" />
							<Th id="newest" label="Newest" sort={sort} {dir} onsort={sortBy} align="right" width="66px" className="c-age" />
							<th class="c-act" scope="col"><span class="sr-only">Actions</span></th>
						</tr>
					</thead>
					<tbody>
						{#each current as row (row.id)}
							{@const move = movements.get(row.id)}
							<tr
								class:active={selected === row.id}
								class:rejected={row.band === 'DISQUALIFIED'}
								class:pickedrow={picked.has(row.id)}
								onclick={() => select(row.id)}
							>
								<td class="c-chk" onclick={(event) => event.stopPropagation()}>
									<span class="chk">
										<input
											type="checkbox"
											checked={picked.has(row.id)}
											aria-label={`Select ${row.name}`}
											onchange={() => togglePick(row.id)}
										/>
									</span>
								</td>
								<td class="num c-rank">
									{allLines ? row.fitRank : row.rank}
									{#if move && move.delta !== 0}
										<span class="delta" data-dir={move.delta > 0 ? 'up' : 'down'}>
											{move.delta > 0 ? '▲' : '▼'}{Math.abs(move.delta)}
										</span>
									{/if}
								</td>
								<td class="c-co">
									<span class="co">{row.name}</span>
									<span class="sub">
										{#if allLines}<span class="line-tag">{row.serviceLine}</span> · {/if}{#if row.domain}<span class="domain">{row.domain}</span> · {/if}{row.country ?? '—'}
										{#if row.industry}· {row.industry}{/if}
										{#if row.sizeBand}· {row.sizeBand}{/if}
									</span>
									{#if row.leadStatus && row.leadStatus !== 'NEW'}
										<span class="stage-tag" data-stage={row.leadStatus}>
											{row.leadStatusLabel}{#if row.contactCount > 1} ×{row.contactCount}{/if}
										</span>
									{/if}
								</td>
								<td class="c-fit">
									<span class="num fitval">{fit(row.probability)}</span>
									{#if meta}
										<ScoreMeter
											probability={row.probability}
											band={row.band}
											baseRate={meta.scoring.baseRate}
											thresholds={{
												watch: meta.scoring.watchThreshold,
												nurture: meta.scoring.nurtureThreshold,
												priority: meta.scoring.priorityThreshold
											}}
										/>
									{/if}
								</td>
								<td class="m">
									<Tag band={row.band} title={row.gateReason ?? row.bandLabel}>{row.bandShort}</Tag>
									{#if row.gateReason}
										<span class="gate" title={row.gateReason}>removed</span>
									{/if}
								</td>
								<td class="c-sig">
									{#if row.chips.length > 0}
										{@const top = reasons(row)}
										<ul class="evidence" class:hot={row.urgency === 'ACT_NOW'}>
											{#each top as chip (chip.questionKey)}
												<li title={`${chip.publisher}${chip.title ? ` · ${chip.title}` : ''}`}>
													<span class="dot" data-polarity={chip.polarity}></span>
													<span class="ev-title">{chip.questionTitle}</span>
													<span class="num ev-amt">{signed(chip.contribution, 2)}</span>
												</li>
											{/each}
										</ul>
										{#if row.signalCount > top.length}
											<span class="sub dim num">{row.signalCount - top.length} more signals</span>
										{/if}
									{:else}
										<span class="sub dim">no signal cleared the floor</span>
									{/if}
								</td>
								<td class="c-th">
									<div class="tags">
										{#each row.themes as name (name)}
											<Tag>{theme(name)}</Tag>
										{/each}
										{#if row.themeCount === 0}
											<span class="sub dim">—</span>
										{/if}
									</div>
									{#if row.themeCount >= 2}
										<span class="corrob" title="Two independent kinds of evidence — what the Priority band requires">corroborated</span>
									{/if}
								</td>
								<td class="num c-doc">
									{row.evidenceCount}
									{#if row.silentDocuments > 0}
										<span class="sub dim" title="Documents read that produced no signal">{row.silentDocuments} silent</span>
									{/if}
								</td>
								<td class="num c-age">{ageShort(row.newestSignalAgeDays)}</td>
								<td class="c-act" onclick={(event) => event.stopPropagation()}>
									<Menu
										items={rowMenu(row)}
										onselect={(id) => runRow(row, id)}
										size="sm"
										label={`Actions for ${row.name}`}
									/>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			{/if}
		</section>

		{#if sorted.length > 0 && !error}
			<Pager total={sorted.length} page={Math.min(page, pages)} {size} onsize={(next) => (size = next)} onpage={(next) => (page = next)} />
		{/if}
	</div>

	{#if selected}
		<aside class="detail" aria-label="Company detail">
			<Scorecard
				companyId={selected}
				serviceLine={lineOf(selected)}
				onclose={() => select(null)}
				oncapture={(id, focus) => (capture = { id, focus })}
			/>
		</aside>
	{/if}
</div>

{#if capture}
	<CaptureSheet evidenceId={capture.id} focus={capture.focus} onclose={() => (capture = null)} />
{/if}

<style>
	.topbar .sub {
		color: var(--ink-3);
	}

	.metrics {
		margin-left: auto;
	}

	.topbar-tools {
		margin-left: var(--sp-4);
	}

	.ask {
		position: relative;
		display: flex;
		align-items: flex-end;
		gap: var(--sp-3);
		padding: var(--sp-3) var(--sp-4);
		background: var(--orange-wash);
		border-bottom: 1px solid var(--orange-line);
		flex: none;
	}

	.ask-input {
		flex: 1;
		min-width: 260px;
		display: flex;
		flex-direction: column;
		gap: var(--sp-1);
	}

	.ask-input .textarea {
		background: var(--surface);
	}

	.ask-opts {
		display: flex;
		align-items: flex-end;
		gap: var(--sp-2);
		flex: none;
	}

	.ask-opts .field .select {
		min-width: 108px;
	}

	.ask-note {
		position: absolute;
		right: var(--sp-4);
		bottom: 3px;
	}

	.result {
		display: flex;
		align-items: center;
		gap: var(--sp-5);
		width: 100%;
	}

	.result .metrics {
		margin-left: 0;
	}

	.result-q {
		flex: 1;
		min-width: 0;
		font-size: var(--fs-lg);
		color: var(--ink-2);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.toolbar .select {
		width: auto;
		min-width: 112px;
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
		width: 268px;
		padding-left: var(--sp-2);
		border: 1px solid var(--rule-strong);
		border-radius: 2px;
		background: var(--surface);
		color: var(--ink-3);
	}

	.search:focus-within {
		border-color: var(--orange);
		box-shadow: 0 0 0 2px var(--orange-wash);
	}

	.search .input {
		border: 0;
		background: transparent;
		box-shadow: none;
		padding-left: 0;
	}

	.search .input:focus {
		box-shadow: none;
	}

	.bulk-hint {
		color: var(--ink-4);
	}

	.view {
		position: relative;
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		grid-template-rows: minmax(0, 1fr);
	}

	.view.split {
		grid-template-columns: minmax(0, 1fr) clamp(440px, 38%, 660px);
	}

	.main {
		display: flex;
		flex-direction: column;
		min-width: 0;
		min-height: 0;
	}

	.view.split .c-th,
	.view.split .c-doc,
	.view.split .c-age,
	.view.split :global(th.c-th),
	.view.split :global(th.c-doc),
	.view.split :global(th.c-age) {
		display: none;
	}

	.view.split .c-sig .evidence li:nth-child(n + 3) {
		display: none;
	}

	.view.split .c-co .sub {
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.list {
		position: relative;
		flex: 1;
		min-height: 0;
		background: var(--surface);
	}

	.detail {
		min-height: 0;
		border-left: 1px solid var(--ink);
		background: var(--surface);
	}

	tbody tr {
		cursor: pointer;
		transition: background var(--ease);
	}

	tbody tr:hover {
		background: var(--paper);
	}

	tbody tr.active {
		background: var(--orange-wash);
		box-shadow: inset 3px 0 0 var(--orange);
	}

	tbody tr.pickedrow {
		background: var(--sunken);
	}

	tbody tr.rejected .co {
		color: var(--ink-3);
	}

	tbody tr.rejected .fitval {
		text-decoration: line-through;
		text-decoration-color: var(--oxblood);
		color: var(--ink-3);
	}

	.c-chk {
		width: 36px;
		padding-right: 0;
	}

	.c-rank {
		font-size: var(--fs-base);
		color: var(--ink-3);
	}

	.c-fit {
		vertical-align: top;
	}

	.c-act {
		width: 36px;
		padding-left: 0;
	}

	.co {
		display: block;
		font-family: var(--font-display);
		font-size: var(--fs-lg);
		font-weight: 500;
		line-height: 1.2;
		letter-spacing: -0.01em;
	}

	.sub {
		display: block;
		font-size: var(--fs-sm);
		color: var(--ink-3);
		line-height: 1.4;
	}

	.delta {
		display: inline-block;
		margin-left: 4px;
		font-size: var(--fs-micro);
	}

	.delta[data-dir='up'] {
		color: var(--accent);
	}

	.delta[data-dir='down'] {
		color: var(--oxblood);
	}

	.fitval {
		display: block;
		font-size: var(--fs-xl);
		font-weight: 500;
		line-height: 1.1;
		margin-bottom: 3px;
	}

	.gate {
		display: block;
		margin-top: 3px;
		font-size: var(--fs-micro);
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--oxblood);
	}

	.evidence {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.evidence li {
		display: flex;
		align-items: baseline;
		gap: var(--sp-1);
		min-width: 0;
	}

	.evidence .dot {
		width: 5px;
		height: 5px;
		flex: none;
		transform: translateY(-1px);
		background: var(--accent);
	}

	.evidence .dot[data-polarity='NEGATIVE'] {
		background: var(--oxblood);
	}

	.evidence .dot[data-polarity='DISQUALIFYING'] {
		background: var(--oxblood);
	}

	.ev-title {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: var(--fs-base);
		color: var(--ink-2);
	}

	.evidence.hot .ev-title {
		color: var(--ink);
	}

	.ev-amt {
		flex: none;
		font-size: var(--fs-micro);
		color: var(--ink-4);
	}

	.tags {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.tags :global(.tag) {
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.line-tag {
		color: var(--orange-ink);
		font-weight: 500;
	}

	.stage-tag {
		display: inline-block;
		margin-top: 3px;
		font-size: var(--fs-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		padding: 1px 5px;
		border: 1px solid var(--rule-strong);
		color: var(--ink-3);
	}

	.stage-tag[data-stage='QUALIFIED'] {
		border-color: var(--accent);
		color: var(--accent);
	}

	.stage-tag[data-stage='REJECTED'] {
		border-color: var(--oxblood-line);
		color: var(--oxblood);
	}

	.stage-tag[data-stage='CONTACTED'],
	.stage-tag[data-stage='REPLIED'] {
		border-color: var(--orange-line);
		color: var(--orange-ink);
	}

	.corrob {
		display: block;
		margin-top: 3px;
		font-size: var(--fs-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--accent);
		white-space: nowrap;
	}

	@media (max-width: 1380px) {
		.c-th,
		:global(th.c-th) {
			display: none;
		}
	}

	@media (max-width: 1180px) {
		.c-doc,
		.c-age,
		:global(th.c-doc),
		:global(th.c-age) {
			display: none;
		}
		.view.split {
			grid-template-columns: minmax(0, 1fr) 400px;
		}
	}

	@media (max-width: 1024px) {
		.metrics {
			display: none;
		}
		.ask {
			flex-wrap: wrap;
		}
		.ask-note {
			position: static;
		}
	}

	@media (max-width: 760px) {
		.topbar {
			flex-wrap: wrap;
			gap: var(--sp-3);
		}
		.topbar-tools {
			margin-left: 0;
		}
		.toolbar {
			flex-wrap: wrap;
		}
		.toolbar .spacer {
			display: none;
		}
		.toolbar .seg {
			flex: 1 1 100%;
			overflow-x: auto;
		}
		.search {
			flex: 1 1 100%;
			order: -1;
		}
		.toolbar .select {
			flex: 1 1 0;
			min-width: 0;
		}
		.list {
			overflow-x: auto;
		}
		table.grid {
			min-width: 700px;
		}
		:global(.pager) {
			padding: var(--sp-2);
			gap: var(--sp-2);
		}
	}

	@media (max-width: 1100px) {
		.view.split {
			grid-template-columns: minmax(0, 1fr);
		}
		.view.split .detail {
			position: absolute;
			top: 0;
			right: 0;
			bottom: 0;
			width: min(560px, 100%);
			z-index: 3;
			border-left: 1px solid var(--ink);
		}
	}
</style>
