<script lang="ts">
	import { fetchIcpProfiles, fetchPeople, fetchTelemetry, type IcpProfile, type Telemetry } from '$lib/api';
	import { workspace } from '$lib/workspace.svelte';
	import { session } from '$lib/session.svelte';
	import { shortNumber } from '$lib/format';
	import Icon from '$lib/components/Icon.svelte';

	/** The desk is set up for one person; this is where it says so. */
	const USER = 'Cristian';

	let profiles = $state<IcpProfile[]>([]);
	let people = $state(0);
	let telemetry = $state<Telemetry | null>(null);

	const status = $derived(workspace.status);
	const bands = $derived(workspace.meta?.bands ?? {});
	const all = $derived(bands.ALL ?? 1);

	/** The queue in order, best first. Every band is measured against the whole
	 * corpus on its own baseline, so the one company that clears the Priority line
	 * looks like what it is: a sliver next to four hundred unproven ones. */
	const queue = $derived(
		[
			{ id: 'PRIORITY', name: 'Priority', count: bands.PRIORITY ?? 0, tone: 'hot' },
			{ id: 'NURTURE', name: 'Nurture', count: bands.NURTURE ?? 0, tone: '' },
			{ id: 'WATCH', name: 'Watch', count: bands.WATCH ?? 0, tone: '' },
			{ id: 'THIN', name: 'Unproven', count: bands.THIN ?? 0, tone: 'dim' },
			{ id: 'DISQUALIFIED', name: 'Out of the queue', count: bands.DISQUALIFIED ?? 0, tone: 'out' }
		].map((row) => ({ ...row, share: row.count / Math.max(1, all) }))
	);

	/** The books, biggest first — one series, so the lengths carry it and the names
	 * are read straight off the rows rather than out of a legend. */
	const books = $derived([...profiles].sort((a, b) => b.companies - a.companies));
	const widest = $derived(Math.max(1, ...books.map((book) => book.companies)));
	

	const coverage = $derived(telemetry?.coverage ?? null);
	const read = $derived(
		coverage ? Math.round((coverage.withDocuments / Math.max(1, coverage.companies)) * 100) : null
	);

	function age(hours: number | null | undefined): string {
		if (hours === null || hours === undefined) return '—';
		if (hours < 24) return `${Math.max(1, Math.round(hours))}h`;
		return `${Math.round(hours / 24)}d`;
	}

	const freshness = $derived(telemetry?.freshness?.newestCaptureAgeHours ?? null);

	/** A headline figure is read, not skimmed: 4,184 says more than 4.2k does. */
	function exact(value: number): string {
		return value.toLocaleString('en-US');
	}

	/** A small count is still a bar, so it gets a floor of two pixels. A count of
	 * zero gets nothing at all — a sliver drawn for nothing is a lie the eye reads
	 * as a small number. */
	function width(value: number, most: number): string {
		if (value <= 0) return '0';
		return `max(2px, ${Math.min(100, (value / Math.max(1, most)) * 100)}%)`;
	}

	const checked = $derived(
		status?.checkedAt
			? new Date(status.checkedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
			: '—'
	);

	$effect(() => {
		const line = session.serviceLine;
		fetchIcpProfiles()
			.then((list) => (profiles = list))
			.catch(() => (profiles = []));
		fetchTelemetry()
			.then((result) => (telemetry = result))
			.catch(() => (telemetry = null));
		if (!line) return;
		let cancelled = false;
		fetchPeople(line)
			.then((list) => {
				if (!cancelled) people = list.length;
			})
			.catch(() => {
				if (!cancelled) people = 0;
			});
		return () => {
			cancelled = true;
		};
	});
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<p class="label kicker">Signal Desk · {session.serviceLine}</p>
			<h1 class="dsp masthead">Welcome, {USER}</h1>
		</div>
		<div class="topbar-tools">
			<a class="btn btn-primary" href="/companies">
				<Icon name="board" size={15} />
				Open the board
			</a>
		</div>
	</header>

	<div class="body scroll">
		<section class="corpus" aria-label="What the desk holds">
			<div class="figure">
				<span class="figure-val num">{exact(status?.companies ?? 0)}</span>
				<span class="figure-lab">Companies watched</span>
			</div>
			<div class="figure">
				<span class="figure-val num">{exact(status?.captures ?? 0)}</span>
				<span class="figure-lab">Documents read</span>
			</div>
			<div class="figure">
				<span class="figure-val num">{exact(status?.signals ?? 0)}</span>
				<span class="figure-lab">Signals found</span>
			</div>
			<div class="figure">
				<span class="figure-val num accent">{exact(profiles.length)}</span>
				<span class="figure-lab">Books of business</span>
			</div>
		</section>

		<div class="columns">
			<section class="panel" aria-label="Where the queue stands">
				<div class="panel-head">
					<h2 class="dsp">Where the queue stands</h2>
					<span class="sub dim">
						{shortNumber(bands.HOT ?? 0)} hot of {shortNumber(all)} · {session.serviceLine}
					</span>
				</div>
				<ul class="bars">
					{#each queue as row (row.id)}
						<li class="bar-row" data-tone={row.tone || undefined} title={`${row.count} companies · ${(row.share * 100).toFixed(1)}% of the corpus`}>
							<span class="bar-name">{row.name}</span>
							<span class="bar-track">
								<span class="bar-fill" style={`width:${width(row.count, all)}`}></span>
							</span>
							<span class="bar-val num">{shortNumber(row.count)}</span>
						</li>
					{/each}
				</ul>
				<p class="sub dim footnote">
					Every company is measured against the same baseline. The queue is short because the evidence is thin,
					not because the filter is.
				</p>
			</section>

			<section class="panel" aria-label="What is read">
				<div class="panel-head"><h2 class="dsp">How much of it is read</h2></div>
				<dl class="facts">
					<div class="fact">
						<dt>With a document</dt>
						<dd class="num">{read === null ? '—' : `${read}%`}</dd>
					</div>
					<div class="fact">
						<dt>Documents each</dt>
						<dd class="num">{coverage ? coverage.documentsPerCompany.toFixed(1) : '—'}</dd>
					</div>
					<div class="fact">
						<dt>Nothing stored for</dt>
						<dd class="num">{shortNumber(coverage?.withoutDocuments ?? 0)}</dd>
					</div>
					<div class="fact">
						<dt>Newest capture</dt>
						<dd class="num">{age(freshness)}</dd>
					</div>
					<div class="fact">
						<dt>Named contacts</dt>
						<dd class="num">{shortNumber(people)}</dd>
					</div>
				</dl>
				<p class="sub dim footnote">
					Reading with {status?.engine ?? '—'}{status?.languageModelAvailable
						? ', the model reachable'
						: ', the model not configured — reads stay local and offline'}. Checked at {checked}.
				</p>
			</section>
		</div>

		<section class="panel" aria-label="Books of business">
			<div class="panel-head">
				<h2 class="dsp">Books of business</h2>
				<span class="sub dim">{books.length} books · the board shows the ones switched on</span>
			</div>
			<ul class="bars books">
				{#each books as book (book.id)}
					<li class="bar-row" title={book.name}>
						<span class="bar-name">
							<span class="book-dot" style={`background:${book.color ?? 'var(--color-ink-4)'}`}></span>
							{book.name}
						</span>
						<span class="bar-track"><span class="bar-fill" style={`width:${width(book.companies, widest)}`}></span></span>
						<span class="bar-val num">{shortNumber(book.companies)}</span>
					</li>
				{/each}
			</ul>
			<p class="sub dim footnote">
				<a class="link" href="/icp">The ICP Studio</a> is where these are drawn, coloured and switched on.
			</p>
		</section>

		<section class="panel onward" aria-label="Where to go">
			<a class="door" href="/companies">
				<span class="door-name">Companies</span>
				<span class="sub dim">The queue, with the arithmetic behind every score</span>
				<Icon name="chevronRight" size={14} />
			</a>
			<a class="door" href="/people">
				<span class="door-name">People</span>
				<span class="sub dim">Who to ring, and what they have said lately</span>
				<Icon name="chevronRight" size={14} />
			</a>
			<a class="door" href="/icp">
				<span class="door-name">ICP Studio</span>
				<span class="sub dim">The books, and what each one holds</span>
				<Icon name="chevronRight" size={14} />
			</a>
		</section>

		{#if status && (status.postgres !== 'up' || status.mongo !== 'up')}
			<p class="note warn">
				<Icon name="alert" size={14} />
				<span>A store is down — postgres {status.postgres}, mongo {status.mongo}. Figures may be stale.</span>
			</p>
		{/if}
	</div>
</div>

<style>
	.kicker {
		color: var(--color-ink-3);
		letter-spacing: 0.08em;
		text-transform: uppercase;
	}

	/* Boosted's h1 step. The token set stops at 22px, and a welcome that has to
	   land from the back of a room needs the display step the brand already has. */
	.masthead {
		font-size: 40px;
		line-height: 1.05;
	}

	.body {
		flex: 1;
		min-height: 0;
		padding: var(--spacing-5) var(--spacing-5) var(--spacing-6);
		display: flex;
		flex-direction: column;
		gap: var(--spacing-6);
	}

	/* ── the four figures, on one rule, nothing boxing them ───────────────── */
	.corpus {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
		border-top: 2px solid var(--color-ink);
		border-bottom: 2px solid var(--color-rule);
	}

	.figure {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-1);
		padding: var(--spacing-4) var(--spacing-5) var(--spacing-4) 0;
		border-left: 2px solid var(--color-rule);
	}

	.figure:first-child {
		border-left: 0;
	}

	.figure-val {
		font-size: 40px;
		font-weight: 700;
		line-height: 1;
		letter-spacing: -0.02em;
	}

	.figure-val.accent {
		color: var(--color-orange-deep);
	}

	.figure-lab {
		font-size: var(--text-micro);
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--color-ink-3);
	}

	.columns {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
		gap: var(--spacing-6);
		align-items: start;
	}

	.panel {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-3);
	}

	.panel-head {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-3);
		border-bottom: 2px solid var(--color-ink);
		padding-bottom: var(--spacing-2);
	}

	.panel-head h2 {
		font-size: var(--text-xl);
	}

	/* ── bars: one hue, length is the only encoding, every value printed ───── */
	.bars {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}

	.bar-row {
		display: grid;
		grid-template-columns: minmax(120px, 32%) minmax(0, 1fr) 64px;
		align-items: center;
		gap: var(--spacing-3);
		padding: var(--spacing-1) 0;
	}

	.bar-name {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		font-size: var(--text-base);
		color: var(--color-ink-2);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.bar-track {
		height: 14px;
		background: var(--color-sunken);
		display: flex;
	}

	.bar-fill {
		background: var(--color-orange);
		transition: width var(--ease);
	}

	.bar-val {
		text-align: right;
		font-size: var(--text-base);
		font-weight: 700;
	}

	.bar-row[data-tone='dim'] .bar-fill {
		background: var(--color-ink-4);
	}

	.bar-row[data-tone='dim'] .bar-val {
		color: var(--color-ink-3);
		font-weight: 400;
	}

	.bar-row[data-tone='out'] .bar-fill {
		background: var(--color-oxblood);
	}

	.bar-row[data-tone='out'] .bar-val {
		color: var(--color-oxblood);
	}

	/* Facts, not a chart: none of these four is a share of anything, and a bar
	   with nothing in it reads as a zero rather than as "not applicable". */
	.facts {
		margin: 0;
		display: flex;
		flex-direction: column;
	}

	.fact {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--spacing-3);
		padding: var(--spacing-2) 0;
		border-bottom: 1px solid var(--color-rule);
	}

	.fact:last-child {
		border-bottom: 0;
	}

	.fact dt {
		font-size: var(--text-base);
		color: var(--color-ink-2);
	}

	.fact dd {
		margin: 0;
		font-size: var(--text-lg);
		font-weight: 700;
	}

	/* Twenty books in one column put the name and its bar most of a screen apart.
	   Two columns of ten keep the label next to the length it belongs to, and the
	   bars thinner, because twenty full-width saturated blocks is a wall and not a
	   reading. */
	.books {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
		gap: var(--spacing-2) var(--spacing-6);
	}

	.books .bar-row {
		grid-template-columns: minmax(150px, 46%) minmax(0, 1fr) 40px;
	}

	.books .bar-track {
		height: 10px;
	}

	.book-dot {
		width: 10px;
		height: 10px;
		flex: none;
	}

	.footnote {
		max-width: 62ch;
	}

	/* ── the way onward ───────────────────────────────────────────────────── */
	.onward {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
		gap: var(--spacing-3);
	}

	.door {
		display: grid;
		grid-template-columns: 1fr auto;
		align-items: center;
		gap: var(--spacing-1) var(--spacing-3);
		padding: var(--spacing-3) var(--spacing-4);
		border: 2px solid var(--color-rule-strong);
	}

	.door:hover {
		border-color: var(--color-orange);
		background: var(--color-orange-wash);
	}

	.door-name {
		grid-column: 1;
		font-family: var(--font-display);
		font-weight: 700;
		font-size: var(--text-lg);
	}

	.door .sub {
		grid-column: 1;
	}

	.door :global(svg) {
		grid-row: 1 / span 2;
		grid-column: 2;
	}

	.note {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
	}

	@media (max-width: 720px) {
		.masthead {
			font-size: 30px;
		}

		.figure-val {
			font-size: 30px;
		}
	}
</style>
