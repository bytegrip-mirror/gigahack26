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
	import { age, date, fit, host, nats, plural, signed, sourceKind, theme } from '$lib/format';
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
			else toasts.push(`HubSpot is not connected — ${result.reason}`, 'warn');
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
			toasts.push(value === 'ACCEPT' ? 'Counted as a qualified lead.' : 'Counted as rejected.', 'success');
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
		{ id: 'accept', label: 'Count as a qualified lead', icon: 'check', separatorBefore: true },
		{ id: 'reject', label: 'Count as rejected', icon: 'close', danger: true },
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
		{@const current = detail}
		<Tabs bind:value={tab} tabs={tabsList} label="Company detail" class="min-h-0 flex-1">
			{#snippet trailing()}
				{#if tab !== 'overview'}
					<span class="num text-lg leading-none font-medium">{fit(current.probability)}</span>
					<Tag band={current.band} class="truncate">{current.bandLabel}</Tag>
				{/if}
			{/snippet}
			{#snippet children(tabValue)}
				<div class="scroll h-full">
					{#if error}
						<Note tone="warn" icon="alert">{error}</Note>
					{:else if tabValue === 'overview'}
						<Panel>
							<div class="flex items-center justify-between gap-3">
								<Metrics>
									<Metric
										value={fit(current.probability)}
										label="Fit"
										tone={current.band === 'DISQUALIFIED' ? 'bad' : 'hot'}
										title="Probability this company is worth calling"
									/>
									<Metric value={current.signalCount} label="Signals" title="Evidence rows that cleared the floor" />
									<Metric value={current.themeCount} label="Themes" title="Independent kinds of evidence" />
								</Metrics>
								<Tag band={current.band}>{current.bandLabel}</Tag>
							</div>
							<ScoreMeter
								probability={current.probability}
								band={current.band}
								baseRate={current.baseRate}
								thresholds={{
									watch: current.watchThreshold,
									nurture: current.nurtureThreshold,
									priority: current.priorityThreshold
								}}
								showScale
							/>
							{#if current.gateReason}
								<p class="mt-2 flex items-start gap-1 text-base leading-normal text-oxblood">
									<Icon name="alert" size="sm" />
									<span><span class="block text-oxblood">Removed from the queue</span> {current.gateReason}</span>
								</p>
							{/if}
						</Panel>

						<section
							class={[
								'flex flex-col gap-2 border-b border-l-2 border-rule px-4 py-3 last:border-b-0',
								current.whyNow.urgency === 'ACT_NOW'
									? 'border-l-orange bg-orange-wash'
									: current.whyNow.urgency === 'THIS_WEEK'
										? 'border-l-orange-line bg-paper'
										: 'border-l-rule-strong bg-paper'
							]}
						>
							<div class="flex min-h-ctl-sm items-center justify-between gap-2">
								<h3 class="type-label">Why now</h3>
								<div class="flex items-center gap-3">
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
							<p class="mt-1 text-base leading-normal text-ink-2">{current.whyNow.narrative || current.whyNow.headline}</p>
							{#if showFacts}
								<div class="mt-2 border-t border-rule pt-2">
									<Facts facts={factsList} />
								</div>
							{/if}
						</section>

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
