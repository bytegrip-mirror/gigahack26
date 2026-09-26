<script lang="ts">
	import { fetchCompany, fetchServiceLines, type CompanyDetail } from '$lib/api';
	import { toasts } from '$lib/toast.svelte';
	import { age, date, fit, fitTone, fitWord, host, plural, safeHref, sourceKind, theme } from '$lib/format';
	import { copy } from '$lib/export';
	import Tag from '$lib/components/Tag.svelte';
	import CompanyMark from '$lib/components/CompanyMark.svelte';
	import SignalTrace from '$lib/components/SignalTrace.svelte';
	import LoadingVeil from '$lib/components/LoadingVeil.svelte';
	import Icon from '$lib/components/Icon.svelte';

	import { setPersonEmail } from '$lib/api';

	let { companyId, serviceLine }: { companyId: string; serviceLine: string } = $props();

	const TABS = [
		{ id: 'contacts', label: 'Contacts' },
		{ id: 'signals', label: 'Signals' },
		{ id: 'news', label: 'News' },
		{ id: 'history', label: 'History' },
		{ id: 'overview', label: 'Overview' }
	];

	let detail = $state<CompanyDetail | null>(null);
	let loading = $state(true);
	let error = $state<string | null>(null);
	let tab = $state('contacts');
	let emailDraft = $state<{ id: string; value: string } | null>(null);
	let savingEmail = $state(false);

	const withEmail = $derived((detail?.contacts ?? []).filter((contact) => contact.email).length);

	async function saveEmail(id: string, value: string) {
		savingEmail = true;
		try {
			await setPersonEmail(id, value);
			const fresh = await fetchCompany(companyId, serviceLine);
			detail = fresh;
			emailDraft = null;
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			savingEmail = false;
		}
	}
	let proposition = $state<string | null>(null);
	const profiles = $derived(detail?.profiles ?? []);

	const thresholds = $derived({
		watch: detail?.watchThreshold ?? 0.28,
		nurture: detail?.nurtureThreshold ?? 0.45,
		priority: detail?.priorityThreshold ?? 0.55
	});
	const word = $derived(detail ? fitWord(detail.probability, thresholds) : '');
	const tone = $derived(detail ? fitTone(detail.probability, thresholds) : 'fair');
	const readings = $derived(detail ? detail.ledger.filter((row) => !row.blocking) : []);
	const scored = $derived(detail ? detail.ledger.filter((row) => !row.blocking && row.tierNats !== 0) : []);
	const documents = $derived(detail ? detail.evidence : []);

	const spark = $derived.by(() => {
		if (!detail) return [];
		const months = new Array(12).fill(0) as number[];
		const now = Date.now();
		for (const row of scored) {
			const days = Math.max(0, (now - new Date(row.anchor).getTime()) / 86_400_000);
			const back = Math.min(11, Math.floor(days / 30));
			months[11 - back] += 1;
		}
		return months;
	});

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
				error = null;
			})
			.catch((cause) => {
				if (!cancelled) error = (cause as Error).message;
			})
			.finally(() => {
				if (!cancelled) loading = false;
			});
		fetchServiceLines(false)
			.then((lines) => {
				if (cancelled) return;
				proposition = lines.find((entry) => entry.key === line)?.valueProposition ?? null;
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	});

	async function copyLink() {
		const ok = await copy(window.location.href);
		toasts.push(ok ? 'Link copied.' : 'This browser blocked the clipboard.', ok ? 'success' : 'warn');
	}
</script>

<div class="view">
	<header class="topbar">
		<div class="ident">
			<div class="title">
				{#if detail}<CompanyMark name={detail.name} domain={detail.domain} size="lg" />{/if}
				<h1 class="dsp">{detail?.name ?? 'Company'}</h1>
				{#if detail}
					<span class="verdict dsp" data-tone={tone}>{word}</span>
				{/if}
			</div>
			{#if detail}
				<p class="sub">
					{#if detail.domain}<a href="https://{detail.domain}" target="_blank" rel="noreferrer noopener">{detail.domain}</a>{/if}{detail.hqCity
						? ` · ${detail.hqCity}`
						: ''}{detail.country ? ` · ${detail.country}` : ''}{detail.industry
						? ` · ${detail.industry}`
						: ''}{detail.sizeBand ? ` · ${detail.sizeBand}` : ''} · {detail.serviceLine}
				</p>
				{#if profiles.length > 0}
					<p class="profiles">
						{#each profiles as name (name)}<Tag variant="wash">{name}</Tag>{/each}
					</p>
				{/if}
			{/if}
		</div>
		<div class="topbar-tools">
			{#if detail}
				<SignalTrace spark={spark} title="Signal strength by month for the last year" />
			{/if}
			<button class="btn btn-sm" onclick={copyLink}><Icon name="link" size={14} /> Copy link</button>
			<a class="btn btn-sm" href="/?co={companyId}"><Icon name="board" size={14} /> On the board</a>
		</div>
	</header>

	<LoadingVeil show={loading} label="Reading the company" />

	{#if error}
		<p class="note warn"><Icon name="alert" size={15} /> {error}</p>
	{:else if detail}
		{#if detail.gateReason}
			<p class="gate"><Icon name="alert" size={15} /> <span><span class="label">Out of the queue</span> {detail.gateReason}</span></p>
		{/if}

		<div class="tabs" role="tablist" aria-label="Company detail">
			{#each TABS as option (option.id)}
				<button role="tab" aria-selected={tab === option.id} onclick={() => (tab = option.id)}>
					{option.label}
					{#if option.id === 'contacts'}<span class="num">{detail.contacts.length}</span>{/if}
					{#if option.id === 'signals'}<span class="num">{scored.length}</span>{/if}
					{#if option.id === 'news'}<span class="num">{documents.length}</span>{/if}
				</button>
			{/each}
		</div>

		<section class="body scroll">
			{#if tab === 'contacts'}
				<p class="lede">
					The people our documents named at this company, and the addresses we hold. An address is only ever
					one somebody gave us — a licensed provider or a rep — never one guessed from a name and a domain.
				</p>
				<table class="tbl contacts">
					<thead>
						<tr>
							<th scope="col" class="c-name">Name</th>
							<th scope="col">Role</th>
							<th scope="col" class="c-mail">Email</th>
							<th scope="col" class="c-stage">Stage</th>
							<th scope="col" class="c-src">Said</th>
						</tr>
					</thead>
					<tbody>
						{#each detail.contacts as contact (contact.id)}
							<tr>
								<td class="c-name">
									<span class="p-name">{contact.name}</span>
									{#if contact.linkedinUrl}
										<a class="link" href={safeHref(contact.linkedinUrl)} target="_blank" rel="noreferrer noopener">
											profile
										</a>
									{/if}
								</td>
								<td class="sub">{contact.role}</td>
								<td class="c-mail">
									{#if emailDraft?.id === contact.id}
										<span class="mail-edit">
											<input
												class="input input-sm"
												bind:value={emailDraft.value}
												placeholder="name@company.com"
												onkeydown={(event) => {
													if (event.key === 'Enter') saveEmail(contact.id, emailDraft?.value ?? '');
													if (event.key === 'Escape') emailDraft = null;
												}}
											/>
											<button class="btn btn-sm btn-ink" disabled={savingEmail} onclick={() => saveEmail(contact.id, emailDraft?.value ?? '')}>
												Save
											</button>
										</span>
									{:else if contact.email}
										<button
											class="mail"
											title="Click to correct it"
											onclick={() => (emailDraft = { id: contact.id, value: contact.email ?? '' })}
										>
											{contact.email}
										</button>
									{:else}
										<button class="btn btn-sm btn-quiet" onclick={() => (emailDraft = { id: contact.id, value: '' })}>
											<Icon name="plus" size={12} /> Add an address
										</button>
									{/if}
								</td>
								<td class="c-stage">
									<span class="p-stage" data-stage={contact.status}>{contact.statusLabel}</span>
									{#if contact.contactCount > 0}
										<span class="sub dim">{contact.contactCount}×</span>
									{/if}
								</td>
								<td class="c-src">
									{#if safeHref(contact.sourceUrl)}
										<a class="link" href={safeHref(contact.sourceUrl)} target="_blank" rel="noreferrer noopener">
											{host(contact.sourceUrl ?? '')}
										</a>
									{:else}
										<span class="sub dim">—</span>
									{/if}
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
				{#if detail.contacts.length === 0}
					<p class="sub dim">No document has named anybody at this company yet.</p>
				{:else if withEmail === 0}
					<p class="sub dim">
						None of them has an address on file. Read the sources, use a licensed provider from the People
						board, or type one in here.
					</p>
				{/if}
			{/if}

			{#if tab === 'signals'}
				<p class="lede">
					Every reading behind this score, strongest first. Each one is a sentence in a document we hold.
				</p>
				<div class="lines">
					{#each readings as row (row.evidenceId + row.questionId + row.spanStart)}
						<div class="line" data-polarity={row.polarity}>
							<div class="line-head">
								<span class="dot" data-polarity={row.polarity}></span>
								<span class="q">{row.questionTitle}</span>
								<span class="num amount" class:neg={row.contributionNats < 0}>
									{row.tierNats === 0 ? 'not counted' : row.contributionNats.toFixed(2)}
								</span>
							</div>
							{#if row.spanText}<blockquote>{row.spanText}</blockquote>{/if}
							<div class="line-foot">
								{#if safeHref(row.sourceUrl)}
									<a class="link" href={safeHref(row.sourceUrl)} target="_blank" rel="noreferrer noopener">
										{row.publisher}
									</a>
								{:else}
									<span>{row.publisher}</span>
								{/if}
								<span class="dim">· {sourceKind(row.sourceKind)} · {age(row.ageDays)}</span>
								{#if row.rationale}<span class="dim">· {row.rationale}</span>{/if}
							</div>
						</div>
					{:else}
						<p class="sub dim">No document answered a question about this company.</p>
					{/each}
				</div>
			{/if}

			{#if tab === 'news'}
				<p class="lede">Every document we hold on this company, newest first. Each opens at the source.</p>
				<ul class="docs">
					{#each documents as item (item.id)}
						<li>
							{#if safeHref(item.sourceUrl)}
								<a class="doc-title" href={safeHref(item.sourceUrl)} target="_blank" rel="noreferrer noopener">
									{item.title ?? host(item.sourceUrl)}
								</a>
							{:else}
								<span class="doc-title">{item.title ?? 'untitled'}</span>
							{/if}
							<span class="sub dim">
								{item.publisher} · {sourceKind(item.sourceKind)} · {date(item.publishedAt ?? item.observedAt)}
								{#if item.signalCount > 0}
									· answered {plural(item.signalCount, 'question')}
								{:else}
									· answered nothing
								{/if}
							</span>
						</li>
					{:else}
						<p class="sub dim">No document was found for this company.</p>
					{/each}
				</ul>
			{/if}

			{#if tab === 'history'}
				<p class="lede">What we have done about this company, and when.</p>
				<dl class="facts history-facts">
					<dt>Stage</dt>
					<dd>{detail.lead?.statusLabel ?? 'Not worked'}</dd>
					<dt>Times worked</dt>
					<dd class="num">{detail.lead?.contactCount ?? 0}</dd>
					<dt>Last contact</dt>
					<dd>{detail.lead?.lastContactedAt ? date(detail.lead.lastContactedAt) : 'never'}</dd>
					<dt>Channel</dt>
					<dd>{detail.lead?.channel ?? '—'}</dd>
					<dt>Last draft</dt>
					<dd>{detail.lead?.outreachSubject ?? 'no draft yet'}</dd>
					<dt>Pushed to HubSpot</dt>
					<dd>{detail.lead?.crmSyncedAt ? date(detail.lead.crmSyncedAt) : 'not pushed'}</dd>
					<dt>In the pipeline since</dt>
					<dd>{detail.lead ? date(detail.lead.updatedAt) : '—'}</dd>
				</dl>
			{/if}

			{#if tab === 'overview'}
				<div class="cards">
					<article class="card">
						<h2 class="label">Why this is a fit</h2>
						<p class="prose">{detail.whyNow.narrative || detail.whyNow.headline}</p>
					</article>

					<article class="card">
						<h2 class="label">What it is made of</h2>
						<div class="chips">
							{#each detail.themes as item (item.name)}<Tag>{theme(item.name)}</Tag>{/each}
							{#if detail.themes.length === 0}<span class="sub dim">No theme carries it.</span>{/if}
						</div>
						<p class="prose">
							{scored.length === 1 ? 'One reason' : `${scored.length} reasons`}, from
							{plural(detail.scannedDocuments, 'document')} read. {detail.silentDocuments > 0
								? `${detail.silentDocuments} of them answered nothing.`
								: ''}
						</p>
					</article>

					<article class="card">
						<h2 class="label">What we would offer</h2>
						<p class="prose">
							{proposition ?? `${detail.serviceLine} — no proposition written for this service line yet.`}
						</p>
					</article>

					<article class="card">
						<h2 class="label">What argues against calling</h2>
						{#if detail.whyNow.counters.length > 0}
							<ul class="plain">
								{#each detail.whyNow.counters as row (row.evidenceId + row.spanStart)}
									<li>{row.questionTitle}{#if row.rationale}<span class="dim"> — {row.rationale}</span>{/if}</li>
								{/each}
							</ul>
						{:else}
							<p class="prose">Nothing in the documents argues against it.</p>
						{/if}
					</article>
				</div>
			{/if}
		</section>
	{/if}
</div>

<style>
	.profiles {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
		margin-top: var(--spacing-1);
	}

	.title {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
	}

	.verdict {
		font-size: var(--text-xl);
		color: var(--color-ink-2);
	}

	.verdict[data-tone='strong'] {
		color: var(--color-orange-ink);
	}

	.verdict[data-tone='weak'] {
		color: var(--color-ink-3);
	}

	.gate {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		margin: 0;
		padding: var(--spacing-2) var(--spacing-4);
		background: var(--color-oxblood-wash);
		border-bottom: 1px solid var(--color-oxblood-line);
		font-size: var(--text-base);
		color: var(--color-oxblood);
	}

	.tabs {
		display: flex;
		align-items: center;
		padding: 0 var(--spacing-4);
		border-bottom: 1px solid var(--color-ink);
		background: var(--color-surface);
		flex: none;
		min-height: 38px;
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

	.body {
		flex: 1;
		min-height: 0;
		padding: var(--spacing-4);
	}

	.lede {
		margin: 0 0 var(--spacing-3);
		font-size: var(--text-base);
		color: var(--color-ink-3);
		max-width: 90ch;
	}

	.lines {
		display: flex;
		flex-direction: column;
	}

	.line {
		padding: var(--spacing-3) 0;
		border-bottom: 1px solid var(--color-rule);
		max-width: 110ch;
	}

	.line-head {
		display: flex;
		align-items: baseline;
		gap: var(--spacing-2);
	}

	.dot {
		width: 6px;
		height: 6px;
		flex: none;
		background: var(--color-accent);
		transform: translateY(-1px);
	}

	.line[data-polarity='NEGATIVE'] .dot,
	.line[data-polarity='DISQUALIFYING'] .dot {
		background: var(--color-oxblood);
	}

	.q {
		flex: 1;
		font-size: var(--text-md);
		font-weight: 500;
		color: var(--color-ink);
	}

	.amount {
		font-size: var(--text-base);
		color: var(--color-ink-3);
	}

	.amount.neg {
		color: var(--color-oxblood);
	}

	blockquote {
		margin: var(--spacing-2) 0 0;
		padding-left: var(--spacing-3);
		border-left: 2px solid var(--color-orange);
		font-family: var(--font-display);
		font-size: var(--text-md);
		line-height: 1.5;
		color: var(--color-ink-2);
	}

	.line-foot {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
		margin-top: var(--spacing-2);
		font-size: var(--text-sm);
	}

	.docs {
		list-style: none;
		margin: 0;
		padding: 0;
		max-width: 110ch;
	}

	.docs li {
		padding: var(--spacing-2) 0;
		border-bottom: 1px solid var(--color-rule);
	}

	.doc-title {
		display: block;
		font-size: var(--text-md);
		color: var(--color-ink);
	}

	.doc-title:hover {
		color: var(--color-orange-deep);
	}

	.history-facts {
		max-width: 60ch;
		font-size: var(--text-md);
	}

	.history-facts dt {
		color: var(--color-ink-3);
	}

	.history-facts dd {
		margin-bottom: var(--spacing-1);
	}

	.contacts .c-name {
		width: 26%;
	}

	.contacts .c-mail {
		width: 26%;
	}

	.contacts .c-stage {
		width: 130px;
	}

	.contacts .c-src {
		width: 170px;
	}

	.contacts .sub {
		display: block;
	}

	.p-name {
		display: block;
		font-family: var(--font-display);
		font-size: var(--text-md);
		font-weight: 500;
	}

	.mail {
		font-family: var(--font-ui);
		font-size: var(--text-base);
		color: var(--color-ink);
		text-decoration: underline;
		text-underline-offset: 2px;
		text-decoration-color: var(--color-rule-strong);
		cursor: pointer;
	}

	.mail:hover {
		text-decoration-color: var(--color-orange);
		color: var(--color-orange-ink);
	}

	.mail-edit {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
	}

	.mail-edit .input {
		max-width: 220px;
	}

	.p-stage {
		display: inline-block;
		font-size: var(--text-micro);
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.07em;
		padding: 1px 5px;
		border: 1px solid var(--color-rule-strong);
		color: var(--color-ink-3);
	}

	.p-stage[data-stage='QUALIFIED'] {
		border-color: var(--color-accent);
		color: var(--color-accent);
	}

	.p-stage[data-stage='REJECTED'] {
		border-color: var(--color-oxblood-line);
		color: var(--color-oxblood);
	}

	.p-stage[data-stage='CONTACTED'],
	.p-stage[data-stage='REPLIED'] {
		border-color: var(--color-orange-line);
		color: var(--color-orange-ink);
	}

	.cards {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
		gap: var(--spacing-3);
		max-width: 1200px;
	}

	.card {
		border: 1px solid var(--color-rule);
		padding: var(--spacing-3);
		background: var(--color-paper);
	}

	.prose {
		margin: var(--spacing-2) 0 0;
		font-size: var(--text-base);
		line-height: 1.55;
		color: var(--color-ink-2);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 4px;
		margin-top: var(--spacing-2);
	}

	.plain {
		margin: var(--spacing-2) 0 0;
		padding-left: var(--spacing-4);
		font-size: var(--text-base);
		line-height: 1.5;
		color: var(--color-ink-2);
	}

	.dim {
		color: var(--color-ink-3);
	}
</style>
