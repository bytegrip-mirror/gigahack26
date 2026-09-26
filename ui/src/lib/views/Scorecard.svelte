<script lang="ts">
	import {
		fetchCompany,
		pushToHubspot,
		recordLead,
		sendFeedback,
		type CompanyDetail,
		type LedgerRow,
		type LeadView
	} from '$lib/api';
	import { toasts } from '$lib/toast.svelte';
	import { age, date, fit, host, plural, signed, sourceKind, theme } from '$lib/format';
	import { copy } from '$lib/export';
	import Tag from '$lib/components/Tag.svelte';
	import ScoreMeter from '$lib/components/ScoreMeter.svelte';
	import Ledger from '$lib/components/Ledger.svelte';
	import OutreachModal from '$lib/components/OutreachModal.svelte';
	import Hubspot from '$lib/components/Hubspot.svelte';
	import Star from '$lib/components/Star.svelte';
	import { fetchBookmarks } from '$lib/api';
	import Icon from '$lib/components/Icon.svelte';
	import Menu from '$lib/components/Menu.svelte';
	import Metric from '$lib/components/Metric.svelte';
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
		{ id: 'ledger', label: 'Ledger' },
		{ id: 'documents', label: 'Documents' }
	];

	let detail = $state<CompanyDetail | null>(null);
	let error = $state<string | null>(null);
	let loading = $state(true);
	let tab = $state('overview');
	let showFacts = $state(false);
	let outreachOpen = $state(false);
	let silentOnly = $state(false);
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

	const scored = $derived(detail ? detail.ledger.filter((row) => !row.blocking) : []);
	const silent = $derived(detail ? detail.evidence.filter((item) => item.signalCount === 0) : []);
	const documents = $derived(detail ? (silentOnly ? silent : detail.evidence) : []);
	const stage = $derived(lead?.status ?? 'NEW');
	const counters = $derived(detail?.whyNow?.counters ?? []);
	const triggers = $derived(detail?.whyNow?.triggers ?? []);
	const notEvidence = $derived(scored.filter((row) => row.tierNats === 0));

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
			`${detail.name} — fit ${fit(detail.probability)} (${detail.bandLabel})`,
			`${detail.domain ?? 'no domain'} · ${detail.country ?? 'unknown'} · ${detail.industry ?? 'unclassified'}`,
			'',
			detail.whyNow.headline,
			'',
			...triggers.map((row) => `${signed(row.contributionNats, 2)}  ${row.questionTitle} — “${row.spanText ?? ''}”`),
			...counters.map((row) => `${signed(row.contributionNats, 2)}  against: ${row.questionTitle}`)
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
			<h2 class="dsp">{detail?.name ?? 'Reading'}</h2>
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
					{#if option.id === 'ledger'}<span class="num">{scored.length}</span>{/if}
					{#if option.id === 'documents'}<span class="num">{detail.evidence.length}</span>{/if}
				</button>
			{/each}
			{#if tab !== 'overview'}
				<span class="chip">
					<span class="num chip-fit">{fit(detail.probability)}</span>
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
					<div class="metrics">
						<Metric value={fit(detail.probability)} label="Fit" tone={detail.band === 'DISQUALIFIED' ? 'bad' : 'hot'} title="How strongly the evidence points at this company, against the base rate you set" />
						<Metric value={detail.signalCount} label="Signals" title="Documents that answered one of your questions" />
						<Metric value={detail.themeCount} label="Themes" title="Different kinds of evidence pointing the same way" />
					</div>
					<Tag band={detail.band}>{detail.bandLabel}</Tag>
				</div>
				<ScoreMeter
					probability={detail.probability}
					band={detail.band}
					baseRate={detail.baseRate}
					thresholds={{
						watch: detail.watchThreshold,
						nurture: detail.nurtureThreshold,
						priority: detail.priorityThreshold
					}}
					showScale
				/>
				{#if detail.gateReason}
					<p class="gate"><Icon name="alert" size={14} /> <span><span class="label">Removed from the queue</span> {detail.gateReason}</span></p>
				{/if}
			</section>

			<section class="panel why" data-urgency={detail.whyNow.urgency}>
				<div class="panel-head">
					<h3 class="label">Why now</h3>
					<div class="head-right">
						<span class="urgency" data-urgency={detail.whyNow.urgency}>{detail.whyNow.urgencyLabel}</span>
						<button
							class="reveal"
							aria-expanded={showFacts}
							title="The counts behind this reading"
							onclick={() => (showFacts = !showFacts)}
						>
							Facts
							<Icon name={showFacts ? 'chevronDown' : 'chevronRight'} size={12} />
						</button>
					</div>
				</div>
				<p class="why-text">{detail.whyNow.narrative || detail.whyNow.headline}</p>
				{#if showFacts}
					<dl class="facts why-facts">
						{#if triggers[0]}
							<dt>Strongest signal</dt>
							<dd class="num">{signed(triggers[0].contributionNats, 2)}</dd>
						{/if}
						<dt>Themes agreeing</dt>
						<dd class="num">{detail.themeCount}</dd>
						<dt>Evidence</dt>
						<dd class="num">{triggers.length} for · {counters.length} against</dd>
						{#if detail.band === 'PRIORITY' && detail.priorityHorizonDays !== null}
							<dt>Holds Priority</dt>
							<dd class="num">{Math.round(detail.priorityHorizonDays)} more days</dd>
						{:else if detail.whyNow.daysInBand !== null}
							<dt>In this band</dt>
							<dd class="num">{Math.round(detail.whyNow.daysInBand)} days</dd>
						{/if}
						<dt>Documents read</dt>
						<dd class="num">{detail.scannedDocuments} · {detail.silentDocuments} silent</dd>
					</dl>
					{#if detail.weakestLink}
						<p class="sub dim">Weakest link in the evidence: {detail.weakestLink}</p>
					{/if}
					{#if detail.gateReason}
						<p class="sub dim gate-line">{detail.gateLine}</p>
					{/if}
					{#if detail.coverageNotes}
						<p class="sub dim">Coverage: {detail.coverageNotes}.</p>
					{/if}
				{/if}
			</section>

			<section class="panel">
				<div class="panel-head">
					<h3 class="label">Evidence</h3>
					<span class="sub dim num">{triggers.length} for · {counters.length} against</span>
				</div>
				<div class="lines">
					{#each triggers as row (row.evidenceId + row.spanStart)}
						<button class="line" onclick={() => openRow(row)}>
							<span class="dot pos"></span>
							<span class="line-text">
								{row.questionTitle}
								{#if row.rationale}<span class="why-line">{row.rationale}</span>{/if}
							</span>
							<span class="num">{signed(row.contributionNats, 2)}</span>
						</button>
					{:else}
						<p class="sub dim">No signal cleared the confidence floor for this policy.</p>
					{/each}
					{#if notEvidence.length > 0}
						<p class="label against-label">Read, not evidence</p>
						{#each notEvidence as row (row.evidenceId + row.questionId + row.spanStart)}
							<button class="line" onclick={() => openRow(row)}>
								<span class="dot neither"></span>
								<span class="line-text">
									{row.questionTitle}
									<span class="why-line">{verdictWords(row)}</span>
								</span>
								<span class="num dim">not counted</span>
							</button>
						{/each}
					{/if}
					{#if counters.length > 0}
						<p class="label against-label">Against</p>
						{#each counters as row (row.evidenceId + row.spanStart)}
							<button class="line" onclick={() => openRow(row)}>
								<span class="dot neg"></span>
								<span class="line-text">
									{row.questionTitle}
									{#if row.rationale}<span class="why-line">{row.rationale}</span>{/if}
								</span>
								<span class="num">{signed(row.contributionNats, 2)}</span>
							</button>
						{/each}
					{/if}
				</div>
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

			<div class="tab-body">
				{#if tab === 'ledger'}
					<Ledger rows={scored} onopen={openRow} />
					<div class="sum num">
						<div>
							<span class="dim">Every company in the pool starts here — the share you said is worth calling</span>
							<span>{(detail.baseRate * 100).toFixed(0)}%</span>
						</div>
						{#each scored as row (row.evidenceId + row.questionId + row.spanStart)}
							<div class="dim">
								<span>{row.questionTitle}{row.themeCapped ? ' · capped' : ''}</span>
								<span>{row.tierNats === 0 ? 'not counted' : signed(row.contributionNats)}</span>
							</div>
						{/each}
						<div class="total">
							<span>Fit</span><span>{fit(detail.probability)}</span>
						</div>
					</div>
					<div class="themes">
						{#each detail.themes as item (item.name)}
							<div class="theme">
								<div class="theme-row">
									<span>{theme(item.name)}</span>
									<span class="num">{signed(item.total)}{item.capped ? ' · capped' : ''}</span>
								</div>
								<div class="bar">
									<span
										data-capped={item.capped}
										style="width:{Math.min(100, (Math.abs(item.total) / detail.themeCap) * 100)}%"
									></span>
								</div>
							</div>
						{/each}
						<p class="sub dim">
							Rows sharing a theme are capped together, so one loud topic can never carry a company into
							Priority on its own. The ceiling is set from the Priority line, not chosen.
						</p>
					</div>
				{/if}

				{#if tab === 'documents'}
					{#if (detail?.belowFloor ?? []).length > 0}
						<section class="panel below-panel">
							<div class="panel-head">
								<h3 class="label">Read and not counted</h3>
								<span class="sub dim num">{detail?.belowFloor?.length ?? 0}</span>
							</div>
							{#each (detail?.belowFloor ?? []).slice(0, 8) as row (row.evidenceId + row.spanStart)}
								<button
									class="below-row"
									title={row.rationale ?? 'matched the rubric below the floor'}
									onclick={() => oncapture(row.evidenceId, { start: row.spanStart, end: row.spanEnd })}
								>
									<span class="num below-score">{row.confidence.toFixed(2)}</span>
									<span class="below-text">
										{row.spanText.slice(0, 130)}
										<span class="why-line">{row.questionTitle} · {row.polarity.toLowerCase()}</span>
									</span>
								</button>
							{/each}
							<p class="sub dim">Matched, scored under the floor, not counted.</p>
						</section>
					{/if}

					<label class="check doc-filter">
						<input type="checkbox" bind:checked={silentOnly} />
						<span>Only the {silent.length} that produced nothing</span>
					</label>
					{#each documents as item (item.id)}
						<button class="doc" onclick={() => oncapture(item.id, null)}>
							<span class="doc-top">
								<span class="label">{sourceKind(item.sourceKind)}</span>
								<span class="num dim">{date(item.publishedAt ?? item.observedAt)}</span>
							</span>
							<span class="doc-title">{item.title ?? host(item.sourceUrl)}</span>
							<span class="sub dim">
								{item.publisher} · {plural(item.charLength, 'char')}
								{#if item.signalCount > 0}· {plural(item.signalCount, 'signal')}{:else}· no signal{/if}
							</span>
						</button>
					{/each}
				{/if}
			</div>
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
	.below-panel {
		margin-bottom: var(--spacing-3);
	}

	.below-row {
		display: grid;
		grid-template-columns: 34px 1fr;
		gap: var(--spacing-2);
		width: 100%;
		padding: var(--spacing-2);
		border: 0;
		border-bottom: 1px solid var(--color-rule);
		background: transparent;
		text-align: left;
		cursor: pointer;
	}

	.below-row:hover {
		background: var(--color-sunken);
	}

	.below-score {
		color: var(--color-ink-3);
	}

	.below-text {
		font-size: var(--text-sm);
		color: var(--color-ink-2);
	}

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

	.head-tools {
		display: flex;
		align-items: center;
		gap: 2px;
		flex: none;
	}

	h2 {
		font-size: var(--text-xl);
		line-height: 1.15;
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
		margin-bottom: var(--spacing-2);
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

	.head-right {
		display: flex;
		align-items: center;
		gap: var(--spacing-3);
	}

	.reveal {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		font-size: var(--text-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.09em;
		color: var(--color-ink-3);
	}

	.reveal:hover {
		color: var(--color-ink);
	}

	.why-facts {
		margin-top: var(--spacing-2);
		padding-top: var(--spacing-2);
		border-top: 1px solid var(--color-rule);
		font-size: var(--text-base);
	}

	.why-facts dt {
		color: var(--color-ink-3);
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
		margin-top: var(--spacing-2);
	}

	.line {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-1);
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
		background: var(--rule-strong, var(--color-ink-3));
	}

	.line-text {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.line .num {
		flex: none;
		font-size: var(--text-micro);
		color: var(--color-ink-4);
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
		font-size: var(--text-lg);
		font-weight: 500;
		line-height: 1;
	}

	.chip :global(.tag) {
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.tab-body {
		padding: var(--spacing-3);
	}

	.doc-filter {
		margin-bottom: var(--spacing-2);
		font-size: var(--text-sm);
		color: var(--color-ink-3);
	}

	.sum {
		margin-top: var(--spacing-3);
		border-top: 1px solid var(--color-rule);
		padding-top: var(--spacing-2);
		font-size: var(--text-sm);
	}

	.sum > div {
		display: flex;
		justify-content: space-between;
		gap: var(--spacing-3);
		padding: 1px 0;
	}

	.sum .total {
		border-top: 1px solid var(--color-rule);
		margin-top: 5px;
		padding-top: 5px;
		font-weight: 500;
		color: var(--color-ink);
	}

	.themes {
		margin-top: var(--spacing-4);
		border-top: 1px solid var(--color-rule);
		padding-top: var(--spacing-3);
	}

	.theme {
		margin-bottom: var(--spacing-2);
	}

	.theme-row {
		display: flex;
		justify-content: space-between;
		font-size: var(--text-base);
	}

	.bar {
		height: 4px;
		background: var(--color-sunken);
		margin-top: 3px;
	}

	.bar span {
		display: block;
		height: 100%;
		background: var(--color-orange);
	}

	.bar span[data-capped='true'] {
		background: var(--color-ink);
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
	}</style>
