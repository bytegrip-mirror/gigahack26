<script lang="ts">
	import { fetchBoard, type BoardRow } from '$lib/api';
	import { session } from '$lib/session.svelte';
	import { shortNumber, theme } from '$lib/format';
	import logos from '$lib/logos.json';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Tag from '$lib/components/Tag.svelte';
	import Icon from '$lib/components/Icon.svelte';

	const THEME_X = 16;
	const QUESTION_X = 300;
	const COMPANY_X = 700;
	const QUESTION_W = 260;
	const COMPANY_W = 288;
	const ROW = 48;
	const NODE_H = 34;
	const SHOWN_COMPANIES = 16;
	const TITLE_CHARS = 38;
	const WIDTH = COMPANY_X + COMPANY_W + 16;

	type Question = {
		key: string;
		title: string;
		text: string;
		theme: string;
		polarity: string;
		companies: Map<string, number>;
	};

	type Company = {
		id: string;
		name: string;
		domain: string | null;
		band: string;
		questions: string[];
		weight: number;
	};

	const files = logos as Record<string, string>;

	let rows = $state<BoardRow[]>([]);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let asTable = $state(false);
	let hovered = $state<string | null>(null);

	const serviceLine = $derived(session.serviceLine);

	$effect(() => {
		const line = serviceLine;
		if (!line) return;
		let cancelled = false;
		loading = true;
		fetchBoard({ serviceLine: line })
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

	const questions = $derived.by(() => {
		const map = new Map<string, Question>();
		for (const row of rows) {
			for (const chip of row.chips) {
				const held = map.get(chip.questionKey) ?? {
					key: chip.questionKey,
					title: chip.questionTitle,
					text: chip.question,
					theme: chip.theme,
					polarity: chip.polarity,
					companies: new Map<string, number>()
				};
				held.companies.set(row.id, (held.companies.get(row.id) ?? 0) + 1);
				map.set(chip.questionKey, held);
			}
		}
		return [...map.values()].sort(
			(a, b) => a.theme.localeCompare(b.theme) || b.companies.size - a.companies.size
		);
	});

	const ranked = $derived.by(() => {
		const seen = new Map<string, Company>();
		const byId = new Map(rows.map((row) => [row.id, row]));
		for (const question of questions) {
			for (const [id, count] of question.companies) {
				const row = byId.get(id);
				if (!row) continue;
				const held = seen.get(id) ?? {
					id,
					name: row.name,
					domain: row.domain,
					band: row.band,
					questions: [],
					weight: 0
				};
				held.questions.push(question.key);
				held.weight += count;
				seen.set(id, held);
			}
		}
		return [...seen.values()].sort(
			(a, b) =>
				b.questions.length - a.questions.length ||
				b.weight - a.weight ||
				a.name.localeCompare(b.name)
		);
	});

	const companies = $derived(ranked.slice(0, SHOWN_COMPANIES));
	const hidden = $derived(ranked.length - companies.length);

	const groups = $derived.by(() => {
		const seen = new Map<string, string[]>();
		for (const question of questions) {
			seen.set(question.theme, [...(seen.get(question.theme) ?? []), question.key]);
		}
		return [...seen.entries()].map(([name, keys]) => ({ name, keys }));
	});

	const layout = $derived.by(() => {
		const questionY = new Map<string, number>();
		questions.forEach((question, index) => questionY.set(question.key, ROW * (index + 1.6)));

		const themeY = new Map<string, { at: number; first: number; last: number }>();
		for (const group of groups) {
			const ys = group.keys.map((key) => questionY.get(key) ?? 0);
			themeY.set(group.name, {
				at: ys.reduce((sum, y) => sum + y, 0) / ys.length,
				first: Math.min(...ys),
				last: Math.max(...ys)
			});
		}

		const companyY = new Map<string, number>();
		const order = [...companies];
		for (let pass = 0; pass < 6; pass++) {
			order.sort((a, b) => centre(a, questionY) - centre(b, questionY));
			order.forEach((company, index) => companyY.set(company.id, ROW * (index + 1.6)));
		}

		return { questionY, themeY, companyY, order };
	});

	function centre(company: Company, questionY: Map<string, number>): number {
		const at = company.questions.map((key) => questionY.get(key) ?? 0);
		return at.reduce((sum, y) => sum + y, 0) / (at.length || 1);
	}

	const height = $derived(ROW * 2.2 + Math.max(questions.length, companies.length) * ROW);

	function curve(fromY: number, toY: number): string {
		const mid = (QUESTION_X + QUESTION_W + COMPANY_X) / 2;
		return `M${QUESTION_X + QUESTION_W} ${fromY} C${mid} ${fromY} ${mid} ${toY} ${COMPANY_X} ${toY}`;
	}

	function clip(value: string): string {
		return value.length <= TITLE_CHARS ? value : `${value.slice(0, TITLE_CHARS - 1).trimEnd()}…`;
	}

	function companyAnswered(id: string, questionKey: string): boolean {
		return companies.some((company) => company.id === id && company.questions.includes(questionKey));
	}

	function questionLit(key: string): boolean {
		if (!hovered) return true;
		if (hovered === key) return true;
		return companyAnswered(hovered, key);
	}

	function companyLit(id: string): boolean {
		if (!hovered) return true;
		if (hovered === id) return true;
		const company = companies.find((entry) => entry.id === id);
		return company?.questions.includes(hovered) ?? false;
	}

	const signals = $derived(rows.reduce((sum, row) => sum + row.signalCount, 0));
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<h1 class="dsp">Signal map</h1>
			<p class="sub">
				{serviceLine} · every question the board asks, what it found, and who it found it in. A company under
				more than one question has more than one reason to call.
			</p>
		</div>
		<div class="topbar-tools">
			<div class="seg" role="tablist" aria-label="How to read it">
				<button class="seg-btn" class:on={!asTable} onclick={() => (asTable = false)}>Map</button>
				<button class="seg-btn" class:on={asTable} onclick={() => (asTable = true)}>Table</button>
			</div>
		</div>
	</header>

	<div class="toolbar">
		<span class="legend">
			<span class="legend-item"><span class="swatch swatch-for"></span> asks for the thing</span>
			<span class="legend-item"><span class="swatch swatch-against"></span> counts against them</span>
			<span class="legend-item"><span class="swatch swatch-line"></span> a question that found them</span>
		</span>
		<span class="spacer"></span>
		<span class="sub dim num">
			{questions.length} questions · {shortNumber(signals)} signals · {rows.length} companies
			{#if hidden > 0}· top {SHOWN_COMPANIES}, {hidden} below{/if}
		</span>
	</div>

	<section class="pane scroll" aria-label="How the questions reach companies">
		<LoadingVeil show={loading} label="Building the map" />

		{#if error}
			<p class="note warn"><Icon name="alert" size={15} /> {error}</p>
		{:else if questions.length === 0 && !loading}
			<div class="empty">
				<Icon name="inbox" size={22} />
				<p>No question on this service line has found anything yet. Ask one in the Studio.</p>
			</div>
		{:else if asTable}
			<div class="table-wrap">
				<table class="tbl">
					<thead>
						<tr>
							<th scope="col">Question</th>
							<th scope="col">Theme</th>
							<th scope="col">Asks</th>
							<th scope="col" class="m">Found</th>
							<th scope="col">Carried by</th>
						</tr>
					</thead>
					<tbody>
						{#each questions as question (question.key)}
							{@const top = companies.filter((c) => c.questions.includes(question.key))}
							<tr>
								<td class="c-q">
									<a class="link" href={`/studio?q=${encodeURIComponent(question.key)}`}>{question.title}</a>
									<span class="sub dim">{question.text}</span>
								</td>
								<td><Tag>{theme(question.theme)}</Tag></td>
								<td>
									<Tag variant={question.polarity === 'POSITIVE' ? 'wash' : 'oxblood'}>
										{question.polarity === 'POSITIVE' ? 'for them' : 'against them'}
									</Tag>
								</td>
								<td class="m num">{question.companies.size}</td>
								<td class="c-carried">
									{#each top.slice(0, 4) as company (company.id)}
										<a class="link" href={`/?co=${company.id}`}>{company.name}</a>
									{/each}
									{#if top.length === 0}<span class="sub dim">nobody in the top {SHOWN_COMPANIES}</span>{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		{:else}
			<div class="map">
				<svg viewBox={`0 0 ${WIDTH} ${height}`} width={WIDTH} height={height} role="img">
					<title>How each question reaches the companies it found</title>

					<g class="axes">
						<text x={THEME_X} y="14">THEME</text>
						<text x={QUESTION_X} y="14">QUESTION</text>
						<text x={COMPANY_X} y="14">COMPANY</text>
					</g>

					<g class="edges">
						{#each questions as question (question.key)}
							{@const qy = layout.questionY.get(question.key) ?? 0}
							{#each companies as company (company.id)}
								{#if company.questions.includes(question.key)}
									<path
										class="edge"
										class:against={question.polarity !== 'POSITIVE'}
										class:lit={questionLit(question.key) && companyLit(company.id)}
										d={curve(qy, layout.companyY.get(company.id) ?? 0)}
									/>
								{/if}
							{/each}
						{/each}
					</g>

					<g class="themes">
						{#each groups as group (group.name)}
							{@const spot = layout.themeY.get(group.name)}
							{#if spot}
								<text class="theme-name" x={THEME_X} y={spot.at + 4}>
									{theme(group.name)}<tspan class="theme-count" dx="6">{group.keys.length}</tspan>
								</text>
								<path
									class="bracket"
									d={`M${QUESTION_X - 12} ${spot.first} H${QUESTION_X - 22} V${spot.last} H${QUESTION_X - 12}`}
								/>
								{#each group.keys as key (key)}
									<line
										class="bracket"
										x1={QUESTION_X - 22}
										y1={layout.questionY.get(key) ?? 0}
										x2={QUESTION_X}
										y2={layout.questionY.get(key) ?? 0}
									/>
								{/each}
							{/if}
						{/each}
					</g>

					<g class="questions">
						{#each questions as question (question.key)}
							{@const qy = layout.questionY.get(question.key) ?? 0}
							<g
								class="node"
								class:lit={questionLit(question.key)}
								role="link"
								tabindex="0"
								onmouseenter={() => (hovered = question.key)}
								onmouseleave={() => (hovered = null)}
								onclick={() => (window.location.href = `/studio?q=${encodeURIComponent(question.key)}`)}
								onkeydown={(event) => {
									if (event.key === 'Enter') window.location.href = `/studio?q=${encodeURIComponent(question.key)}`;
								}}
							>
								<title>{question.title} — {question.text}</title>
								<rect
									class="q-bar"
									class:against={question.polarity !== 'POSITIVE'}
									x={QUESTION_X}
									y={qy - NODE_H / 2}
									width={QUESTION_W}
									height={NODE_H}
									rx="2"
								/>
								<text class="q-title" x={QUESTION_X + 12} y={qy - 1}>{clip(question.title)}</text>
								<text class="q-count" x={QUESTION_X + 12} y={qy + 13}>
									{question.companies.size} {question.companies.size === 1 ? 'company' : 'companies'}
								</text>
							</g>
						{/each}
					</g>

					<g class="companies">
						{#each layout.order as company (company.id)}
							{@const cy = layout.companyY.get(company.id) ?? 0}
							<g
								class="node"
								class:lit={companyLit(company.id)}
								role="link"
								tabindex="0"
								onmouseenter={() => (hovered = company.id)}
								onmouseleave={() => (hovered = null)}
								onclick={() => (window.location.href = `/?co=${company.id}`)}
								onkeydown={(event) => {
									if (event.key === 'Enter') window.location.href = `/?co=${company.id}`;
								}}
							>
								<rect
									class="c-pill"
									data-band={company.band}
									x={COMPANY_X}
									y={cy - NODE_H / 2}
									width={COMPANY_W}
									height={NODE_H}
									rx="2"
								/>
								{#if company.domain && files[company.domain.toLowerCase()]}
									<image
										href={`/logos/${files[company.domain.toLowerCase()]}`}
										x={COMPANY_X + 8}
										y={cy - 9}
										width="18"
										height="18"
									/>
								{/if}
								<text class="c-name" x={COMPANY_X + 34} y={cy + 4}>{clip(company.name)}</text>
								<text class="c-count" x={COMPANY_X + COMPANY_W - 10} y={cy + 4} text-anchor="end">
									{company.questions.length}×
								</text>
							</g>
						{/each}
					</g>
				</svg>
			</div>
		{/if}
	</section>
</div>

<style>
	.toolbar .spacer {
		margin-left: auto;
	}

	.legend {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--spacing-4);
		font-size: var(--text-sm);
		color: var(--color-ink-3);
	}

	.legend-item {
		display: inline-flex;
		align-items: center;
		gap: var(--spacing-1);
	}

	.swatch {
		width: 12px;
		height: 8px;
		border-radius: 1px;
	}

	.swatch-for {
		background: var(--color-ink);
	}

	.swatch-against {
		background: var(--color-oxblood);
	}

	.swatch-line {
		width: 16px;
		height: 2px;
		background: var(--color-rule-strong);
	}

	.pane {
		position: relative;
		flex: 1;
		min-height: 0;
		background: var(--color-surface);
	}

	.map {
		padding: var(--spacing-3) var(--spacing-4) var(--spacing-5);
		min-width: min-content;
	}

	.map svg {
		display: block;
	}

	.axes text {
		font-size: var(--text-micro);
		font-weight: 600;
		letter-spacing: 0.09em;
		fill: var(--color-ink-4);
	}

	.edge {
		fill: none;
		stroke: var(--color-rule-strong);
		stroke-width: 1;
		opacity: 0.45;
		transition: opacity var(--ease), stroke var(--ease);
	}

	.edge.against {
		stroke: var(--color-oxblood-line);
	}

	.edge.lit {
		opacity: 0.7;
		stroke-width: 1.5;
	}

	.edge:not(.lit) {
		opacity: 0.08;
	}

	.bracket {
		fill: none;
		stroke: var(--color-rule);
		stroke-width: 1;
	}

	.theme-name {
		font-family: var(--font-display);
		font-size: var(--text-md);
		font-weight: 600;
		fill: var(--color-ink);
	}

	.theme-count {
		font-family: var(--font-ui);
		font-size: var(--text-micro);
		font-weight: 400;
		fill: var(--color-ink-4);
	}

	.node {
		cursor: pointer;
		transition: opacity var(--ease);
	}

	.node:not(.lit) {
		opacity: 0.3;
	}

	.q-bar {
		fill: var(--color-ink);
	}

	.q-bar.against {
		fill: var(--color-oxblood);
	}

	.node:hover .q-bar {
		fill: var(--color-orange);
	}

	.q-title {
		font-size: var(--text-sm);
		fill: var(--color-surface);
		pointer-events: none;
	}

	.q-count {
		font-size: var(--text-micro);
		fill: var(--color-surface);
		opacity: 0.72;
		pointer-events: none;
	}

	.c-pill {
		fill: var(--color-paper);
		stroke: var(--color-rule);
		stroke-width: 1;
	}

	.c-pill[data-band='PRIORITY'] {
		fill: var(--color-orange-wash);
		stroke: var(--color-orange-line);
	}

	.c-pill[data-band='NURTURE'] {
		fill: var(--color-orange-wash);
		stroke: var(--color-rule);
	}

	.c-pill[data-band='DISQUALIFIED'] {
		fill: var(--color-oxblood-wash);
		stroke: var(--color-oxblood-line);
	}

	.node:hover .c-pill {
		stroke: var(--color-ink);
	}

	.c-name {
		font-size: var(--text-base);
		fill: var(--color-ink);
		pointer-events: none;
	}

	.c-count {
		font-size: var(--text-micro);
		fill: var(--color-ink-3);
		pointer-events: none;
	}

	.table-wrap {
		padding: var(--spacing-3) var(--spacing-4);
	}

	.c-q .sub {
		display: block;
		max-width: 60ch;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.c-carried {
		display: flex;
		flex-wrap: wrap;
		gap: var(--spacing-1) var(--spacing-2);
	}

	.empty {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: var(--spacing-2);
		height: 100%;
		color: var(--color-ink-3);
	}

	.seg-btn {
		font: inherit;
		font-size: var(--text-sm);
		padding: 2px var(--spacing-3);
		border: 0;
		background: transparent;
		color: var(--color-ink-2);
		cursor: pointer;
	}

	.seg-btn.on {
		background: var(--color-surface);
		color: var(--color-ink);
		font-weight: 600;
	}
</style>
