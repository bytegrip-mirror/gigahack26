<script lang="ts">
	import { untrack } from 'svelte';
	import { pushState } from '$app/navigation';
	import {
		addBookmark,
		ask,
		fetchBoard,
		fetchBookmarks,
		fetchCompany,
		deriveCues,
		readAsk,
		recordLead,
		removeBookmark,
		rescore,
		type AskOutcome,
		type AskReading,
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
	import { ageShort, duration, fit, precise, shortNumber, signed, theme } from '$lib/format';
	import { regionLabel } from '$lib/regions';
	import { copy, csv, save } from '$lib/export';
	import ScoreMeter from '$lib/components/ScoreMeter.svelte';
	import CaptureSheet from '$lib/components/CaptureSheet.svelte';
	import Scorecard from '$lib/views/Scorecard.svelte';
	import Star from '$lib/components/Star.svelte';
	import View from '$lib/ui/View.svelte';
	import Topbar from '$lib/ui/Topbar.svelte';
	import Toolbar from '$lib/ui/Toolbar.svelte';
	import Bulkbar from '$lib/ui/Bulkbar.svelte';
	import Button from '$lib/ui/Button.svelte';
	import IconButton from '$lib/ui/IconButton.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Checkbox from '$lib/ui/Checkbox.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';
	import Note from '$lib/ui/Note.svelte';
	import Empty from '$lib/ui/Empty.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import type { MenuItem } from '$lib/ui/menu';
	import Metric from '$lib/ui/Metric.svelte';
	import Pager from '$lib/ui/Pager.svelte';
	import Th from '$lib/ui/Th.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import LoadingVeil from '$lib/ui/LoadingVeil.svelte';

	let {
	initialSelected = null,
	initialLine = null
}: { initialSelected?: string | null; initialLine?: string | null } = $props();

	const FILTERS = [
		{ id: 'all', label: 'All' },
		{ id: 'hot', label: 'Hot' },
		{ id: 'priority', label: 'Priority' },
		{ id: 'rejected', label: 'Rejected' },
		{ id: 'thin', label: 'Thin' },
		{ id: 'saved', label: 'Saved' }
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
		saved: () => true,
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
	let engineFilter = $state<'all' | 'model' | 'local'>('all');
	let stage = $state('');
	let query = $state('');
	let selected = $state<string | null>(untrack(() => initialSelected));
	let echoed = untrack(() => initialSelected) ?? null;

	let sort = $state('rank');
	let dir = $state<'asc' | 'desc'>('asc');
	let size = $state(50);
	let page = $state(1);
	let picked = $state<Set<string>>(new Set());
	let saved = $state<Set<string>>(new Set());
	let busy = $state(false);

	let askOpen = $state(false);
	let questionText = $state('');
	let weightTier = $state<WeightTier>('HIGH');
	let polarity = $state<Polarity>('POSITIVE');
	let engine = $state<Engine>('local');
	let rescoring = $state(false);
	let elapsed = $state(0);
	let applied = $state<AskOutcome | null>(null);
	let reading = $state<AskReading | null>(null);
	let askCues = $state<string[]>([]);

	let capture = $state<{ id: string; focus: { start: number; end: number } | null } | null>(null);

	const meta = $derived(workspace.meta);
	const serviceLine = $derived(session.serviceLine);
	const moved = $derived(
		(applied?.moves ?? [])
			.filter((move) => move.direction !== 'flat')
			.sort((a, b) => (a.afterRank ?? 0) - (b.afterRank ?? 0))
			.slice(0, 6)
	);

	const filtered = $derived(session.region !== 'world' || stage !== '' || query !== '' || applied !== null);
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
		applied?.moves?.forEach((move) => map.set(move.companyId, move));
		return map;
	});

	const localEngine = $derived(workspace.meta?.localEngine ?? 'local-rubric-v1');

	const visible = $derived.by(() => {
		const bandWanted = BAND_FILTERS[filter] ?? BAND_FILTERS.all;
		return rows.filter((row) => {
			const passesBand = filter === 'saved' ? saved.has(row.id) : bandWanted(row.band);
			const passesEngine =
				engineFilter === 'all' ||
				(row.engine !== null &&
					(engineFilter === 'model' ? row.engine !== localEngine : row.engine === localEngine));
			return passesBand && passesEngine && (!askIds || askIds.has(row.id));
		});
	});

	const savedCount = $derived(rows.filter((row) => saved.has(row.id)).length);

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
		const consume = (key: string) => {
			const value = sessionStorage.getItem(key);
			if (value !== null) sessionStorage.removeItem(key);
			return value;
		};
		if (consume('gigahack26.openAsk')) askOpen = true;
		const wantedFilter = consume('gigahack26.openFilter');
		if (wantedFilter) filter = wantedFilter;
		const wantedStage = consume('gigahack26.openStage');
		if (wantedStage) stage = wantedStage;
		if (consume('gigahack26.openExport')) {
			void exportLedger(sorted, `ledger-${serviceLine.toLowerCase().replace(/\s+/g, '-')}.csv`);
		}
	});

	$effect(() => {
		const wanted = initialLine;
		if (!wanted || wanted === serviceLine || !workspace.lines.includes(wanted)) return;
		workspace.select(wanted);
	});

	$effect(() => {
		const wanted = initialSelected ?? null;
		if (wanted === echoed) return;
		echoed = wanted;
		selected = wanted;
	});

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

	function loadSaved() {
		fetchBookmarks()
			.then((list) => (saved = new Set(list.map((bookmark) => bookmark.companyId))))
			.catch(() => {});
	}

	$effect(() => {
		void serviceLine;
		loadSaved();
	});

	$effect(() => {
		const text = questionText.trim();
		if (text.length < 4) {
			reading = null;
			askCues = [];
			return;
		}
		let cancelled = false;
		const timer = setTimeout(() => {
			readAsk(text)
				.then((result) => {
					if (!cancelled) reading = result;
				})
				.catch(() => {
					if (!cancelled) reading = null;
				});
			if (text.length >= 10) {
				deriveCues(text)
					.then((list) => {
						if (!cancelled) askCues = list;
					})
					.catch(() => {
						if (!cancelled) askCues = [];
					});
			}
		}, 260);
		return () => {
			cancelled = true;
			clearTimeout(timer);
		};
	});

	$effect(() => {
		if (!rescoring) return;
		const started = performance.now();
		const timer = setInterval(() => (elapsed = performance.now() - started), 90);
		return () => clearInterval(timer);
	});

	function select(id: string | null) {
		selected = id;
		echoed = id;
		pushState(id ? `/?co=${id}&line=${encodeURIComponent(lineOf(id))}` : '/', {});
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

	function gateCause(reason: string): string {
		const [head] = reason.split(' — ');
		return head.replace(/^(Building in-house|Below the size floor|Outside the ICP geography|Outside the ICP industries)$/, (whole) =>
			({ 'Building in-house': 'in-house', 'Below the size floor': 'too small',
				'Outside the ICP geography': 'off-geography', 'Outside the ICP industries': 'off-industry' })[whole] ?? whole
		);
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

	const KIND_LABEL: Record<string, string> = {
		FILTER: 'Read as a filter',
		SIGNAL: 'Read as a signal question',
		FILTER_AND_SIGNAL: 'Filter and classify'
	};

	const KIND_ACTION: Record<string, string> = {
		FILTER: 'Filter the board',
		SIGNAL: 'Score the corpus',
		FILTER_AND_SIGNAL: 'Filter and score'
	};

	const askIds = $derived(applied?.companyIds ? new Set(applied.companyIds) : null);

	function summarise(result: AskOutcome): string {
		if (result.movedRows !== undefined && result.facets.length > 0) {
			return `${result.matchedCompanies} companies match, and ${result.movedRows} rows moved.`;
		}
		if (result.movedRows !== undefined) {
			return `${result.movedRows} rows moved in ${duration(result.tookMs ?? 0)}. The question is now policy.`;
		}
		return `${result.matchedCompanies} of ${rows.length} companies match. The board is filtered.`;
	}

	function clearAsk() {
		applied = null;
		questionText = '';
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
		applied = null;
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

	async function exportLedger(list: ScopedRow[], name: string) {
		if (list.length === 0) return;
		busy = true;
		try {
			const details = await Promise.allSettled(
				list.map((row) => fetchCompany(row.id, row.serviceLine ?? lineOf(row.id)))
			);
			const lines: (string | number)[][] = [];
			details.forEach((result, index) => {
				if (result.status !== 'fulfilled') return;
				const company = list[index];
				for (const entry of result.value.ledger) {
					lines.push([
						company.name,
						company.serviceLine ?? lineOf(company.id),
						entry.questionKey,
						entry.questionTitle,
						entry.verdict,
						entry.polarity,
						precise(entry.confidence),
						entry.theme,
						entry.sourceKind,
						entry.publisher,
						entry.title ?? '',
						entry.sourceUrl,
						entry.spanStart,
						entry.spanEnd,
						(entry.spanText ?? '').replace(/\s+/g, ' '),
						entry.engine,
						entry.spanVerified ? 'verified' : 'unverified',
						precise(entry.contributionNats),
						entry.rationale ?? ''
					]);
				}
			});
			save(
				name,
				csv(
					[
						'company',
						'service_line',
						'question_key',
						'question',
						'verdict',
						'polarity',
						'confidence',
						'theme',
						'source_kind',
						'publisher',
						'document_title',
						'source_url',
						'span_start',
						'span_end',
						'verbatim_span',
						'engine',
						'span_check',
						'contribution_nats',
						'model_reason'
					],
					lines
				)
			);
			toasts.push(
				lines.length === 0
					? 'Nothing to export: those companies carry no scored evidence.'
					: `Exported ${lines.length} evidence rows with their byte ranges.`,
				lines.length === 0 ? 'warn' : 'success'
			);
		} finally {
			busy = false;
		}
	}

	async function saveMany(ids: string[]) {
		if (ids.length === 0) return;
		busy = true;
		try {
			const results = await Promise.allSettled(ids.map((id) => addBookmark(id, lineOf(id))));
			const done = results.filter((result) => result.status === 'fulfilled').length;
			toasts.push(
				done === ids.length
					? `Saved ${done} ${done === 1 ? 'company' : 'companies'}.`
					: `Saved ${done} of ${ids.length}. The rest failed.`,
				done === ids.length ? 'success' : 'warn'
			);
			loadSaved();
			workspace.savedChanged();
		} finally {
			busy = false;
		}
	}

	async function unsaveMany(ids: string[]) {
		if (ids.length === 0) return;
		busy = true;
		try {
			const results = await Promise.allSettled(ids.map((id) => removeBookmark(id, lineOf(id))));
			const done = results.filter((result) => result.status === 'fulfilled').length;
			toasts.push(
				done === ids.length
					? `Removed ${done} from Saved.`
					: `Removed ${done} of ${ids.length}. The rest failed.`,
				done === ids.length ? 'success' : 'warn'
			);
			loadSaved();
			workspace.savedChanged();
			if (filter === 'saved') rows = await loadBoard();
		} finally {
			busy = false;
		}
	}

	async function runAsk() {
		const text = questionText.trim();
		if (text.length < 4 || rescoring) return;
		rescoring = true;
		elapsed = 0;
		applied = null;
		try {
			const result = await ask({ serviceLine, question: text, engine, tier: weightTier, polarity });
			applied = result;
			if (result.board) {
				rows = allLines
					? await loadBoard()
					: result.board.map((row) => ({ ...row, serviceLine, fitRank: row.rank }));
			} else {
				rows = await loadBoard();
			}
			elapsed = result.tookMs ?? 0;
			if (filter !== 'all') filter = 'all';
			toasts.push(summarise(result), 'success');
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
		{
			id: 'export-ledger',
			label: 'Export the evidence ledger',
			icon: 'download',
			hint: picked.size > 0 ? `${picked.size} selected` : 'every visible row',
			disabled: sorted.length === 0
		},
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
		else if (id === 'export-ledger') {
			const list = picked.size > 0 ? sorted.filter((row) => picked.has(row.id)) : sorted;
			const slug = (allLines ? 'all' : serviceLine).toLowerCase().replace(/\s+/g, '-');
			await exportLedger(list, `ledger-${slug}.csv`);
		} else if (id === 'copy-link') copyLink();
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
			{ id: 'ledger', label: 'Export its evidence ledger', icon: 'download' },
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
		else if (id === 'ledger')
			exportLedger([row], `${row.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-ledger.csv`);
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

	const typingIn = (target: EventTarget | null) => {
		const node = target as HTMLElement | null;
		const tag = node?.tagName;
		return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || node?.isContentEditable === true;
	};

	function step(delta: number) {
		const list = sorted;
		if (list.length === 0) return;
		const at = selected ? list.findIndex((row) => row.id === selected) : -1;
		const next = at < 0 ? (delta > 0 ? 0 : list.length - 1) : Math.min(list.length - 1, Math.max(0, at + delta));
		const row = list[next];
		if (!row) return;
		select(row.id);
		const index = list.filter((candidate) => lineOf(candidate.id) === row.serviceLine).indexOf(row);
		const pageFor = Math.floor(index / (size || list.length));
		if (size && pageFor + 1 !== page) page = pageFor + 1;
		requestAnimationFrame(() => {
			document.querySelector(`tr[data-company="${row.id}"]`)?.scrollIntoView({ block: 'nearest' });
		});
	}

	function onKey(event: KeyboardEvent) {
		if (event.metaKey || event.ctrlKey || event.altKey) return;
		const inField = typingIn(event.target);
		if (event.key === '/' && !inField) {
			event.preventDefault();
			(document.querySelector('[role="toolbar"] input[type="search"]') as HTMLInputElement | null)?.focus();
			return;
		}
		if (inField) return;
		if (event.key === 'j' || event.key === 'ArrowDown') {
			event.preventDefault();
			step(1);
		} else if (event.key === 'k' || event.key === 'ArrowUp') {
			event.preventDefault();
			step(-1);
		} else if (event.key === 'Enter' && !selected) {
			event.preventDefault();
			step(1);
		} else if (event.key === 'Escape' && selected) {
			event.preventDefault();
			select(null);
		} else if (event.key === 's' && selected) {
			event.preventDefault();
			const id = selected;
			if (saved.has(id)) removeBookmark(id, lineOf(id)).then(() => {
				loadSaved();
				workspace.savedChanged();
			});
			else addBookmark(id, lineOf(id)).then(() => {
				loadSaved();
				workspace.savedChanged();
			});
		}
	}
</script>

<svelte:window onkeydown={onKey} />

<div class="relative flex min-h-0 flex-1">
	<View class="min-w-0 flex-1">
		<Topbar title="Board">
			{#snippet subtitle()}
				{#if error}
					The board could not be read from the API — a failure, not an empty result
				{:else}
					{shortNumber(rows.length)} companies · {regionLabel(meta?.regions ?? [], session.region)}{#if stage}
						· {STAGES.find((s) => s.id === stage)?.label}{/if}
				{/if}
			{/snippet}
			{#snippet metrics()}
				{#if meta}
					<div class="flex flex-wrap items-stretch max-compact:hidden">
						<Metric value={shortNumber(counts.PRIORITY ?? 0)} label="Priority" tone="hot" title="Companies above the Priority line, in view" />
						<Metric value={shortNumber(counts.HOT ?? 0)} label="Hot" title="Priority and Nurture together, in view" />
						<Metric value={shortNumber(meta.captures)} label="Captures" title="Stored documents the board was scored against" />
					</div>
				{/if}
			{/snippet}
			{#snippet tools()}
				<Button variant="primary" icon="target" onclick={() => (askOpen = !askOpen)} aria-expanded={askOpen}>
					{askOpen ? 'Close' : 'Ask a question'}
				</Button>
				<Menu items={secondary} onselect={runSecondary} label="More board actions" />
			{/snippet}
		</Topbar>

		{#if askOpen || applied}
			<div class="flex flex-none flex-col gap-2 border-b border-orange-line bg-orange-wash px-4 py-3">
				{#if !applied}
					<div class="flex flex-wrap items-end gap-3">
					<div class="flex min-w-rail flex-1 flex-col gap-1">
						<label class="type-label" for="ask">Question the corpus has not been scored against</label>
						<Textarea
							id="ask"
							rows={2}
							bind:value={questionText}
							placeholder="Is the company consolidating shared service centres into a captive operation?"
						/>
					</div>

					<div class="flex flex-none items-end gap-2">
						<div class="flex flex-col gap-1">
							<span class="type-label">Weight</span>
							<Select
								id="weight-tier"
								label="Weight"
								size="sm"
								bind:value={weightTier}
								options={[
									{ value: 'HIGH', label: 'High · 1.5' },
									{ value: 'MEDIUM', label: 'Medium · 0.7' },
									{ value: 'LOW', label: 'Low · 0.3' }
								]}
							/>
						</div>
						<div class="flex flex-col gap-1">
							<span class="type-label">Polarity</span>
							<Select
								id="polarity"
								label="Polarity"
								size="sm"
								bind:value={polarity}
								options={[
									{ value: 'POSITIVE', label: 'Positive' },
									{ value: 'NEGATIVE', label: 'Counter' },
									{ value: 'DISQUALIFYING', label: 'Disqualifying' }
								]}
							/>
						</div>
						<div class="flex flex-col gap-1">
							<span class="type-label">Engine</span>
							<Select
								id="engine"
								label="Engine"
								size="sm"
								bind:value={engine}
								options={[
									{ value: 'local', label: 'Local rubric' },
									{
										value: 'deepseek',
										label: `Model${meta?.languageModelAvailable ? '' : ' — off'}`,
										disabled: !meta?.languageModelAvailable
									}
								]}
							/>
						</div>
						<Menu
							text="Examples"
							size="sm"
							align="start"
							label="Example questions"
							items={ASKS.map((ask, index) => ({ id: String(index), label: ask }))}
							onselect={(id) => (questionText = ASKS[Number(id)])}
						/>
						<Button
							variant="primary"
							onclick={runAsk}
							disabled={rescoring || questionText.trim().length < 4}
						>
							{#if rescoring}<Spinner />{/if}
							{rescoring ? duration(elapsed) : reading ? KIND_ACTION[reading.kind] : 'Ask'}
						</Button>
					</div>

					</div>

					<div class="flex flex-wrap items-baseline gap-2">
						{#if rescoring}
							<span class="type-label">Classifying {shortNumber(meta?.captures ?? 0)} documents</span>
						{:else if reading}
							<span
								class={[
									'whitespace-nowrap border bg-surface px-2 py-px text-micro font-semibold uppercase tracking-caps',
									reading.kind === 'SIGNAL' ? 'border-rule-strong text-ink-3' : 'border-orange-line text-orange-ink'
								]}
								data-kind={reading.kind}>{KIND_LABEL[reading.kind]}</span
							>
							<span class="type-sub">{reading.reading}</span>
						{:else}
							<span class="type-sub text-ink-4">
								Ask about the company itself (“in Romania”, “large German banks”) and the board filters. Anything else is
								read as a signal question and every stored document is classified against it.
							</span>
						{/if}
					</div>

					{#if askCues.length > 0 && !rescoring}
						<div class="flex flex-wrap items-center gap-2 border-t border-rule px-3 py-2">
							<span class="type-label">It will match</span>
							{#each askCues as cue (cue)}<Tag tone="wash">{cue}</Tag>{/each}
							<span class="type-sub text-ink-4">Nothing is configured, so the rubric derives these from the wording.</span>
						</div>
					{/if}
				{:else}
					<div class="flex w-full items-center gap-5">
						{#if applied.movedRows !== undefined}
							<div class="flex flex-wrap items-stretch">
								<Metric value={applied.movedRows} label="Rows moved" tone="hot" />
								<Metric value={duration(applied.tookMs ?? 0)} label="Elapsed" />
								<Metric value={shortNumber(applied.documentsScanned ?? 0)} label="Documents" />
								<Metric value={shortNumber(applied.signalsProduced ?? 0)} label="Signals" />
							</div>
						{:else}
							<div class="flex flex-wrap items-stretch">
								<Metric value={applied.matchedCompanies ?? 0} label="Companies match" tone="hot" />
								<Metric value={applied.facets.length} label="Attributes read" />
							</div>
						{/if}
						{#if applied.keptPrevious}
							<Note tone="warn">
								Every classification in that run failed, so the signals already stored were kept rather than
								replaced. The board is unchanged — re-run when the engine is reachable.
							</Note>
						{/if}
						{#if moved.length > 0}
							<ul class="m-0 mt-3 list-none border-t border-rule p-0">
								{#each moved as entry (entry.companyId)}
									<li>
										<button
											type="button"
											class="flex w-full items-center gap-3 border-b border-rule px-3 py-2 text-left hover:bg-sunken"
											onclick={() => select(entry.companyId)}
										>
											<span class="min-w-0 flex-1 truncate">{entry.company}</span>
											<span class="num text-ink-3">
												{entry.beforeRank > 0 ? `#${entry.beforeRank}` : 'new'} → #{entry.afterRank}
											</span>
											<span class={entry.direction === 'up' ? 'text-accent' : entry.direction === 'down' ? 'text-oxblood' : ''}>
												{entry.direction === 'up' ? '▲' : entry.direction === 'down' ? '▼' : '='}
												{Math.abs((entry.beforeProbability ?? 0) - entry.afterProbability).toFixed(2)}
											</span>
										</button>
									</li>
								{/each}
							</ul>
						{/if}
						<div class="flex min-w-0 flex-1 flex-col gap-1">
							<p class="type-display min-w-0 flex-1 truncate text-lg text-ink-2">“{applied.question}”</p>
							{#if applied.movedRows !== undefined}
								<p class="type-sub text-ink-4">
									The question is policy now. Open it in the Studio to write the rubric that decides what counts —
									a question without one is scored on its wording alone.
									<a href={`/studio?q=${encodeURIComponent(applied.questionKey ?? '')}`}>Refine in the Studio →</a>
								</p>
							{/if}
							{#if applied.facets.length > 0}
								<p class="flex flex-wrap gap-1">
									{#each applied.facets as facet (facet.kind + facet.value)}
										<Tag tone="wash">{facet.label} <span class="num">{facet.matches}</span></Tag>
									{/each}
								</p>
							{/if}
						</div>
						<Button size="sm" onclick={clearAsk}>Clear</Button>
					</div>
				{/if}
			</div>
		{/if}

		{#if picked.size > 0}
			<Bulkbar count={picked.size} onclear={() => (picked = new Set())}>
				<Menu
					items={bulkItems}
					onselect={(id) => mark([...picked], id, LEAD_STAGES.find((s) => s.id === id)?.label.toLowerCase() ?? '')}
					text="Set stage"
					size="sm"
					align="start"
					label="Set lead stage for the selection"
					tone="inverse"
				/>
				<Button variant="inverse" size="sm" icon="star" onclick={() => saveMany([...picked])} disabled={busy}>Save</Button>
				<Button variant="inverse" size="sm" icon="star" onclick={() => unsaveMany([...picked])} disabled={busy}>Unsave</Button>
				<Button
					variant="inverse"
					size="sm"
					icon="download"
					onclick={() => exportRows(sorted.filter((row) => picked.has(row.id)), 'selection.csv')}
				>
					Export
				</Button>
				<Button
					variant="inverse"
					size="sm"
					icon="link"
					onclick={async () => {
						const ok = await copy([...picked].map((id) => `${window.location.origin}/?co=${id}`).join('\n'));
						toasts.push(ok ? `Copied ${picked.size} links.` : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
					}}
				>
					Copy links
				</Button>
				{#if busy}<Spinner />{/if}
			</Bulkbar>
		{:else}
			<Toolbar>
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
				{#if workspace.switching}<Spinner />{/if}

				<Segmented
					bind:value={filter}
					label="Filter"
					class="max-sm:w-full max-sm:overflow-x-auto"
					options={FILTERS.map((option) => ({
						value: option.id,
						label: option.label,
						count: option.id === 'saved' ? savedCount : undefined
					}))}
				/>

				<Input
					type="search"
					size="sm"
					icon="search"
					aria-label="Filter by name, country or industry"
					placeholder="Name, country, industry"
					bind:value={query}
					class="w-list flex-none max-sm:order-first max-sm:w-full"
				/>

				<Menu
					text={engineFilter === 'all' ? 'Any engine' : engineFilter === 'model' ? 'Model only' : 'Rubric only'}
					icon="studio"
					size="sm"
					align="end"
					label="Filter by the engine that answered"
					items={[
						{ id: 'all', label: 'Any engine', checked: engineFilter === 'all' },
						{ id: 'model', label: 'Model only', checked: engineFilter === 'model' },
						{ id: 'local', label: 'Rubric only', checked: engineFilter === 'local' }
					]}
					onselect={(id) => (engineFilter = id as 'all' | 'model' | 'local')}
				/>

				<div class="ml-auto">
					<Select
						id="stage"
						label="Lead stage"
						size="sm"
						class="w-auto max-sm:min-w-0 max-sm:flex-1"
						bind:value={stage}
						options={STAGES.map((option) => ({ value: option.id, label: option.label }))}
					/>
				</div>

				{#if filtered}
					<Button size="sm" variant="quiet" onclick={clearFilters}>Clear</Button>
				{/if}
			</Toolbar>
		{/if}

			<section class="scroll relative min-h-0 flex-1 bg-surface max-sm:overflow-x-auto" aria-label="Ranked companies">
				<LoadingVeil show={loading} label="Scoring the board" />
				{#if error}
					<Note tone="warn" icon="alert">{error}</Note>
				{:else if visible.length === 0 && !loading}
					<Empty>
						<Icon name="inbox" size="xl" />
						<p>Nothing matches this filter. Widen the region, clear the stage, or ask a different question.</p>
					</Empty>
				{:else}
					<table class="table-grid">
						<thead>
							<tr>
								<th scope="col" class="w-row pr-0">
									<Checkbox
										checked={allPicked}
										indeterminate={!allPicked && somePicked}
										label="Select every company on this page"
										onchange={toggleAll}
									/>
								</th>
								<Th id="rank" label="#" sort={sort} {dir} onsort={sortBy} align="right" width="58px" title="Fit rank — the order the ledger produces" />
								<Th id="name" label="Company" sort={sort} {dir} onsort={sortBy} width="21%" />
								<Th id="fit" label="Fit" sort={sort} {dir} onsort={sortBy} align="right" width="128px" title="Probability this company is worth calling" />
								<Th id="band" label="Band" sort={sort} {dir} onsort={sortBy} align="center" width="96px" />
								<th scope="col">Top evidence</th>
								<Th id="themes" label="Themes" sort={sort} {dir} onsort={sortBy} width="142px" class={selected !== null ? 'hidden' : 'max-xl:hidden'} />
								<Th id="docs" label="Docs" sort={sort} {dir} onsort={sortBy} align="right" width="56px" class={selected !== null ? 'hidden' : 'max-lg:hidden'} />
								<Th id="newest" label="Newest" sort={sort} {dir} onsort={sortBy} align="right" width="66px" class={selected !== null ? 'hidden' : 'max-lg:hidden'} />
								<th scope="col" class="w-ctl pr-0 text-center"><span class="sr-only">Saved</span></th>
								<th scope="col" class="w-row pl-0"><span class="sr-only">Actions</span></th>
							</tr>
						</thead>
						<tbody>
							{#each current as row (row.id)}
								{@const move = movements.get(row.id)}
								{@const top = row.chips.length > 0 ? reasons(row) : []}
								{@const shownTop = selected !== null ? top.slice(0, 2) : top}
								<tr
									data-company={row.id}
									class={[
										'cursor-pointer border-l-2 border-transparent transition-colors hover:bg-paper',
										selected === row.id && 'border-orange bg-orange-wash',
										picked.has(row.id) && 'bg-sunken'
									]}
									onclick={() => select(row.id)}
								>
									<td class="w-row pr-0" onclick={(event) => event.stopPropagation()}>
										<Checkbox
											checked={picked.has(row.id)}
											label={`Select ${row.name}`}
											onchange={() => togglePick(row.id)}
										/>
									</td>
									<td class="num text-base text-ink-3">
										{allLines ? row.fitRank : row.rank}
										{#if move && move.delta !== 0}
											<span class={['ml-1 inline-block text-micro', move.delta > 0 ? 'text-accent' : 'text-oxblood']}>
												{move.delta > 0 ? '▲' : '▼'}{Math.abs(move.delta)}
											</span>
										{/if}
									</td>
									<td class="max-md:whitespace-nowrap">
										<span class={['block font-display text-lg font-medium leading-snug tracking-num', row.band === 'DISQUALIFIED' && 'text-ink-3']}>{row.name}</span>
										{#if row.evidenceCount === 0}
											<span
												class="ml-2 inline-block cursor-help rounded-sm border border-dotted border-rule-strong px-1 text-micro tracking-wide text-ink-3 uppercase"
												title="No document was ever captured for this company. Nothing can be said either way — a gap in the crawl, not a finding."
											>no evidence</span>
										{/if}
										<span class={['block type-sub', selected !== null && 'truncate']}>
											{#if allLines}<span class="font-medium text-orange-ink">{row.serviceLine}</span> · {/if}{#if row.domain}<span>{row.domain}</span> · {/if}{row.country ?? '—'}
											{#if row.industry}· {row.industry}{/if}
											{#if row.sizeBand}· {row.sizeBand}{/if}
										</span>
										{#if row.leadStatus && row.leadStatus !== 'NEW'}
											<span
												class={[
													'mt-1 inline-block border px-1 py-px text-micro font-semibold tracking-caps uppercase',
													row.leadStatus === 'QUALIFIED' && 'border-accent text-accent',
													row.leadStatus === 'REJECTED' && 'border-oxblood-line text-oxblood',
													(row.leadStatus === 'CONTACTED' || row.leadStatus === 'REPLIED') && 'border-orange-line text-orange-ink',
													!['QUALIFIED', 'REJECTED', 'CONTACTED', 'REPLIED'].includes(row.leadStatus) && 'border-rule-strong text-ink-3'
												]}
											>
												{row.leadStatusLabel}{#if row.contactCount > 1} ×{row.contactCount}{/if}
											</span>
										{/if}
									</td>
									<td class="align-top">
										<span class={['block text-xl font-medium leading-tight mb-1', row.band === 'DISQUALIFIED' && 'text-ink-3 line-through decoration-oxblood']}>{fit(row.probability)}</span>
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
									<td class="text-center">
										<Tag band={row.band} title={row.gateReason ?? row.bandLabel}>{row.bandShort}</Tag>
										{#if row.gateReason}
											<span class="mt-1 block text-micro tracking-wide text-oxblood uppercase" title={row.gateReason}>{gateCause(row.gateReason)}</span>
										{/if}
									</td>
									<td>
										{#if row.chips.length > 0}
											<ul class="m-0 flex list-none flex-col gap-0.5 p-0">
												{#each shownTop as chip (chip.questionKey)}
													<li class="flex min-w-0 items-baseline gap-1" title={`${chip.publisher}${chip.title ? ` · ${chip.title}` : ''}`}>
														<span
															class={[
																'size-1 flex-none -translate-y-px',
																chip.polarity === 'NEGATIVE' || chip.polarity === 'DISQUALIFYING' ? 'bg-oxblood' : 'bg-accent'
															]}
														></span>
														<span class={['min-w-0 flex-1 truncate text-base', row.urgency === 'ACT_NOW' ? 'text-ink' : 'text-ink-2']}>{chip.questionTitle}</span>
														<span class="num flex-none text-micro text-ink-4">{signed(chip.contribution, 2)}</span>
													</li>
												{/each}
											</ul>
											{#if row.strongest?.quote}
												<p
													class="mt-1 line-clamp-2 border-l-2 border-orange-line pl-2 font-display text-base leading-normal text-ink italic"
													title={row.signalCount > top.length
														? `${row.signalCount - top.length} further signals in the ledger`
														: ''}
												>“{row.strongest.quote}”</p>
											{/if}
										{:else}
											<span class="type-sub text-ink-4">no signal cleared the floor</span>
										{/if}
									</td>
									<td class={[selected !== null && 'hidden', 'max-xl:hidden']}>
										<div class="flex flex-wrap gap-1">
											{#each row.themes as name (name)}
												<Tag class="max-w-full truncate">{theme(name)}</Tag>
											{/each}
											{#if row.themeCount === 0}
												<span class="type-sub text-ink-4">—</span>
											{/if}
										</div>
										{#if row.themeCount >= 2}
											<span class="mt-1 block text-micro font-semibold tracking-caps text-accent uppercase" title="Two independent kinds of evidence — what the Priority band requires">corroborated</span>
										{/if}
									</td>
									<td class={['num', selected !== null && 'hidden', 'max-lg:hidden']}>
										{#if row.evidenceCount === 0}
											<span class="ml-2 inline-block cursor-help rounded-sm border border-dotted border-rule-strong px-1 text-micro tracking-wide text-ink-3 uppercase" title="No document was ever captured for this company. Nothing can be said either way — this is a gap in the crawl, not a finding.">no evidence</span>
										{:else}
											{row.evidenceCount}
											{#if row.silentDocuments > 0}
												<span class="type-sub text-ink-4" title="Documents read that produced no signal">{row.silentDocuments} silent</span>
											{/if}
										{/if}
									</td>
									<td class={['num', selected !== null && 'hidden', 'max-lg:hidden']}>{ageShort(row.newestSignalAgeDays)}</td>
									<td class="w-ctl pr-0 text-center">
									<Star
										companyId={row.id}
										companyName={row.name}
										serviceLine={row.serviceLine}
										saved={saved.has(row.id)}
										onsaved={(on) => {
											const next = new Set(saved);
											on ? next.add(row.id) : next.delete(row.id);
											saved = next;
										}}
									/>
								</td>
								<td class="w-row pl-0" onclick={(event) => event.stopPropagation()}>
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
	</View>

	{#if selected}
		<div
			class="fixed inset-0 z-raised animate-scrim bg-scrim md:hidden"
			role="presentation"
			onclick={() => select(null)}
		></div>
		<aside
			class={[
				'min-h-0 animate-enter-right border-l border-ink bg-surface',
				'max-md:fixed max-md:inset-y-0 max-md:right-0 max-md:z-overlay max-md:w-full max-md:max-w-palette',
				'md:relative md:w-2/5 md:max-w-palette md:flex-none'
			]}
			aria-label="Company detail"
		>
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
