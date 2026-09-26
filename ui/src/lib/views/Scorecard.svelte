<script lang="ts">
	import {
		fetchCompany,
		fetchBookmarks,
		pushToHubspot,
		recordLead,
		sendFeedback,
		type CompanyDetail,
		type LedgerRow,
		type LeadView
	} from '$lib/api';
	import { toasts } from '$lib/toast.svelte';
	import { age, date, fit, fitTone, fitWord, host, plural, sourceKind, theme } from '$lib/format';
	import { copy } from '$lib/export';
	import Tag from '$lib/components/Tag.svelte';
	import CompanyMark from '$lib/components/CompanyMark.svelte';
	import OutreachModal from '$lib/components/OutreachModal.svelte';
	import Hubspot from '$lib/components/Hubspot.svelte';
	import Star from '$lib/components/Star.svelte';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import type { MenuItem } from '$lib/components/menu';

	let {
		companyId,
		serviceLine,
		onclose,
		oncapture
	}: {
		companyId: string;
		serviceLine: string;
		onclose: () => void;
		oncapture: (evidenceId: string, focus: { start: number; end: number } | null) => void;
	} = $props();

	const STAGES = [
		{ id: 'NEW', label: 'New' },
		{ id: 'CONTACTED', label: 'Contacted' },
		{ id: 'REPLIED', label: 'Replied' },
		{ id: 'QUALIFIED', label: 'Qualified' },
		{ id: 'REJECTED', label: 'Rejected' }
	];

	const TABS = [
		{ id: 'overview', label: 'Overview' },
		{ id: 'documents', label: 'Documents' }
	];

	const READINGS: Record<string, string> = {
		Strong: 'The evidence here is well clear of what a company in your market shows by default.',
		Good: 'The evidence here sits above what a company in your market shows by default.',
		Fair: 'The evidence here is close to what a company in your market shows by default.',
		Weak: 'Nothing the corpus holds lifts this company above the market default.'
	};

	let detail = $state<CompanyDetail | null>(null);
	let error = $state<string | null>(null);
	let loading = $state(true);
	let tab = $state('overview');
	let outreachOpen = $state(false);
	let lead = $state<LeadView | null>(null);
	let busy = $state<string | null>(null);
	let saved = $state(false);

	$effect(() => {
		const id = companyId;
		const line = serviceLine;
		if (!id || !line) return;
		let cancelled = false;
		loading = true;
		fetchCompany(id, line)
			.then((result) => {
				if (cancelled) return;
				detail = result;
				lead = result.lead;
				error = null;
			})
			.catch((cause) => {
				if (!cancelled) error = cause.message;
			})
			.finally(() => {
				if (!cancelled) loading = false;
			});
		fetchBookmarks()
			.then((list) => {
				if (!cancelled) saved = list.some((bookmark) => bookmark.companyId === id && bookmark.serviceLine === line);
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	});

	const thresholds = $derived({
		watch: detail?.watchThreshold ?? 0.28,
		nurture: detail?.nurtureThreshold ?? 0.45,
		priority: detail?.priorityThreshold ?? 0.55
	});
	const word = $derived(detail ? fitWord(detail.probability, thresholds) : '');
	const tone = $derived(detail ? fitTone(detail.probability, thresholds) : 'fair');
	const triggers = $derived(detail?.whyNow?.triggers ?? []);
	const counters = $derived(detail?.whyNow?.counters ?? []);
	const notEvidence = $derived(detail ? detail.ledger.filter((row) => !row.blocking && row.tierNats === 0) : []);

	const documents = $derived.by(() => {
		if (!detail) return { read: [], silent: [] };
		const read = detail.evidence
			.filter((item) => item.signalCount > 0)
			.sort(
				(a, b) =>
					b.signalCount - a.signalCount ||
					new Date(b.publishedAt ?? b.observedAt).getTime() - new Date(a.publishedAt ?? a.observedAt).getTime()
			);
		const silent = detail.evidence.filter((item) => item.signalCount === 0);
		return { read, silent };
	});

	const stage = $derived(lead?.status ?? 'NEW');

	function verdictWords(row: LedgerRow): string {
		if (row.verdict === 'BUILD') return 'the model read this as the company selling the capability itself';
		if (row.verdict === 'COUNTER') return 'the model read this as arguing against buying';
		return 'the model read this as answering the question';
	}

	function daysSince(iso: string): number {
		return Math.max(0, (Date.now() - new Date(iso).getTime()) / 86_400_000);
	}

	async function setStage(status: string) {
		if (!detail || status === stage) return;
		busy = 'stage';
		try {
			lead = await recordLead({ companyId: detail.id, serviceLine, status });
			toasts.push(`Marked ${lead.statusLabel.toLowerCase()}.`, 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = null;
		}
	}

	async function pushCrm() {
		if (!detail) return;
		busy = 'crm';
		try {
			const result = await pushToHubspot(detail.id, serviceLine);
			if (result.pushed) toasts.push('Pushed to HubSpot.', 'success');
			else toasts.push('HubSpot is not connected on this deployment. The push is recorded on the lead, not sent.', 'warn');
			lead = await recordLead({ companyId: detail.id, serviceLine });
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = null;
		}
	}

	async function judge(value: 'ACCEPT' | 'REJECT') {
		if (!detail) return;
		try {
			await sendFeedback({ companyId: detail.id, repVerdict: value });
			toasts.push(value === 'ACCEPT'
				? 'Counted as a good fit — it feeds the precision figure on the board.'
				: 'Counted as not a fit — it feeds the precision figure on the board.', 'success');
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		}
	}

	function openRow(row: LedgerRow) {
		oncapture(row.evidenceId, { start: row.spanStart, end: row.spanEnd });
	}

	async function copySummary() {
		if (!detail) return;
		const lines = [
			`${detail.name} — ${word.toLowerCase()} fit (${fit(detail.probability)}), ${detail.bandLabel}`,
			`${detail.domain ?? 'no domain'} · ${detail.country ?? 'unknown'} · ${detail.industry ?? 'unclassified'}`,
			'',
			detail.whyNow.headline,
			'',
			...triggers.map((row) => `${row.questionTitle}${row.spanText ? ` — “${row.spanText}”` : ''}`),
			...counters.map((row) => `against: ${row.questionTitle}`)
		];
		const ok = await copy(lines.join('\n'));
		toasts.push(ok ? 'Summary copied.' : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
	}

	const overflow: MenuItem[] = $derived([
		{ id: 'summary', label: 'Copy the summary', icon: 'copy' },
		{ id: 'source', label: 'Open the website', icon: 'link', disabled: !detail?.domain },
		{ id: 'accept', label: 'Count as a good fit', icon: 'check', separatorBefore: true },
		{ id: 'reject', label: 'Count as not a fit', icon: 'close', danger: true },
		{ id: 'close', label: 'Close the scorecard', icon: 'chevronRight', separatorBefore: true }
	]);

	async function runOverflow(id: string) {
		if (id === 'summary') await copySummary();
		else if (id === 'source' && detail?.domain) window.open(`https://${detail.domain}`, '_blank', 'noopener');
		else if (id === 'accept') await judge('ACCEPT');
		else if (id === 'reject') await judge('REJECT');
		else if (id === 'close') onclose();
	}
</script>

<div class="card">
	<header class="head">
		<div class="head-main">
			{#if detail}
				<div class="head-line">
					<CompanyMark name={detail.name} domain={detail.domain} size="lg" />
					<h2 class="dsp">{detail.name}</h2>
				</div>
			{:else}
				<h2 class="dsp">Reading</h2>
			{/if}
			{#if detail}
				<p class="sub">
					{#if detail.domain}<a href="https://{detail.domain}" target="_blank" rel="noreferrer noopener">{detail.domain}</a>{/if}{detail.hqCity
						? ` · ${detail.hqCity}`
						: ''}{detail.country ? ` · ${detail.country}` : ''}{detail.industry
						? ` · ${detail.industry}`
						: ''}{detail.sizeBand ? ` · ${detail.sizeBand}` : ''}
				</p>
			{/if}
		</div>
		<div class="head-tools">
			{#if detail}
				<Star
					companyId={detail.id}
					companyName={detail.name}
					{serviceLine}
					{saved}
					size={17}
					onsaved={(on) => (saved = on)}
				/>
			{/if}
			<Menu items={overflow} onselect={runOverflow} label={`More actions for ${detail?.name ?? 'this company'}`} />
			<button class="icon-btn" onclick={onclose} aria-label="Close detail"><Icon name="close" size={16} /></button>
		</div>
	</header>

	{#if detail}
		<div class="tabs" role="tablist" aria-label="Company detail">
			{#each TABS as option (option.id)}
				<button role="tab" aria-selected={tab === option.id} onclick={() => (tab = option.id)}>
					{option.label}
					{#if option.id === 'documents'}<span class="num">{detail.evidence.length}</span>{/if}
				</button>
			{/each}
			{#if tab !== 'overview'}
				<span class="chip">
					<span class="chip-fit dsp" data-tone={tone}>{word}</span>
					<Tag band={detail.band}>{detail.bandLabel}</Tag>
				</span>
			{/if}
		</div>
	{/if}

	{#if error}
		<p class="note warn"><Icon name="alert" size={15} /> {error}</p>
	{:else if loading && !detail}
		<div class="veil"><span class="spinner"></span><span>Reading the scorecard</span></div>
	{:else if detail}
		<div class="body scroll">
			{#if tab === 'overview'}
				<section class="panel score">
					<div class="score-row">
						<span class="verdict dsp" data-tone={tone}>{word}</span>
						<Tag band={detail.band}>{detail.bandLabel}</Tag>
					</div>
					<p class="reading">{READINGS[word]}</p>
					{#if detail.themes.length > 0}
						<div class="themes">
							<span class="label">What it is</span>
							<span class="theme-tags">
								{#each detail.themes as item (item.name)}
									<Tag>{theme(item.name)}</Tag>
								{/each}
							</span>
							{#if detail.themeCount >= 2}
								<span class="corrob" title="More than one kind of evidence points the same way, which is what the Priority band needs.">
									<Icon name="check" size={12} /> corroborated
								</span>
							{:else}
								<span class="sub dim" title="One loud topic is capped, so a company carried by a single theme cannot reach Priority on its own.">
									one theme only
								</span>
							{/if}
						</div>
					{/if}
					{#if detail.gateReason}
						<p class="gate"><Icon name="alert" size={14} /> <span><span class="label">Removed from the queue</span> {detail.gateReason}</span></p>
					{/if}
				</section>

				<section class="panel why" data-urgency={detail.whyNow.urgency}>
					<div class="panel-head">
						<h3 class="label">Why now</h3>
						<span class="urgency" data-urgency={detail.whyNow.urgency}>{detail.whyNow.urgencyLabel}</span>
					</div>
					<p class="why-text">{detail.whyNow.narrative || detail.whyNow.headline}</p>
				</section>

				<section class="panel">
					<div class="panel-head">
						<h3 class="label">Why we would call</h3>
						<span class="sub dim num">{triggers.length} of {plural(detail.scannedDocuments, 'document')}</span>
					</div>
					<div class="lines">
						{#each triggers as row (row.evidenceId + row.spanStart)}
							<button class="line" onclick={() => openRow(row)} title="Open the sentence this was read from">
								<span class="dot pos"></span>
								<span class="line-text">
									{row.questionTitle}
									{#if row.rationale}<span class="why-line">{row.rationale}</span>{/if}
								</span>
							</button>
						{:else}
							<p class="sub dim">Nothing in their documents answers your questions.</p>
						{/each}
					</div>

					{#if counters.length > 0}
						<p class="label against-label">Reasons not to call</p>
						<div class="lines">
							{#each counters as row (row.evidenceId + row.spanStart)}
								<button class="line" onclick={() => openRow(row)}>
									<span class="dot neg"></span>
									<span class="line-text">
										{row.questionTitle}
										{#if row.rationale}<span class="why-line">{row.rationale}</span>{/if}
									</span>
								</button>
							{/each}
						</div>
					{/if}

					{#if notEvidence.length > 0}
						<p class="label against-label">Read, not evidence</p>
						<div class="lines">
							{#each notEvidence as row (row.evidenceId + row.questionId + row.spanStart)}
								<button class="line" onclick={() => openRow(row)}>
									<span class="dot neither"></span>
									<span class="line-text">
										{row.questionTitle}
										<span class="why-line">{verdictWords(row)}</span>
									</span>
								</button>
							{/each}
						</div>
					{/if}
				</section>

				<section class="panel">
					<div class="panel-head">
						<h3 class="label">Stage</h3>
						{#if lead && lead.contactCount > 0}
							<span class="sub dim num">
								{plural(lead.contactCount, 'contact')}{#if lead.lastContactedAt} · last {age(daysSince(lead.lastContactedAt))}{/if}{#if lead.channel} · {lead.channel}{/if}
							</span>
						{:else}
							<span class="sub dim">not contacted yet</span>
						{/if}
					</div>
					<div class="seg stage-seg" role="tablist" aria-label="Lead stage">
						{#each STAGES as option (option.id)}
							<button
								role="tab"
								aria-selected={stage === option.id}
								disabled={busy === 'stage'}
								onclick={() => setStage(option.id)}
							>
								{option.label}
							</button>
						{/each}
					</div>
					<button class="btn btn-ink btn-block" onclick={() => (outreachOpen = true)}>
						<Icon name="mail" size={15} />
						Generate outreach
					</button>
					<button
						class="btn btn-block"
						disabled={busy === 'crm'}
						title={detail.crmConfigured
							? 'Push this company to HubSpot'
							: 'No HubSpot token is configured, so the push is recorded as an intent'}
						onclick={pushCrm}
					>
						{#if busy === 'crm'}<span class="spinner"></span>{:else}<Hubspot size={17} />{/if}
						{lead?.crmSyncedAt ? 'Re-push to HubSpot' : 'Push to HubSpot'}
					</button>
				</section>
			{/if}

			{#if tab === 'documents'}
				<div class="tab-body">
					{#each documents.read as item (item.id)}
						<button class="doc" onclick={() => oncapture(item.id, null)}>
							<span class="doc-top">
								<span class="label">{sourceKind(item.sourceKind)}</span>
								<span class="num dim">{date(item.publishedAt ?? item.observedAt)}</span>
							</span>
							<span class="doc-title">{item.title ?? host(item.sourceUrl)}</span>
							<span class="sub dim">
								{item.publisher} · {plural(item.signalCount, 'question')} answered
							</span>
						</button>
					{/each}

					{#if documents.silent.length > 0}
						<p class="label quiet-head">Read, nothing to report</p>
						{#each documents.silent as item (item.id)}
							<button class="doc doc-quiet" onclick={() => oncapture(item.id, null)}>
								<span class="doc-top">
									<span class="label">{sourceKind(item.sourceKind)}</span>
									<span class="num dim">{date(item.publishedAt ?? item.observedAt)}</span>
								</span>
								<span class="doc-title">{item.title ?? host(item.sourceUrl)}</span>
								<span class="sub dim">{item.publisher} · answered none of your questions</span>
							</button>
						{/each}
					{/if}
				</div>
			{/if}
		</div>
	{/if}
</div>

{#if outreachOpen && detail}
	<OutreachModal
		companyId={detail.id}
		companyName={detail.name}
		{serviceLine}
		onclose={() => (outreachOpen = false)}
		ondrafted={(subject) => {
			const id = detail?.id;
			if (!id) return;
			recordLead({ companyId: id, serviceLine, status: 'CONTACTED', outreachSubject: subject })
				.then((saved) => (lead = saved))
				.catch(() => {});
		}}
	/>
{/if}

<style>
	.why-line {
		display: block;
		margin-top: 1px;
		font-size: var(--text-micro);
		color: var(--color-ink-3);
		font-weight: 400;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.card {
		display: flex;
		flex-direction: column;
		height: 100%;
		min-height: 0;
	}

	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--spacing-2);
		padding: var(--spacing-3) var(--spacing-4);
		border-bottom: 1px solid var(--color-ink);
		flex: none;
		min-height: var(--spacing-head);
	}

	.head-line {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		min-width: 0;
	}

	.head-tools {
		display: flex;
		align-items: center;
		gap: 2px;
		flex: none;
	}

	h2 {
		font-size: var(--text-xl);
		line-height: 1.15;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.sub {
		font-size: var(--text-sm);
		line-height: 1.45;
		color: var(--color-ink-3);
		margin-top: 2px;
	}

	.veil {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--spacing-2);
		background: var(--color-paper);
		color: var(--color-ink-3);
		font-size: var(--text-md);
	}

	.body {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
	}

	.score-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--spacing-3);
	}

	.verdict {
		font-size: var(--text-title);
		line-height: 1.1;
		color: var(--color-ink-2);
	}

	.verdict[data-tone='strong'] {
		color: var(--color-orange-ink);
	}

	.verdict[data-tone='weak'] {
		color: var(--color-ink-4);
	}

	.reading {
		margin-top: var(--spacing-1);
		font-size: var(--text-base);
		line-height: 1.5;
		color: var(--color-ink-2);
	}

	.themes {
		display: flex;
		align-items: center;
		flex-wrap: wrap;
		gap: var(--spacing-2);
		margin-top: var(--spacing-3);
		padding-top: var(--spacing-2);
		border-top: 1px solid var(--color-rule);
	}

	.theme-tags {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
	}

	.corrob {
		display: inline-flex;
		align-items: center;
		gap: 3px;
		font-size: var(--text-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-accent);
	}

	.gate {
		display: flex;
		align-items: flex-start;
		gap: var(--spacing-1);
		margin-top: var(--spacing-2);
		font-size: var(--text-base);
		line-height: 1.45;
		color: var(--color-oxblood);
	}

	.gate .label {
		display: block;
		color: var(--color-oxblood);
	}

	.why {
		background: var(--color-paper);
		border-left: 2px solid var(--color-rule-strong);
	}

	.why[data-urgency='ACT_NOW'] {
		background: var(--color-orange-wash);
		border-left-color: var(--color-orange);
	}

	.why[data-urgency='THIS_WEEK'] {
		border-left-color: var(--color-orange-line);
	}

	.urgency {
		font-size: var(--text-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.09em;
		color: var(--color-ink-3);
	}

	.urgency[data-urgency='ACT_NOW'] {
		color: var(--color-orange-ink);
	}

	.urgency[data-urgency='ACT_NOW']::before,
	.urgency[data-urgency='THIS_WEEK']::before {
		content: '● ';
		color: var(--color-orange);
	}

	.why-text {
		margin-top: var(--spacing-1);
		font-size: var(--text-base);
		line-height: 1.6;
		color: var(--color-ink-2);
	}

	.stage-seg {
		width: 100%;
	}

	.stage-seg button {
		flex: 1;
		justify-content: center;
		padding: 0 4px;
		font-size: var(--text-sm);
	}

	.lines {
		display: flex;
		flex-direction: column;
		gap: 1px;
	}

	.against-label {
		margin-top: var(--spacing-3);
	}

	.line {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
		width: 100%;
		text-align: left;
		padding: 2px 0;
		font-size: var(--text-base);
		color: var(--color-ink-2);
		line-height: 1.35;
	}

	.line:hover .line-text {
		color: var(--color-orange-deep);
		text-decoration: underline;
		text-underline-offset: 2px;
	}

	.dot {
		width: 5px;
		height: 5px;
		flex: none;
		transform: translateY(-1px);
	}

	.dot.pos {
		background: var(--color-accent);
	}

	.dot.neg {
		background: var(--color-oxblood);
	}

	.dot.neither {
		background: var(--color-ink-4);
	}

	.line-text {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.tabs {
		display: flex;
		align-items: center;
		padding: 0 var(--spacing-3);
		border-bottom: 1px solid var(--color-ink);
		background: var(--color-surface);
		flex: none;
		min-height: 36px;
	}

	.tabs button {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		padding: var(--spacing-2) 0;
		margin-right: var(--spacing-4);
		font-size: var(--text-base);
		color: var(--color-ink-3);
		border-bottom: 2px solid transparent;
		margin-bottom: -1px;
		transition: color var(--ease), border-color var(--ease);
	}

	.tabs button:hover {
		color: var(--color-ink);
	}

	.tabs button[aria-selected='true'] {
		color: var(--color-ink);
		font-weight: 500;
		border-bottom-color: var(--color-orange);
	}

	.tabs .num {
		font-size: var(--text-micro);
		color: var(--color-ink-4);
	}

	.chip {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		margin-left: auto;
		padding-left: var(--spacing-2);
		min-width: 0;
	}

	.chip-fit {
		font-size: var(--text-base);
		line-height: 1;
		color: var(--color-ink-2);
	}

	.chip-fit[data-tone='strong'] {
		color: var(--color-orange-ink);
	}

	.chip :global(.tag) {
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.tab-body {
		padding: var(--spacing-3);
	}

	.quiet-head {
		margin: var(--spacing-4) 0 var(--spacing-2);
		padding-top: var(--spacing-3);
		border-top: 1px solid var(--color-rule);
	}

	.doc {
		display: flex;
		flex-direction: column;
		gap: 3px;
		width: 100%;
		text-align: left;
		padding: var(--spacing-2) 0;
		border-bottom: 1px solid var(--color-rule);
		transition: background var(--ease);
	}

	.doc:hover {
		background: var(--color-paper);
	}

	.doc-quiet {
		padding: var(--spacing-2);
		margin-bottom: -1px;
		background: var(--color-sunken);
		border-bottom: 1px solid var(--color-surface);
	}

	.doc-quiet:hover {
		background: var(--color-rule);
	}

	.doc-quiet .doc-title {
		color: var(--color-ink-3);
	}

	.doc-top {
		display: flex;
		justify-content: space-between;
		gap: var(--spacing-3);
	}

	.doc-title {
		font-size: var(--text-base);
		line-height: 1.35;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
</style>
