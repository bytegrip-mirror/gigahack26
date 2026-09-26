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
	import { markCompetitor } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { ageShort, duration, fit, fitTone, fitWord, plural, precise, shortNumber } from '$lib/format';
	import { regionLabel } from '$lib/regions';
	import { copy, csv, save } from '$lib/export';
	import Tag from '$lib/components/Tag.svelte';
	import CompanyMark from '$lib/components/CompanyMark.svelte';
	import CaptureSheet from '$lib/components/CaptureSheet.svelte';
	import Scorecard from '$lib/views/Scorecard.svelte';
	import Star from '$lib/components/Star.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import type { MenuItem } from '$lib/components/menu';
	import Metric from '$lib/components/Metric.svelte';
	import SignalTrace from '$lib/components/SignalTrace.svelte';
	import Pager from '$lib/components/Pager.svelte';
	import Th from '$lib/components/Th.svelte';

	let {
	initialSelected = null,
	initialLine = null
}: { initialSelected?: string | null; initialLine?: string | null } = $props();

	const FILTERS = [
		{ id: 'to-call', label: 'To call', title: 'Worth calling, and nobody has worked them yet. Set the lead stage under Filters to see the ones already worked.' },
		{ id: 'priority', label: 'Priority', title: 'Above the Priority line' },
		{ id: 'thin', label: 'Unproven', title: 'No document answers your questions about them' },
		{ id: 'rejected', label: 'Out of the queue', title: 'Removed by a gate or the blacklist' },
		{ id: 'saved', label: 'Saved', title: 'Bookmarked' },
		{ id: 'everyone', label: 'Everyone', title: 'Every company we hold, whatever the queue decided and whoever has worked them' }
	];

	const WEAK_OPENERS = new Set([
		'companies', 'company', 'banks', 'bank', 'firms', 'firm', 'businesses', 'business',
		'organisations', 'organizations', 'employers', 'employer', 'groups', 'agencies', 'operators',
		'retailers', 'insurers', 'carriers', 'makers', 'players', 'giants', 'enterprises', 'corporations'
	]);

	const STAGES = [
		{ id: '', label: 'Any lead stage' },
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

	const ALL_LINES = '__all__';

	type ScopedRow = BoardRow & { serviceLine: string; fitRank: number };

	function worked(row: BoardRow): boolean {
		return row.leadStatus !== null && row.leadStatus !== 'NEW';
	}

	const VIEWS: Record<string, (row: BoardRow) => boolean> = {
		'to-call': (row) => row.band !== 'DISQUALIFIED' && row.band !== 'THIN' && !worked(row),
		priority: (row) => row.band === 'PRIORITY',
		thin: (row) => row.band === 'THIN',
		rejected: (row) => row.band === 'DISQUALIFIED',
		saved: (row) => saved.has(row.id),
		everyone: () => true
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

	let filter = $state('to-call');
	let engineFilter = $state<'all' | 'model' | 'local'>('all');
	let profileFilter = $state('');
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
	let engineChosen = $state(false);
	let rescoring = $state(false);
	let elapsed = $state(0);
	let applied = $state<AskOutcome | null>(null);
	let reading = $state<AskReading | null>(null);
	let askCues = $state<string[]>([]);

	let capture = $state<{ id: string; focus: { start: number; end: number } | null } | null>(null);
	let peeking = $state<string | null>(null);
	let peek = $state<Awaited<ReturnType<typeof fetchCompany>> | null>(null);
	let peekTimer: ReturnType<typeof setTimeout> | null = null;

	const meta = $derived(workspace.meta);
	const serviceLine = $derived(session.serviceLine);
	const moved = $derived(
		(applied?.moves ?? [])
			.filter((move) => move.direction !== 'flat')
			.sort((a, b) => (a.afterRank ?? 0) - (b.afterRank ?? 0))
			.slice(0, 6)
	);

	const subtitle = $derived.by(() => {
		const total = shortNumber(rows.length);
		if (filter === 'everyone') return `${total} companies we hold`;
		const view = FILTERS.find((option) => option.id === filter)?.label.toLowerCase() ?? 'companies';
		return `${shortNumber(sorted.length)} ${view} of ${total}`;
	});

	const narrowed = $derived(
		session.region !== 'world' || stage !== '' || query !== '' || applied !== null || filter !== 'to-call'
	);
	const activeFilters = $derived(
		(stage === '' ? 0 : 1) + (engineFilter === 'all' ? 0 : 1) + (profileFilter === '' ? 0 : 1)
	);
	const profiles = $derived(workspace.profiles);
	const STAGE_LABEL = $derived(STAGES.find((option) => option.id === stage)?.label ?? 'Any lead stage');
	const ENGINE_LABEL = $derived(
		engineFilter === 'all' ? 'Any engine' : engineFilter === 'model' ? 'Read by the model' : 'Matched by the rules'
	);

	const filterItems: MenuItem[] = $derived([
		{ id: 'head-stage', label: 'Lead stage', heading: true },
		...STAGES.map((option) => ({
			id: `stage:${option.id}`,
			label: option.label,
			checked: stage === option.id
		})),
		...(profiles.length > 0
			? [
					{ id: 'head-icp', label: 'Customer profile', heading: true, separatorBefore: true },
					{ id: 'icp:', label: 'Any profile', checked: profileFilter === '' },
					...profiles.map((name) => ({
						id: `icp:${name}`,
						label: name,
						checked: profileFilter === name
					}))
				]
			: []),
		{ id: 'head-engine', label: 'Read by', heading: true, separatorBefore: true },
		...(
			[
				{ id: 'all', label: 'Any engine' },
				{ id: 'model', label: 'The model' },
				{ id: 'local', label: 'The rules' }
			] as const
		).map((option) => ({ id: `engine:${option.id}`, label: option.label, checked: engineFilter === option.id }))
	]);

	function runFilter(id: string) {
		const [kind, value] = id.split(':');
		if (kind === 'stage') stage = value;
		else if (kind === 'icp') profileFilter = value;
		else engineFilter = value as 'all' | 'model' | 'local';
	}
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

	const thresholds = $derived({
		watch: meta?.scoring.watchThreshold ?? 0.28,
		nurture: meta?.scoring.nurtureThreshold ?? 0.45,
		priority: meta?.scoring.priorityThreshold ?? 0.55
	});

	const visible = $derived.by(() => {
		const wanted = VIEWS[filter] ?? VIEWS.everyone;
		return rows.filter((row) => {
			const passesBand = wanted(row) && (profileFilter === '' || row.profiles.includes(profileFilter));
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
			ALL: visible.length,
			PRIORITY: 0,
			NURTURE: 0,
			WATCH: 0,
			THIN: 0,
			DISQUALIFIED: 0
		};
		for (const row of visible) tally[row.band] = (tally[row.band] ?? 0) + 1;
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
		if (!engineChosen && meta?.languageModelAvailable) engine = 'deepseek';
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

	function openPeek(row: ScopedRow) {
		if (peekTimer) clearTimeout(peekTimer);
		peekTimer = setTimeout(() => {
			peeking = row.id;
			fetchCompany(row.id, row.serviceLine)
				.then((result) => {
					if (peeking === row.id) peek = result;
				})
				.catch(() => {});
		}, 260);
	}

	function closePeek() {
		if (peekTimer) clearTimeout(peekTimer);
		peeking = null;
		peek = null;
	}

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
		SIGNAL: 'Read every document for it',
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
		filter = 'to-call';
		engineFilter = 'all';
		profileFilter = '';
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
						'quote_starts_at_character',
						'quote_ends_at_character',
						'verbatim_quote',
						'engine',
						'quote_check',
						'weight_in_the_score',
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

	const weakCues = $derived(askCues.filter((cue) => !cue.includes(' ') || WEAK_OPENERS.has(cue.split(' ')[0])));

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
			if (filter !== 'to-call') filter = 'to-call';
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
			disabled: !narrowed,
			separatorBefore: true
		},
		{ id: 'rescore', label: 'Read everything again', icon: 'refresh' }
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
				? { id: 'capture', label: 'Open the quoted document', icon: 'external' }
				: { id: 'capture', label: 'No quoted document', icon: 'external', disabled: true },
			{ id: 'link', label: 'Copy link to this company', icon: 'link' },
			{ id: 'export', label: 'Export this row', icon: 'download' },
			{ id: 'ledger', label: 'Export its evidence ledger', icon: 'download' },
			{
				id: 'competitor',
				label: row.competitor ? 'Not a competitor after all' : 'Mark as a competitor',
				icon: row.competitor ? 'check' : 'shield',
				hint: row.competitor ? 'back into the queue' : 'out of the queue',
				separatorBefore: true
			},
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
		else if (id === 'competitor') {
			try {
				await markCompetitor(row.id, !row.competitor);
				rows = await loadBoard();
				toasts.push(
					row.competitor ? `${row.name} is callable again.` : `${row.name} is marked as a competitor and leaves the queue.`,
					'success'
				);
			} catch (cause) {
				toasts.push((cause as Error).message, 'error');
			}
		}
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
			(document.querySelector('.toolbar input[type="search"]') as HTMLInputElement | null)?.focus();
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

<div class="view" class:split={selected !== null}>
	<div class="main">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Board</h1>
			<p class="sub num">
				{#if error}
					The board could not be read.
				{:else}
					{subtitle} · {regionLabel(meta?.regions ?? [], session.region)}{#if stage}
						· {STAGES.find((s) => s.id === stage)?.label}{/if}
				{/if}
			</p>
		</div>

		{#if meta}
			<div class="metrics">
				<Metric value={shortNumber(counts.PRIORITY ?? 0)} label="Priority" tone="hot" title="Companies above the Priority line, in view" />
				<Metric value={shortNumber(counts.HOT ?? 0)} label="Hot" title="Priority and Nurture together, in view" />
				<Metric value={shortNumber(meta.captures)} label="Documents" title="Documents the board read" />
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

	{#if askOpen || applied}
		<div class="ask">
			{#if !applied}
				<div class="ask-row">
				<div class="ask-input">
					<label class="label" for="ask">A question nobody has asked yet</label>
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
							<option value="HIGH">High</option>
							<option value="MEDIUM">Medium</option>
							<option value="LOW">Low</option>
						</select>
					</label>
					<label class="field">
						<span class="label">Polarity</span>
						<select class="select select-sm" bind:value={polarity}>
							<option value="POSITIVE">Counts for the company</option>
							<option value="NEGATIVE">Counts against the company</option>
							<option value="DISQUALIFYING">Removes the company from the queue</option>
						</select>
					</label>
					<label class="field">
						<span class="label">Engine</span>
						<select
							class="select select-sm"
							value={engine}
							onchange={(event) => {
								engineChosen = true;
								engine = (event.currentTarget as HTMLSelectElement).value as Engine;
							}}
						>
							<option value="deepseek" disabled={!meta?.languageModelAvailable}>
								Reads the documents{meta?.languageModelAvailable ? ' (recommended)' : ' — unavailable'}
							</option>
							<option value="local">Matches words</option>
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
						disabled={rescoring || questionText.trim().length < 4}
					>
						{#if rescoring}<span class="spinner"></span>{/if}
						{rescoring ? duration(elapsed) : reading ? KIND_ACTION[reading.kind] : 'Ask'}
					</button>
				</div>

				</div>

				<div class="ask-read">
					{#if rescoring}
						<span class="label pulsing">Reading {shortNumber(meta?.captures ?? 0)} documents</span>
					{:else if reading}
						<span class="read-kind" data-kind={reading.kind}>{KIND_LABEL[reading.kind]}</span>
						<span class="sub ask-note">{reading.reading}</span>
					{:else}
						<span class="sub dim ask-note">
							Ask about the company itself (“in Romania”, “large German banks”) and the board filters. Anything else is
							read as a signal question and every document we hold is read for it.
						</span>
					{/if}
				</div>

				{#if askCues.length > 0 && !rescoring}
					<div class="ask-cues">
						<span class="label">The words it would look for</span>
						{#each askCues as cue (cue)}<Tag variant={weakCues.includes(cue) ? 'oxblood' : 'wash'}>{cue}</Tag>{/each}
						{#if weakCues.length > 0}
							<p class="note warn">
								<Icon name="alert" size={14} />
								<span>
									{weakCues.length === 1 ? 'One of these is' : `${weakCues.length} of these are`} too general to be a
									signal — {weakCues.join(', ')} will match almost every company we hold. {meta?.languageModelAvailable
										? 'Send it to the model instead, or write the question with the words you would actually look for.'
										: 'Write the question with the words you would actually look for.'}
								</span>
							</p>
						{/if}
						<span class="sub dim">
							This question has no word list of its own, so these werese were taken from the wording of the question.
						</span>
					</div>
				{/if}
			{:else}
				<div class="result">
					{#if applied.movedRows !== undefined}
						<div class="metrics">
							<Metric value={applied.movedRows} label="Rows moved" tone="hot" />
							<Metric value={duration(applied.tookMs ?? 0)} label="Elapsed" />
							<Metric value={shortNumber(applied.documentsScanned ?? 0)} label="Documents" />
							<Metric value={shortNumber(applied.signalsProduced ?? 0)} label="Signals" />
							{#if applied.answeredFromOverlay}
								<Metric
									value={shortNumber(applied.answeredFromOverlay)}
									label="Already read by the model"
									title="Kept what was already read, so nothing was paid for twice"
								/>
							{/if}
						</div>
					{:else}
						<div class="metrics">
							<Metric value={applied.matchedCompanies ?? 0} label="Companies match" tone="hot" />
							<Metric value={applied.facets.length} label="Attributes read" />
						</div>
					{/if}
					{#if applied.keptPrevious}
						<p class="note warn">
							Every classification in that run failed, so the signals already stored were kept rather than
							replaced. The board is unchanged — re-run when the engine is reachable.
						</p>
					{/if}
					{#if moved.length > 0}
						<ul class="moved">
							{#each moved as entry (entry.companyId)}
								<li>
									<button class="moved-row" type="button" onclick={() => select(entry.companyId)}>
										<span class="moved-name">{entry.company}</span>
										<span class="num muted">
											{entry.beforeRank > 0 ? `#${entry.beforeRank}` : 'new'} → #{entry.afterRank}
										</span>
										<span class="moved-delta" data-dir={entry.direction}>
											{entry.direction === 'up' ? '▲' : entry.direction === 'down' ? '▼' : '='}
											{Math.abs((entry.beforeProbability ?? 0) - entry.afterProbability).toFixed(2)}
										</span>
									</button>
								</li>
							{/each}
						</ul>
					{/if}
					<div class="result-text">
						<p class="result-q dsp">“{applied.question}”</p>
						{#if applied.movedRows !== undefined}
							<p class="sub dim">
								The question is policy now. Open it in the Studio to write the rubric that decides what counts —
								a question without one is scored on its wording alone.
								<a class="link" href={`/studio?q=${encodeURIComponent(applied.questionKey ?? '')}`}>Refine in the Studio →</a>
							</p>
						{/if}
						{#if applied.facets.length > 0}
							<p class="sub chips">
								{#each applied.facets as facet (facet.kind + facet.value)}
									<span class="tag tag-wash">{facet.label} <span class="num">{facet.matches}</span></span>
								{/each}
							</p>
						{/if}
					</div>
					<button class="btn btn-sm" onclick={clearAsk}>Clear</button>
				</div>
			{/if}
		</div>
	{/if}

	{#if picked.size > 0}
		<div class="bulkbar">
			<span class="count"><span class="num">{picked.size}</span> selected</span>
			<span class="bulk-hint">·</span>
			<Menu items={bulkItems} onselect={(id) => mark([...picked], id, LEAD_STAGES.find((s) => s.id === id)?.label.toLowerCase() ?? '')} text="Set stage" size="sm" align="start" label="Set lead stage for the selection" />
			<button class="btn btn-sm" onclick={() => saveMany([...picked])} disabled={busy}>
				<Icon name="star" size={14} /> Save
			</button>
			<button class="btn btn-sm" onclick={() => unsaveMany([...picked])} disabled={busy}>
				<Icon name="star" size={14} /> Unsave
			</button>
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

			<div class="seg" role="tablist" aria-label="Which companies to show">
				{#each FILTERS as option (option.id)}
					<button
						role="tab"
						aria-selected={filter === option.id}
						title={option.title}
						onclick={() => (filter = option.id)}
					>
						{option.label}
						{#if option.id === 'saved'}<span class="num">{savedCount}</span>{/if}
					</button>
				{/each}
			</div>

			<span class="spacer"></span>

			<label class="search">
				<span class="sr-only">Filter by name, country or industry</span>
				<Icon name="search" size={14} />
				<input
					class="input input-sm"
					type="search"
					placeholder="Name, country, industry"
					bind:value={query}
				/>
			</label>

			<Menu
				text={activeFilters > 0 ? `Filters · ${activeFilters}` : 'Filters'}
				icon="filter"
				size="sm"
				align="end"
				label="Narrow the board"
				title={`${STAGE_LABEL} · ${ENGINE_LABEL}`}
				items={filterItems}
				onselect={runFilter}
			/>

			{#if narrowed}
				<button class="btn btn-sm btn-quiet" onclick={clearFilters}>Clear</button>
			{/if}
		</div>
	{/if}

		<section class="list scroll" aria-label="Ranked companies">
			<LoadingVeil show={loading} label="Ranking the board" />
			{#if error}
				<p class="note warn"><Icon name="alert" size={15} /> {error}</p>
			{:else if visible.length === 0 && !loading}
				<div class="empty">
					<Icon name="inbox" size={22} />
					<p>
						Nothing here. To call leaves out everyone you have already worked — set the lead stage under
						Filters to bring them back, or widen the region.
					</p>
				</div>
			{:else}
				<table class="tbl">
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
							<Th id="rank" label="#" sort={sort} {dir} onsort={sortBy} align="right" width="58px" title="Position on the board: highest fit first, companies out of the queue at the end" />
							<th class="c-mark" scope="col"><span class="sr-only">Company mark</span></th>
							<Th id="name" label="Company" sort={sort} {dir} onsort={sortBy} width="23%" />
							<Th id="fit" label="Fit" sort={sort} {dir} onsort={sortBy} width="118px" title="How strong the evidence is, and whether the queue will act on it" />
							<th class="c-sig" scope="col">Signals</th>
							<th class="c-trend" scope="col"><span class="sr-only">Signal trend over the last year</span></th>
							<Th id="docs" label="Docs" sort={sort} {dir} onsort={sortBy} align="right" width="56px" className="c-doc" />
							<Th id="newest" label="Latest" sort={sort} {dir} onsort={sortBy} align="right" width="66px" className="c-age" />
							<th class="c-star" scope="col"><span class="sr-only">Saved</span></th>
							<th class="c-act" scope="col"><span class="sr-only">Actions</span></th>
						</tr>
					</thead>
					<tbody>
						{#each current as row (row.id)}
							{@const move = movements.get(row.id)}
							<tr
								data-company={row.id}
								class:active={selected === row.id}
								class:rejected={row.band === 'DISQUALIFIED'}
								class:pickedrow={picked.has(row.id)}
								onclick={() => {
									closePeek();
									window.location.href = `/companies/${row.id}?line=${encodeURIComponent(row.serviceLine)}`;
								}}
								onmouseenter={() => openPeek(row)}
								onmouseleave={closePeek}
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
								<td class="c-mark">
									<CompanyMark name={row.name} domain={row.domain} />
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
									{#each row.profiles as name (name)}
										<span class="profile-tag" title="The customer profile this company falls into">{name}</span>
									{/each}
								</td>
								<td class="c-fit">
									{#if row.band === 'DISQUALIFIED'}
										<Tag band={row.band} title={row.gateReason ?? row.bandLabel}>Out of the queue</Tag>
										{#if row.gateReason}
											<span class="gate" title={row.gateReason}>{gateCause(row.gateReason)}</span>
										{/if}
									{:else if row.band === 'THIN'}
										<span class="fitval dsp" data-tone="weak" title="No document answers your questions about this company, so nothing can be said either way.">
											Unproven
										</span>
									{:else}
										<span
											class="fitval dsp"
											data-tone={fitTone(row.probability, thresholds)}
											title={meta
												? `Fit ${fit(row.probability)} — ${fitWord(row.probability, thresholds).toLowerCase()} evidence. A company in your market scores ${fit(meta.scoring.baseRate)} with no evidence at all.`
												: `Fit ${fit(row.probability)}`}
										>{fitWord(row.probability, thresholds)}</span>
										{#if row.themeCount === 1 && row.probability >= thresholds.priority}
											<span class="one-theme" title="The evidence is at the Priority line and stops there: one theme is all they have, and a single topic is capped so it cannot carry a company on its own.">
												capped · one theme
											</span>
										{/if}
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
												</li>
											{/each}
										</ul>
										{#if row.signalCount > top.length}
											<p class="sub dim">
												and {row.signalCount - top.length} more on the scorecard
											</p>
										{/if}
									{:else}
										<span class="sub dim">nothing on file answers your questions about them</span>
									{/if}
								</td>
								<td class="c-trend">
									<SignalTrace
										spark={row.spark ?? []}
										title={row.newestSignalAgeDays === null
											? 'Nothing dated has been read about this company'
											: `Signal strength by month for the last year. Most recent, ${ageShort(row.newestSignalAgeDays)}.`}
									/>
								</td>
								<td class="num c-doc">
									{#if row.evidenceCount === 0}
										<span class="blind" title="No document was ever found for this company. Nothing can be said either way.">no evidence</span>
									{:else}
										{row.evidenceCount}
									{/if}
								</td>
								<td class="num c-age">{ageShort(row.newestSignalAgeDays)}</td>
								<td class="c-star">
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
		<div class="scrim" role="presentation" onclick={() => select(null)}></div>
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

{#if peek && peeking}
	<aside class="peek" aria-label="Company preview" onmouseleave={closePeek}>
		<div class="peek-head">
			<CompanyMark name={peek.name} domain={peek.domain} size="md" />
			<div class="peek-ident">
				<span class="peek-name">{peek.name}</span>
				<span class="sub dim">
					{peek.industry ?? 'unclassified'} · {peek.country ?? '—'}{peek.sizeBand ? ` · ${peek.sizeBand}` : ''}
				</span>
			</div>
			<span class="verdict dsp" data-tone={fitTone(peek.probability, thresholds)}>
				{fitWord(peek.probability, thresholds)}
			</span>
		</div>

		<p class="peek-why">{peek.whyNow?.headline ?? 'Nothing to say about this company yet.'}</p>

		{#if peek.whyNow && peek.whyNow.triggers.length > 0}
			<ul class="peek-signals">
				{#each peek.whyNow.triggers.slice(0, 3) as row (row.evidenceId + row.spanStart)}
					<li>
						<span class="dot" data-polarity={row.polarity}></span>
						<span class="peek-q">{row.questionTitle}</span>
					</li>
				{/each}
			</ul>
		{/if}

		<div class="peek-foot">
			<span class="sub dim num">
				{plural(peek.scannedDocuments, 'document')} read · {plural(peek.themeCount, 'theme')}
			</span>
			<span class="spacer"></span>
			<span class="sub dim">Click for the full page</span>
		</div>
	</aside>
{/if}

{#if capture}
	<CaptureSheet evidenceId={capture.id} focus={capture.focus} onclose={() => (capture = null)} />
{/if}

<style>
	.topbar .sub {
		color: var(--color-ink-3);
	}

	.metrics {
		margin-left: auto;
	}

	.topbar-tools {
		margin-left: var(--spacing-4);
	}

	.ask {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-2);
		padding: var(--spacing-3) var(--spacing-4);
		background: var(--color-orange-wash);
		border-bottom: 1px solid var(--color-orange-line);
		flex: none;
	}

	.ask-row {
		display: flex;
		align-items: flex-end;
		gap: var(--spacing-3);
		flex-wrap: wrap;
	}

	.ask-input {
		flex: 1;
		min-width: 260px;
		display: flex;
		flex-direction: column;
		gap: var(--spacing-1);
	}

	.ask-input .textarea {
		background: var(--color-surface);
	}

	.ask-opts {
		display: flex;
		align-items: flex-end;
		gap: var(--spacing-2);
		flex: none;
	}

	.ask-opts .field .select {
		min-width: 108px;
	}

	.ask-read {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
		flex-wrap: wrap;
	}

	.ask-note {
		margin: 0;
	}

	.read-kind {
		font-size: var(--text-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.09em;
		padding: 1px 6px;
		border: 1px solid var(--color-orange-line);
		background: var(--color-surface);
		color: var(--color-orange-ink);
		white-space: nowrap;
	}

	.read-kind[data-kind='SIGNAL'] {
		border-color: var(--color-rule-strong);
		color: var(--color-ink-3);
	}

	.result-text {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-1);
		flex: 1;
		min-width: 0;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--spacing-1);
	}

	.chips .tag {
		background: var(--color-surface);
	}

	.result {
		display: flex;
		align-items: center;
		gap: var(--spacing-5);
		width: 100%;
	}

	.result .metrics {
		margin-left: 0;
	}

	.result-q {
		flex: 1;
		min-width: 0;
		font-size: var(--text-lg);
		color: var(--color-ink-2);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.toolbar {
		flex-wrap: wrap;
		row-gap: var(--spacing-2);
	}

	:global(.toolbar .menu-trigger .trigger-text) {
		max-width: 170px;
	}

	.search {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		flex: 1 1 200px;
		min-width: 150px;
		max-width: 300px;
		padding-left: var(--spacing-2);
		border: 1px solid var(--color-rule-strong);
		border-radius: 2px;
		background: var(--color-surface);
		color: var(--color-ink-3);
	}

	.search:focus-within {
		border-color: var(--color-orange);
		box-shadow: 0 0 0 2px var(--color-orange-wash);
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
		color: var(--color-ink-4);
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

	/**
	 * The scrim is for the overlay layout only.
	 *
	 * Above 1100px the pane is a column beside the board and there is nothing to
	 * dim — but the element was still in the DOM, and with no rule to take it out
	 * of the grid it took the second column for itself and pushed the pane onto
	 * an implicit second row, full width, under the board. Which is what it
	 * looked like: the pane replacing the page.
	 */
	.view .scrim {
		display: none;
		animation: fade-in var(--ease-enter) both;
	}

	.main {
		display: flex;
		flex-direction: column;
		min-width: 0;
		min-height: 0;
	}

	.view.split .c-doc,
	.view.split .c-age,
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

	.peek {
		position: fixed;
		left: 244px;
		bottom: var(--spacing-4);
		width: 380px;
		z-index: var(--z-index-menu);
		background: var(--color-surface);
		border: 1px solid var(--color-ink);
		padding: var(--spacing-3);
		animation: rise var(--ease-enter) both;
	}

	.peek-head {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
	}

	.peek-ident {
		flex: 1;
		min-width: 0;
	}

	.peek-name {
		display: block;
		font-family: var(--font-display);
		font-size: var(--text-lg);
		font-weight: 600;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.peek .verdict {
		flex: none;
		font-size: var(--text-md);
		color: var(--color-ink-2);
	}

	.peek .verdict[data-tone='strong'] {
		color: var(--color-orange-ink);
	}

	.peek .verdict[data-tone='weak'] {
		color: var(--color-ink-4);
	}

	.peek-why {
		margin: var(--spacing-2) 0 0;
		font-size: var(--text-base);
		line-height: 1.5;
		color: var(--color-ink-2);
	}

	.peek-signals {
		list-style: none;
		margin: var(--spacing-2) 0 0;
		padding: 0;
	}

	.peek-signals li {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
		padding: 2px 0;
	}

	.peek-signals .dot {
		width: 5px;
		height: 5px;
		flex: none;
		background: var(--color-accent);
	}

	.peek-signals .dot[data-polarity='NEGATIVE'],
	.peek-signals .dot[data-polarity='DISQUALIFYING'] {
		background: var(--color-oxblood);
	}

	.peek-q {
		font-size: var(--text-base);
		color: var(--color-ink-2);
	}

	.peek-foot {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
		margin-top: var(--spacing-3);
		padding-top: var(--spacing-2);
		border-top: 1px solid var(--color-rule);
	}

	.peek-foot .spacer {
		margin-left: auto;
	}

	.list {
		position: relative;
		flex: 1;
		min-height: 0;
		background: var(--color-surface);
	}

	.detail {
		animation: enter-right var(--ease-enter) both;
		min-height: 0;
		border-left: 1px solid var(--color-ink);
		background: var(--color-surface);
	}

	tbody tr {
		cursor: pointer;
		transition: background var(--ease);
	}

	tbody tr:hover {
		background: var(--color-paper);
	}

	tbody tr.active {
		background: var(--color-orange-wash);
		box-shadow: inset 3px 0 0 var(--color-orange);
	}

	tbody tr.pickedrow {
		background: var(--color-sunken);
	}

	tbody tr.rejected .co {
		color: var(--color-ink-3);
	}

	tbody tr.rejected .fitval {
		text-decoration: line-through;
		text-decoration-color: var(--color-oxblood);
		color: var(--color-ink-3);
	}

	.c-chk {
		width: 36px;
		padding-right: 0;
	}

	.c-rank {
		font-size: var(--text-base);
		color: var(--color-ink-3);
	}

	.c-mark {
		width: 34px;
		padding-right: 0;
	}

	.c-fit {
		vertical-align: top;
		min-width: 0;
	}

	.one-theme {
		display: block;
		margin-top: 2px;
		font-size: var(--text-micro);
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--color-ink-4);
	}

	.c-act {
		width: 36px;
		padding-left: 0;
	}

	.c-trend {
		width: 52px;
		padding-left: 0;
	}

	.c-star {
		width: 30px;
		padding-right: 0;
		text-align: center;
	}

	.co {
		display: block;
		font-family: var(--font-display);
		font-size: var(--text-lg);
		font-weight: 500;
		line-height: 1.2;
		letter-spacing: -0.01em;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.sub {
		display: block;
		font-size: var(--text-sm);
		color: var(--color-ink-3);
		line-height: 1.4;
	}

	.delta {
		display: inline-block;
		margin-left: 4px;
		font-size: var(--text-micro);
	}

	.delta[data-dir='up'] {
		color: var(--color-accent);
	}

	.delta[data-dir='down'] {
		color: var(--color-oxblood);
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

	.moved {
		list-style: none;
		margin: var(--spacing-3) 0 0;
		padding: 0;
		border-top: 1px solid var(--color-rule);
	}

	.moved-row {
		display: flex;
		align-items: center;
		gap: var(--spacing-3);
		width: 100%;
		padding: var(--spacing-2) var(--spacing-3);
		border: 0;
		border-bottom: 1px solid var(--color-rule);
		background: transparent;
		font: inherit;
		text-align: left;
		cursor: pointer;
	}

	.moved-row:hover {
		background: var(--color-sunken);
	}

	.moved-name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.moved-delta[data-dir='up'] {
		color: var(--color-accent);
	}

	.moved-delta[data-dir='down'] {
		color: var(--color-oxblood);
	}

	.ask-cues {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--spacing-2);
		padding: var(--spacing-2) var(--spacing-3);
		border-top: 1px solid var(--color-rule);
	}

	.blind {
		display: inline-block;
		margin-left: var(--spacing-2);
		padding: 0 var(--spacing-1);
		font-size: var(--text-micro);
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--color-ink-3);
		border: 1px dotted var(--color-rule-strong);
		border-radius: 2px;
		cursor: help;
	}

	.gate {
		display: block;
		margin-top: 3px;
		font-size: var(--text-micro);
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--color-oxblood);
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
		gap: var(--spacing-1);
		min-width: 0;
	}

	.evidence .dot {
		width: 5px;
		height: 5px;
		flex: none;
		transform: translateY(-1px);
		background: var(--color-accent);
	}

	.evidence .dot[data-polarity='NEGATIVE'] {
		background: var(--color-oxblood);
	}

	.evidence .dot[data-polarity='DISQUALIFYING'] {
		background: var(--color-oxblood);
	}

	.ev-title {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-size: var(--text-base);
		color: var(--color-ink-2);
	}

	.evidence.hot .ev-title {
		color: var(--color-ink);
	}

	.line-tag {
		color: var(--color-orange-ink);
		font-weight: 500;
	}

	.profile-tag {
		display: inline-block;
		margin: 3px 0 0 var(--spacing-1);
		font-size: var(--text-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		padding: 1px 5px;
		border: 1px solid var(--color-accent-line);
		background: var(--color-accent-wash);
		color: var(--color-accent);
	}

	.stage-tag {
		display: inline-block;
		margin-top: 3px;
		font-size: var(--text-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		padding: 1px 5px;
		border: 1px solid var(--color-rule-strong);
		color: var(--color-ink-3);
	}

	.stage-tag[data-stage='QUALIFIED'] {
		border-color: var(--color-accent);
		color: var(--color-accent);
	}

	.stage-tag[data-stage='REJECTED'] {
		border-color: var(--color-oxblood-line);
		color: var(--color-oxblood);
	}

	.stage-tag[data-stage='CONTACTED'],
	.stage-tag[data-stage='REPLIED'] {
		border-color: var(--color-orange-line);
		color: var(--color-orange-ink);
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

	}

	@media (max-width: 760px) {
		.topbar {
			flex-wrap: wrap;
			gap: var(--spacing-3);
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
		.list {
			overflow-x: auto;
		}
		table.tbl {
			min-width: 700px;
		}
		:global(.pager) {
			padding: var(--spacing-2);
			gap: var(--spacing-2);
		}
	}

	@media (max-width: 1100px) {
		.view.split {
			grid-template-columns: minmax(0, 1fr);
		}
		.view :global(.scrim) {
			display: block;
			position: absolute;
			inset: 0;
			z-index: 3;
			background: rgb(18 16 13 / 0.32);
		}
		.view.split .detail {
			position: absolute;
			top: 0;
			right: 0;
			bottom: 0;
			width: min(640px, 100%);
			z-index: 4;
			border-left: 1px solid var(--color-ink);
		}
	}</style>
