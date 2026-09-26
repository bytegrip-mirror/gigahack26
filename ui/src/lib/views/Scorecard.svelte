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
	import Tag from '$lib/ui/Tag.svelte';
	import ScoreMeter from '$lib/components/ScoreMeter.svelte';
	import Ledger from '$lib/components/Ledger.svelte';
	import OutreachModal from '$lib/components/OutreachModal.svelte';
	import Hubspot from '$lib/components/Hubspot.svelte';
	import Star from '$lib/components/Star.svelte';
	import { fetchBookmarks } from '$lib/api';
	import Icon from '$lib/ui/Icon.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Metric from '$lib/ui/Metric.svelte';
	import Metrics from '$lib/ui/Metrics.svelte';
	import Tabs from '$lib/ui/Tabs.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import Panel from '$lib/ui/Panel.svelte';
	import Facts from '$lib/ui/Facts.svelte';
	import Checkbox from '$lib/ui/Checkbox.svelte';
	import Button from '$lib/ui/Button.svelte';
	import IconButton from '$lib/ui/IconButton.svelte';
	import Note from '$lib/ui/Note.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';
	import type { MenuItem } from '$lib/ui/menu';

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

	const tabsList = $derived(
		detail
			? [
					{ value: 'overview', label: 'Overview' },
					{ value: 'ledger', label: 'Ledger', count: scored.length },
					{ value: 'documents', label: 'Documents', count: detail.evidence.length }
				]
			: []
	);

	const stageOptions = $derived(
		STAGES.map((option) => ({ value: option.id, label: option.label, disabled: busy === 'stage' }))
	);

	const factsList = $derived(
		detail
			? [
					...(triggers[0] ? [{ label: 'Strongest signal', value: signed(triggers[0].contributionNats, 2) }] : []),
					{ label: 'Themes agreeing', value: detail.themeCount },
					{ label: 'Evidence', value: `${triggers.length} for · ${counters.length} against` },
					...(detail.band === 'PRIORITY' && detail.priorityHorizonDays !== null
						? [{ label: 'Holds Priority', value: `${Math.round(detail.priorityHorizonDays)} more days` }]
						: detail.whyNow.daysInBand !== null
							? [{ label: 'In this band', value: `${Math.round(detail.whyNow.daysInBand)} days` }]
							: []),
					{ label: 'Documents read', value: `${detail.scannedDocuments} · ${detail.silentDocuments} silent` }
				]
			: []
	);

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

<div class="flex h-full min-h-0 flex-col">
	<header class="flex min-h-head flex-none items-center justify-between gap-2 border-b border-ink px-4 py-3">
		<div class="min-w-0 flex-1">
			<h2 class="type-display text-xl leading-tight">{detail?.name ?? 'Reading'}</h2>
			{#if detail}
				<p class="type-sub mt-0.5">
					{#if detail.domain}<a href="https://{detail.domain}" target="_blank" rel="noreferrer noopener">{detail.domain}</a
						>{/if}{detail.hqCity ? ` · ${detail.hqCity}` : ''}{detail.country ? ` · ${detail.country}` : ''}{detail.industry
						? ` · ${detail.industry}`
						: ''}{detail.sizeBand ? ` · ${detail.sizeBand}` : ''}
				</p>
			{/if}
		</div>
		<div class="flex flex-none items-center gap-0.5">
			{#if detail}
				<Star
					companyId={detail.id}
					companyName={detail.name}
					{serviceLine}
					{saved}
					size="lg"
					onsaved={(on) => (saved = on)}
				/>
			{/if}
			<Menu items={overflow} onselect={runOverflow} label={`More actions for ${detail?.name ?? 'this company'}`} />
			<IconButton icon="close" label="Close detail" size="md" onclick={onclose} />
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
										class={[
											'text-micro font-semibold tracking-caps uppercase',
											current.whyNow.urgency === 'ACT_NOW' ? 'text-orange-ink' : 'text-ink-3'
										]}
									>
										{#if current.whyNow.urgency === 'ACT_NOW' || current.whyNow.urgency === 'THIS_WEEK'}
											<span class="text-orange">● </span>
										{/if}{current.whyNow.urgencyLabel}
									</span>
									<button
										class="inline-flex items-center gap-0.5 text-micro font-semibold tracking-caps text-ink-3 uppercase hover:text-ink"
										aria-expanded={showFacts}
										title="How this score was reached"
										onclick={() => (showFacts = !showFacts)}
									>
										Facts
										<Icon name={showFacts ? 'chevronDown' : 'chevronRight'} size="sm" />
									</button>
								</div>
							</div>
						{/each}
						<p class="sub dim">
							Rows sharing a theme are capped together, so one loud topic can never carry a company into
							Priority on its own. The ceiling is set from the Priority line, not chosen.
						</p>
					</div>
				{/if}

						<Panel>
							<div class="flex min-h-ctl-sm items-center justify-between gap-2">
								<h3 class="type-label">Evidence</h3>
								<span class="num text-sm leading-normal text-ink-4">{triggers.length} for · {counters.length} against</span>
							</div>
							<div class="flex flex-col gap-px">
								{#each triggers as row (row.evidenceId + row.spanStart)}
									<button
										class="group flex w-full items-baseline gap-1 py-0.5 text-left text-base leading-snug text-ink-2"
										onclick={() => openRow(row)}
									>
										<span class="size-1 flex-none -translate-y-px bg-accent"></span>
										<span
											class="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap group-hover:text-orange-deep group-hover:underline"
										>
											{row.questionTitle}
											{#if row.rationale}<span
													class="mt-px block overflow-hidden text-micro font-normal text-ellipsis whitespace-nowrap text-ink-3"
													>{row.rationale}</span
												>{/if}
										</span>
										<span class="num flex-none text-micro text-ink-4">{signed(row.contributionNats, 2)}</span>
									</button>
								{:else}
									<p class="text-sm leading-normal text-ink-4">No signal cleared the confidence floor for this policy.</p>
								{/each}
								{#if counters.length > 0}
									<p class="type-label mt-2">Against</p>
									{#each counters as row (row.evidenceId + row.spanStart)}
										<button
											class="group flex w-full items-baseline gap-1 py-0.5 text-left text-base leading-snug text-ink-2"
											onclick={() => openRow(row)}
										>
											<span class="size-1 flex-none -translate-y-px bg-oxblood"></span>
											<span
												class="min-w-0 flex-1 overflow-hidden text-ellipsis whitespace-nowrap group-hover:text-orange-deep group-hover:underline"
											>
												{row.questionTitle}
												{#if row.rationale}<span
														class="mt-px block overflow-hidden text-micro font-normal text-ellipsis whitespace-nowrap text-ink-3"
														>{row.rationale}</span
													>{/if}
											</span>
											<span class="num flex-none text-micro text-ink-4">{signed(row.contributionNats, 2)}</span>
										</button>
									{/each}
								{/if}
							</div>
						</Panel>

						<Panel>
							<div class="flex min-h-ctl-sm items-center justify-between gap-2">
								<h3 class="type-label">Stage</h3>
								{#if lead && lead.contactCount > 0}
									<span class="num text-sm leading-normal text-ink-4">
										{plural(lead.contactCount, 'contact')}{#if lead.lastContactedAt}
											· last {age(daysSince(lead.lastContactedAt))}{/if}{#if lead.channel} · {lead.channel}{/if}
									</span>
								{:else}
									<span class="text-sm leading-normal text-ink-4">not contacted yet</span>
								{/if}
							</div>
							<Segmented value={stage} options={stageOptions} label="Lead stage" fill class="w-full" onchange={(id) => setStage(id)} />
							<Button variant="ink" icon="mail" class="w-full" onclick={() => (outreachOpen = true)}>Generate outreach</Button>
							<Button
								class="w-full"
								disabled={busy === 'crm'}
								title={current.crmConfigured
									? 'Push this company to HubSpot'
									: 'No HubSpot token is configured, so the push is recorded as an intent'}
								onclick={pushCrm}
							>
								{#if busy === 'crm'}<Spinner />{:else}<Hubspot size={17} />{/if}
								{lead?.crmSyncedAt ? 'Re-push to HubSpot' : 'Push to HubSpot'}
							</Button>
						</Panel>
					{:else if tabValue === 'ledger'}
						<div class="p-3">
							<Ledger rows={scored} onopen={openRow} />
							<div class="num mt-3 border-t border-rule pt-2 text-sm">
								<div class="flex justify-between gap-3 py-px">
									<span class="text-ink-4">logit({current.baseRate.toFixed(2)})</span><span>{signed(current.baseLogit)}</span>
								</div>
								{#each scored as row (row.evidenceId + row.questionId + row.spanStart)}
									<div class="flex justify-between gap-3 py-px text-ink-4">
										<span>{row.questionTitle}{row.themeCapped ? ' · capped' : ''}</span>
										<span>{signed(row.contributionNats)}</span>
									</div>
								{/each}
								<div class="mt-1 flex justify-between gap-3 border-t border-rule pt-1 font-medium text-ink">
									<span>logit → fit</span><span>{signed(current.logit)} → {fit(current.probability)}</span>
								</div>
							</div>
							<div class="mt-4 border-t border-rule pt-3">
								{#each current.themes as item (item.name)}
									<div class="mb-2">
										<div class="flex justify-between text-base">
											<span>{theme(item.name)}</span>
											<span class="num">{signed(item.total)}{item.capped ? ' · capped' : ''}</span>
										</div>
										<div class="mt-1 h-1 bg-sunken">
											<span
												class={['block h-full', item.capped ? 'bg-ink' : 'bg-orange']}
												style:width={`${Math.min(100, (Math.abs(item.total) / current.themeCap) * 100)}%`}
											></span>
										</div>
									</div>
								{/each}
								<p class="text-sm leading-normal text-ink-4">Cap {nats(current.themeCap)}, derived from the Priority line.</p>
							</div>
						</div>
					{:else if tabValue === 'documents'}
						<div class="p-3">
							{#if (current?.belowFloor ?? []).length > 0}
								<div class="mb-3">
									<Panel>
										<div class="flex min-h-ctl-sm items-center justify-between gap-2">
											<h3 class="type-label">Read and not counted</h3>
											<span class="num text-sm leading-normal text-ink-4">{current?.belowFloor?.length ?? 0}</span>
										</div>
										{#each (current?.belowFloor ?? []).slice(0, 8) as row (row.evidenceId + row.spanStart)}
											<button
												class="flex w-full items-start gap-2 border-b border-rule py-2 text-left hover:bg-sunken"
												title={row.rationale ?? 'matched the rubric below the floor'}
												onclick={() => oncapture(row.evidenceId, { start: row.spanStart, end: row.spanEnd })}
											>
												<span class="num w-row flex-none text-ink-3">{row.confidence.toFixed(2)}</span>
												<span class="min-w-0 flex-1 text-sm leading-normal text-ink-2">
													{row.spanText.slice(0, 130)}
													<span
														class="mt-px block overflow-hidden text-micro font-normal text-ellipsis whitespace-nowrap text-ink-3"
													>
														{row.questionTitle} · {row.polarity.toLowerCase()}
													</span>
												</span>
											</button>
										{/each}
										<p class="text-sm leading-normal text-ink-4">Matched, scored under the floor, not counted.</p>
									</Panel>
								</div>
							{/if}

							<div class="mb-2">
								<Checkbox bind:checked={silentOnly}>Only the {silent.length} that produced nothing</Checkbox>
							</div>
							{#each documents as item (item.id)}
								<button
									class="flex w-full flex-col gap-0.5 border-b border-rule py-2 text-left transition-colors hover:bg-paper"
									onclick={() => oncapture(item.id, null)}
								>
									<span class="flex justify-between gap-3">
										<span class="type-label">{sourceKind(item.sourceKind)}</span>
										<span class="num text-sm leading-normal text-ink-4">{date(item.publishedAt ?? item.observedAt)}</span>
									</span>
									<span class="line-clamp-2 text-base leading-snug">{item.title ?? host(item.sourceUrl)}</span>
									<span class="text-sm leading-normal text-ink-3">
										{item.publisher} · {plural(item.charLength, 'char')}
										{#if item.signalCount > 0}· {plural(item.signalCount, 'signal')}{:else}· no signal{/if}
									</span>
								</button>
							{/each}
						</div>
					{/if}
				</div>
			{/snippet}
		</Tabs>
	{:else if error}
		<Note tone="warn" icon="alert">{error}</Note>
	{:else if loading}
		<div class="flex flex-1 items-center justify-center gap-2 bg-paper text-md text-ink-3">
			<Spinner />
			<span>Reading the scorecard</span>
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
		margin-bottom: var(--sp-3);
	}

	.below-row {
		display: grid;
		grid-template-columns: 34px 1fr;
		gap: var(--sp-2);
		width: 100%;
		padding: var(--sp-2);
		border: 0;
		border-bottom: 1px solid var(--rule);
		background: transparent;
		text-align: left;
		cursor: pointer;
	}

	.below-row:hover {
		background: var(--sunken);
	}

	.below-score {
		color: var(--ink-3);
	}

	.below-text {
		font-size: var(--fs-sm);
		color: var(--ink-2);
	}

	.why-line {
		display: block;
		margin-top: 1px;
		font-size: var(--fs-micro);
		color: var(--ink-3);
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
		gap: var(--sp-2);
		padding: var(--sp-3) var(--sp-4);
		border-bottom: 1px solid var(--ink);
		flex: none;
		min-height: var(--head-h);
	}

	.head-tools {
		display: flex;
		align-items: center;
		gap: 2px;
		flex: none;
	}

	h2 {
		font-size: var(--fs-xl);
		line-height: 1.15;
	}

	.sub {
		font-size: var(--fs-sm);
		line-height: 1.45;
		color: var(--ink-3);
		margin-top: 2px;
	}

	.veil {
		flex: 1;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--sp-2);
		background: var(--paper);
		color: var(--ink-3);
		font-size: var(--fs-md);
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
		gap: var(--sp-3);
		margin-bottom: var(--sp-2);
	}

	.gate {
		display: flex;
		align-items: flex-start;
		gap: var(--sp-1);
		margin-top: var(--sp-2);
		font-size: var(--fs-base);
		line-height: 1.45;
		color: var(--oxblood);
	}

	.gate .label {
		display: block;
		color: var(--oxblood);
	}

	.why {
		background: var(--paper);
		border-left: 2px solid var(--rule-strong);
	}

	.why[data-urgency='ACT_NOW'] {
		background: var(--orange-wash);
		border-left-color: var(--orange);
	}

	.why[data-urgency='THIS_WEEK'] {
		border-left-color: var(--orange-line);
	}

	.urgency {
		font-size: var(--fs-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.09em;
		color: var(--ink-3);
	}

	.urgency[data-urgency='ACT_NOW'] {
		color: var(--orange-ink);
	}

	.urgency[data-urgency='ACT_NOW']::before,
	.urgency[data-urgency='THIS_WEEK']::before {
		content: '● ';
		color: var(--orange);
	}

	.head-right {
		display: flex;
		align-items: center;
		gap: var(--sp-3);
	}

	.reveal {
		display: inline-flex;
		align-items: center;
		gap: 2px;
		font-size: var(--fs-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.09em;
		color: var(--ink-3);
	}

	.reveal:hover {
		color: var(--ink);
	}

	.why-facts {
		margin-top: var(--sp-2);
		padding-top: var(--sp-2);
		border-top: 1px solid var(--rule);
		font-size: var(--fs-base);
	}

	.why-facts dt {
		color: var(--ink-3);
	}

	.why-text {
		margin-top: var(--sp-1);
		font-size: var(--fs-base);
		line-height: 1.6;
		color: var(--ink-2);
	}

	.stage-seg {
		width: 100%;
	}

	.stage-seg button {
		flex: 1;
		justify-content: center;
		padding: 0 4px;
		font-size: var(--fs-sm);
	}

	.lines {
		display: flex;
		flex-direction: column;
		gap: 1px;
	}

	.against-label {
		margin-top: var(--sp-2);
	}

	.line {
		display: flex;
		align-items: baseline;
		gap: var(--sp-1);
		width: 100%;
		text-align: left;
		padding: 2px 0;
		font-size: var(--fs-base);
		color: var(--ink-2);
		line-height: 1.35;
	}

	.line:hover .line-text {
		color: var(--orange-deep);
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
		background: var(--accent);
	}

	.dot.neg {
		background: var(--oxblood);
	}

	.dot.neither {
		background: var(--rule-strong, var(--ink-3));
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
		font-size: var(--fs-micro);
		color: var(--ink-4);
	}

	.tabs {
		display: flex;
		align-items: center;
		padding: 0 var(--sp-3);
		border-bottom: 1px solid var(--ink);
		background: var(--surface);
		flex: none;
		min-height: 36px;
	}

	.tabs button {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
		padding: var(--sp-2) 0;
		margin-right: var(--sp-4);
		font-size: var(--fs-base);
		color: var(--ink-3);
		border-bottom: 2px solid transparent;
		margin-bottom: -1px;
		transition: color var(--ease), border-color var(--ease);
	}

	.tabs button:hover {
		color: var(--ink);
	}

	.tabs button[aria-selected='true'] {
		color: var(--ink);
		font-weight: 500;
		border-bottom-color: var(--orange);
	}

	.tabs .num {
		font-size: var(--fs-micro);
		color: var(--ink-4);
	}

	.chip {
		display: flex;
		align-items: center;
		gap: var(--sp-2);
		margin-left: auto;
		padding-left: var(--sp-2);
		min-width: 0;
	}

	.chip-fit {
		font-size: var(--fs-lg);
		font-weight: 500;
		line-height: 1;
	}

	.chip :global(.tag) {
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.tab-body {
		padding: var(--sp-3);
	}

	.doc-filter {
		margin-bottom: var(--sp-2);
		font-size: var(--fs-sm);
		color: var(--ink-3);
	}

	.sum {
		margin-top: var(--sp-3);
		border-top: 1px solid var(--rule);
		padding-top: var(--sp-2);
		font-size: var(--fs-sm);
	}

	.sum > div {
		display: flex;
		justify-content: space-between;
		gap: var(--sp-3);
		padding: 1px 0;
	}

	.sum .total {
		border-top: 1px solid var(--rule);
		margin-top: 5px;
		padding-top: 5px;
		font-weight: 500;
		color: var(--ink);
	}

	.themes {
		margin-top: var(--sp-4);
		border-top: 1px solid var(--rule);
		padding-top: var(--sp-3);
	}

	.theme {
		margin-bottom: var(--sp-2);
	}

	.theme-row {
		display: flex;
		justify-content: space-between;
		font-size: var(--fs-base);
	}

	.bar {
		height: 4px;
		background: var(--sunken);
		margin-top: 3px;
	}

	.bar span {
		display: block;
		height: 100%;
		background: var(--orange);
	}

	.bar span[data-capped='true'] {
		background: var(--ink);
	}

	.doc {
		display: flex;
		flex-direction: column;
		gap: 3px;
		width: 100%;
		text-align: left;
		padding: var(--sp-2) 0;
		border-bottom: 1px solid var(--rule);
		transition: background var(--ease);
	}

	.doc:hover {
		background: var(--paper);
	}

	.doc-top {
		display: flex;
		justify-content: space-between;
		gap: var(--sp-3);
	}

	.doc-title {
		font-size: var(--fs-base);
		line-height: 1.35;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
</style>
