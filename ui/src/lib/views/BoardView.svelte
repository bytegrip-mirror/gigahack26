<script lang="ts">
	import NotificationBell from '$lib/components/NotificationBell.svelte';
	import { untrack } from 'svelte';
	import { pushState } from '$app/navigation';
	import {
		addBookmark,
		fetchBoard,
		fetchBookmarks,
		fetchCompany,
		recordLead,
		removeBookmark,
		rescore,
		type BoardRow,
		type Chip
	} from '$lib/api';
	import { markCompetitor } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import { ageShort, duration, fit, fitShade, fitTone, fitWord, precise, shortNumber } from '$lib/format';
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
	import SignalTrace from '$lib/components/SignalTrace.svelte';
	import FitBar from '$lib/components/FitBar.svelte';
	import Pager from '$lib/components/Pager.svelte';
	import Th from '$lib/components/Th.svelte';

	let {
	initialSelected = null,
	initialLine = null
}: { initialSelected?: string | null; initialLine?: string | null } = $props();

	const FILTERS = [
		{ id: 'to-call', label: 'To contact', title: 'Worth calling: above the evidence floor and still in the queue.' },
		{ id: 'priority', label: 'Priority', title: 'Above the Priority line' },
		{ id: 'thin', label: 'Unproven', title: 'No document answers your questions about them' },
		{ id: 'rejected', label: 'Out of the queue', title: 'Removed by a gate or the blacklist' },
		{ id: 'saved', label: 'Saved', title: 'Bookmarked' },
		{ id: 'everyone', label: 'Everyone', title: 'Every company we hold, whatever the queue decided — still only the ones nobody has worked. Set the lead stage under Filters to bring the worked ones back.' }
	];

	const STAGES = [
		{ id: '', label: 'Any lead stage' },
		{ id: 'worked', label: 'Worked' },
		{ id: 'untouched', label: 'Untouched' },
		{ id: 'CONTACTED', label: 'Contacted' },
		{ id: 'REPLIED', label: 'Replied' },
		{ id: 'QUALIFIED', label: 'Qualified' },
		{ id: 'REJECTED', label: 'Rejected' }
	];

	const ALL_LINES = '__all__';

	type ScopedRow = BoardRow & { serviceLine: string; fitRank: number };

	function worked(row: BoardRow): boolean {
		return row.leadStatus !== null && row.leadStatus !== 'NEW';
	}

	const VIEWS: Record<string, (row: BoardRow) => boolean> = {
		'to-call': (row) => row.band !== 'DISQUALIFIED' && row.band !== 'THIN',
		priority: (row) => row.band === 'PRIORITY',
		thin: (row) => row.band === 'THIN',
		rejected: (row) => row.band === 'DISQUALIFIED',
		saved: (row) => saved.has(row.id),
		everyone: () => true
	};

	/**
	 * The board is the work you have not done yet.
	 *
	 * A company somebody has already rung, or qualified, or turned down, belongs to
	 * the pipeline — that is what the pipeline is for, and leaving them here meant
	 * every view, including `Everyone`, quietly mixed the two together. The lead
	 * stage under Filters is the way back: name a stage and the board shows exactly
	 * those.
	 */
	function passesStage(row: BoardRow): boolean {
		if (stage === '' || stage === 'untouched') return !worked(row);
		if (stage === 'worked') return worked(row);
		return row.leadStatus === stage;
	}

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
	let profileFilter = $state<Set<string>>(new Set());
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

	let capture = $state<{ id: string; focus: { start: number; end: number } | null } | null>(null);

	const meta = $derived(workspace.meta);
	const serviceLine = $derived(session.serviceLine);
	const subtitle = $derived.by(() => {
		const total = shortNumber(rows.length);
		if (stage !== '' && stage !== 'untouched') {
			return `${shortNumber(sorted.length)} ${STAGES.find((entry) => entry.id === stage)?.label.toLowerCase() ?? ''} of ${total}`;
		}
		if (filter === 'everyone') {
			return `${shortNumber(sorted.length)} of ${total} companies, the ones not yet worked`;
		}
		const view = FILTERS.find((option) => option.id === filter)?.label.toLowerCase() ?? 'companies';
		return `${shortNumber(sorted.length)} ${view} of ${total} not yet worked`;
	});

	const narrowed = $derived(
		session.region !== 'world' ||
			stage !== '' ||
			query !== '' ||
			filter !== 'to-call' ||
			engineFilter !== 'all' ||
			profileFilter.size > 0
	);
	const activeFilters = $derived(
		(stage === '' ? 0 : 1) + (engineFilter === 'all' ? 0 : 1) + profileFilter.size
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
		{ id: 'head-engine', label: 'Read by', heading: true, separatorBefore: true },
		...(
			[
				{ id: 'all', label: 'Any engine' },
				{ id: 'model', label: 'The model' },
				{ id: 'local', label: 'The rules' }
			] as const
		).map((option) => ({ id: `engine:${option.id}`, label: option.label, checked: engineFilter === option.id }))
	]);

	/**
	 * Split on the first colon only. Splitting on every one threw away the rest of
	 * the name, which made a profile called "DACH: mid-market" impossible to pick.
	 */
	function runFilter(id: string) {
		const at = id.indexOf(':');
		const kind = at === -1 ? id : id.slice(0, at);
		const value = at === -1 ? '' : id.slice(at + 1);
		if (kind === 'stage') stage = value;
		else if (kind === 'icp') toggleProfile(value);
		else if (kind === 'icp-clear') {
			profileFilter = new Set();
			page = 1;
		} else engineFilter = value as 'all' | 'model' | 'local';
	}

	/** Several books at once, and switching on none shows everything again. */
	function toggleProfile(id: string) {
		const profile = profiles.find((entry) => entry.id === id);
		if (!profile) return;
		const next = new Set(profileFilter);
		if (next.has(profile.name)) next.delete(profile.name);
		else next.add(profile.name);
		profileFilter = next;
		page = 1;
	}
	const allLines = $derived(session.allLines);
	const scopeItems = $derived([
		{
			id: ALL_LINES,
			label: 'All service lines',
			/*
			 * The live board's own count, not the per-line counts added up. A company
			 * can be ready to call for more than one line, and summing answers "how
			 * many line-and-company pairs", which claimed ten companies where the
			 * board — which keeps one row per company — showed six. Falls back to the
			 * sum until the merged rows land, so the menu is never blank.
			 */
			hint: String(
				allLines && rows.length > 0
					? rows.filter((row) => row.band === 'PRIORITY').length
					: Object.values(workspace.counts).reduce((sum, count) => sum + count, 0)
			),
			checked: allLines
		},
		...workspace.lines.map((line) => ({
			id: line,
			label: line,
			hint: workspace.counts[line] === undefined ? '' : String(workspace.counts[line]),
			checked: !allLines && line === serviceLine
		})),
		...(profiles.length > 0
			? [
					{ id: 'head-icp', label: 'ICP Profiles', heading: true, separatorBefore: true },
					...profiles.map((profile) => ({
						id: `icp:${profile.id}`,
						label: profile.name,
						hint: profile.companies === 1 ? '1 company' : `${shortNumber(profile.companies)} companies`,
						checked: profileFilter.has(profile.name)
					})),
					...(profileFilter.size > 0
						? [{ id: 'icp-clear', label: 'Any profile', icon: 'close' as const }]
						: [])
				]
			: [])
	]);

	/**
	 * One control for what the board is looking at: a service line ranks it, an ICP
	 * profile narrows it, and several profiles can be on at once. Two controls for
	 * one question is how the two of them start disagreeing.
	 */
	function runScope(id: string) {
		if (id === ALL_LINES) {
			session.setAll();
			return;
		}
		if (id === 'icp-clear') {
			profileFilter = new Set();
			page = 1;
			return;
		}
		if (id.startsWith('icp:')) {
			toggleProfile(id.slice(4));
			return;
		}
		void workspace.select(id);
	}

	const scopeText = $derived(
		profileFilter.size === 0
			? session.scopeLabel()
			: `${session.scopeLabel()} · ${profileFilter.size} ${profileFilter.size === 1 ? 'profile' : 'profiles'}`
	);

	const localEngine = $derived(workspace.meta?.localEngine ?? 'local-rubric-v1');

	const thresholds = $derived({
		watch: meta?.scoring.watchThreshold ?? 0.28,
		nurture: meta?.scoring.nurtureThreshold ?? 0.45,
		priority: meta?.scoring.priorityThreshold ?? 0.55
	});

	const visible = $derived.by(() => {
		const wanted = VIEWS[filter] ?? VIEWS.everyone;
		return rows.filter((row) => {
			const passesBand =
				wanted(row) &&
				passesStage(row) &&
				(profileFilter.size === 0 || row.profiles.some((name) => profileFilter.has(name)));
			const passesEngine =
				engineFilter === 'all' ||
				(row.engine !== null &&
					(engineFilter === 'model' ? row.engine !== localEngine : row.engine === localEngine));
			return passesBand && passesEngine;
		});
	});

	const savedCount = $derived(rows.filter((row) => saved.has(row.id)).length);


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
			stage: '',
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
		/*
		 * Read synchronously, so the effect re-runs when the line list arrives.
		 * `loadBoard` reads it too, but it runs inside a timeout, where the read is
		 * not tracked — so on a fresh load, with the list still empty, the board
		 * quietly fetched one line and stayed on it while the picker said every
		 * line. Opening on "All service lines" showed a single line's queue.
		 */
		void workspace.lines.length;
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

	function select(id: string | null) {
		selected = id;
		echoed = id;
		pushState(id ? `/companies?co=${id}&line=${encodeURIComponent(lineOf(id))}` : '/companies', {});
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

	/*
	 * Two, not three. A signal title wraps to two lines in this column, so a third
	 * one made that row twice the height of the rows around it — and a board is
	 * read down the left edge, where a tall row is a row the eye has to stop on
	 * for a reason that turns out to be "there was more to say". The rest are on
	 * the scorecard, one click away, and the count of them is printed underneath.
	 */
	const SIGNALS_PER_ROW = 2;

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
			.slice(0, SIGNALS_PER_ROW);
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
		profileFilter = new Set();
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
			const ok = await copy(`${window.location.origin}/companies?co=${row.id}`);
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

		<div class="topbar-tools">
			<Menu items={secondary} onselect={runSecondary} label="More board actions" />
			<NotificationBell />
		</div>
	</header>

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
					const ok = await copy([...picked].map((id) => `${window.location.origin}/companies?co=${id}`).join('\n'));
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
			<label class="search-field">
				<span class="sr-only">Filter by name, country or industry</span>
				<Icon name="search" size={14} />
				<input
					class="input input-sm"
					type="search"
					placeholder="Name, country, industry"
					bind:value={query}
				/>
			</label>
		</div>
		<div class="toolbar">
			<Menu
				text={scopeText}
				icon="layers"
				align="start"
				searchable
				wide
				label="What the board shows"
				title="The service line it is ranked for, and the ICP profiles it is narrowed to"
				items={scopeItems}
				onselect={runScope}
			/>
			{#if workspace.switching}<span class="spinner"></span>{/if}

			<div class="tabs" role="tablist" aria-label="Which companies to show">
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
						Nothing here. This board leaves out everyone you have already worked. Set the lead stage under
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
							<Th id="name" label="Company" sort={sort} {dir} onsort={sortBy} width="30%" />
							<Th id="fit" label="Fit" sort={sort} {dir} onsort={sortBy} width="136px" title="How strong the evidence is, and whether the queue will act on it" />
							<th class="c-stage" scope="col" title="Where the lead stands: new, contacted, replied, qualified or rejected">Stage</th>
							<th class="c-sig" scope="col">Signals</th>
							<th class="c-trend" scope="col" title="When the signals arrived over the last year">Trend</th>
							<Th id="docs" label="Docs" sort={sort} {dir} onsort={sortBy} align="right" width="56px" className="c-doc" />
							<Th id="newest" label="Latest" sort={sort} {dir} onsort={sortBy} align="right" width="66px" className="c-age" />
							<th class="c-star" scope="col"><span class="sr-only">Saved</span></th>
							<th class="c-act" scope="col"><span class="sr-only">Actions</span></th>
						</tr>
					</thead>
					<tbody>
						{#each current as row (row.id)}
							<tr
								data-company={row.id}
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
										<span class="fitline">
											<span
												class="fitval dsp"
												data-tone={fitTone(row.probability, thresholds)}
												title={meta
													? `Fit ${fit(row.probability)} — ${fitWord(row.probability, thresholds).toLowerCase()} evidence. A company in your market scores ${fit(meta.scoring.baseRate)} with no evidence at all.`
													: `Fit ${fit(row.probability)}`}
											>{fitWord(row.probability, thresholds)}</span>
											<span class="fitnum num dsp" title="The reading itself">{fit(row.probability)}</span>
										</span>
										<FitBar value={row.probability} shade={fitShade(row.probability, thresholds)} />
										{#if row.themeCount === 1 && row.probability >= thresholds.priority}
											<span class="one-theme" title="The evidence is at the Priority line and stops there: one theme is all they have, and a single topic is capped so it cannot carry a company on its own.">
												capped · one theme
											</span>
										{/if}
									{/if}
								</td>
								<td class="c-stage">
									<span class="stage-tag" data-stage={row.leadStatus ?? 'NEW'}>
										{row.leadStatusLabel ?? 'New'}{#if row.contactCount > 1} ×{row.contactCount}{/if}
									</span>
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
											: `When the signals arrived over the last year. Latest, ${ageShort(row.newestSignalAgeDays)}.`}
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

{#if capture}
	<CaptureSheet evidenceId={capture.id} focus={capture.focus} onclose={() => (capture = null)} />
{/if}

<style>
	.topbar .sub {
		color: var(--color-ink-3);
	}

	.topbar-tools {
		margin-left: var(--spacing-4);
	}

	.toolbar {
		flex-wrap: wrap;
		row-gap: var(--spacing-2);
	}

	:global(.toolbar .menu-trigger .trigger-text) {
		max-width: 170px;
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

	.view.split .c-trend,
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
		/* Centred with the company beside it. The mark is the first character of
		   the name, not a label for the row — pinned to the top of a tall row it
		   floated above the words it belongs to. */
		vertical-align: middle;
	}

	.c-fit {
		/* Centred, not top and not bottom. The signals list beside it runs from
		   one line to four, so the row is as tall as that list; pinned to either
		   end, the word and its meter drifted away from the middle of the row they
		   describe. */
		vertical-align: middle;
		min-width: 0;
	}

	.one-theme {
		display: block;
		margin-top: 2px;
		font-size: var(--text-micro);
		color: var(--color-ink-4);
	}

	/* The trailing columns — the document count, the age, the star and the menu —
	   are one short value each, and they take the same centring the verdict
	   columns did. Pinned to the top of a tall row they read as belonging to the
	   first line of the signals list rather than to the row. */
	.c-doc,
	.c-age,
	.c-star,
	.c-act {
		vertical-align: middle;
	}

	.c-act {
		width: 36px;
		padding-left: 0;
	}

	/*
	 * A table cell ignores max-width in auto layout, so the cap goes on what is
	 * inside it: without this the signals column swallows every spare pixel on a
	 * wide screen and the company names lose the space instead.
	 */
	/*
	 * Auto table layout hands every spare pixel to the column that asks for
	 * nothing, and a max-width on the cell is ignored, so on a wide screen this
	 * column swallowed 740px to show 250px of text while the company names
	 * squeezed. Fixed layout makes the widths the columns actually declare mean
	 * something.
	 */
	.list table.tbl {
		table-layout: fixed;
	}

	/*
	 * A measure, not a bucket, and narrower than it looks like it needs to be.
	 *
	 * The titles wrap to two lines here and that is the point: the dead space a
	 * reader sees is inside this cell, between the end of the text and the edge,
	 * and it grows with the screen — 97px at 1600, 264px at 2800. A title that
	 * wraps costs a line; a title with a third of a screen of nothing beside it
	 * costs the whole row's credibility as a dense list.
	 */
	.c-sig {
		width: 230px;
		min-width: 0;
	}

	/*
	 * The titles wrap now, and `flex-start` put the dot level with the top of the
	 * first line box rather than on its baseline — the mark was visibly above the
	 * words it belongs to. A baseline is a baseline however many lines follow it.
	 */
	.c-sig .evidence li {
		align-items: baseline;
	}

	.ev-title {
		overflow-wrap: anywhere;
	}

	.c-co {
		overflow-wrap: anywhere;
		vertical-align: middle;
	}

	/*
	 * No width, on purpose. With `table-layout: fixed` the column that declares no
	 * width takes what the others leave, and that used to be this one's neighbour:
	 * the signals column measured 222px at 1600 and 1062px at 2800 while the graph
	 * stayed at 180 — a field of white to the right of titles that needed a third
	 * of it. A graph is the thing on this row that is worth more room; a title is
	 * not improved by being given more than it wraps in.
	 *
	 * Bottom, not top: the trace has an x-axis at its foot, and a row three lines
	 * tall left that axis floating in the middle of the cell.
	 */
	.c-trend {
		/*
		 * No width at all, and it is the only column that has none — which is what
		 * makes it take everything the others leave. A percentage looked right and
		 * measured wrong: with every column declaring a width, a fixed-layout table
		 * spreads the leftover over all of them proportionally, so the signals
		 * column kept growing (340 → 507) and the graph only grew a little. One
		 * column without a width is the only way to name the column that absorbs
		 * the slack.
		 *
		 * `min-width` is not an option here: a fixed-layout table sizes columns
		 * from the first row's declared widths and ignores the minimum, which I
		 * tried and measured — the graph collapsed to 62px on a laptop.
		 */
		vertical-align: bottom;
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

	/* The word and the reading on one line, adjacent: the word is what a rep says
	   out loud and the number is what they check it against, so the two belong
	   together rather than at opposite ends of the cell. */
	.fitline {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
	}

	/* The reading wears the classifier's own face and size, and gives up only
	   colour: the word leads and the number follows it, but the pair reads as one
	   label. Tabular figures from `.num` keep the digits in a column. */
	.fitnum {
		font-size: var(--text-lg);
		color: var(--color-ink-3);
		white-space: nowrap;
	}

	.fitval {
		display: block;
		font-size: var(--text-lg);
		line-height: 1.2;
		color: var(--color-ink-2);
		margin-bottom: 3px;
	}

	.fitval[data-tone='strong'] {
		color: var(--color-orange-ink);
	}

	.fitval[data-tone='weak'] {
		color: var(--color-ink-3);
	}

	.blind {
		display: inline-block;
		margin-left: var(--spacing-2);
		padding: 0 var(--spacing-1);
		font-size: var(--text-micro);
		color: var(--color-ink-3);
		border: 1px dotted var(--color-rule-strong);
		border-radius: var(--radius);
		cursor: help;
	}

	.gate {
		display: block;
		margin-top: 3px;
		font-size: var(--text-micro);
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
		font-size: var(--text-base);
		line-height: 1.35;
		color: var(--color-ink-2);
	}

	.evidence.hot .ev-title {
		color: var(--color-ink);
	}

	.line-tag {
		color: var(--color-orange-ink);
		font-weight: 500;
	}

	.c-stage {
		width: 104px;
		/* Centred with the fit column beside it: the two are the row's verdict —
		   what we make of the company and where it has got to — so they sit on the
		   same line however tall the signals list makes the row. */
		vertical-align: middle;
	}

	.stage-tag {
		display: inline-block;
		font-size: var(--text-micro);
		font-weight: 700;
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

	/*
	 * A laptop does not fit this table. At 1366 the columns already added up to
	 * more than the width, and the browser squeezed whatever it could — which,
	 * once the graph was the column that took the slack, meant the graph collapsed
	 * to 8px and disappeared. The two count columns go first: a document count and
	 * an age are the only things on this row a rep can get anywhere else, and the
	 * graph is the thing the row is read for.
	 */
	@media (max-width: 1500px) {
		.c-doc,
		.c-age,
		:global(th.c-doc),
		:global(th.c-age) {
			display: none;
		}
	}

	@media (max-width: 1180px) {
		.c-trend {
			display: none;
		}
		.view.split {
			grid-template-columns: minmax(0, 1fr) 400px;
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
		.toolbar .tabs {
			flex: 1 1 100%;
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
			background: rgb(0 0 0 / 0.32);
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
