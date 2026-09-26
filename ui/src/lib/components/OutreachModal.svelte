<script lang="ts">
	import { fetchOutreach, type Outreach } from '$lib/api';
	import { toasts } from '$lib/toast.svelte';
	import { host } from '$lib/format';
	import Icon from './Icon.svelte';
	import Menu from './Menu.svelte';

	let {
		companyId,
		companyName,
		serviceLine,
		onclose,
		ondrafted
	}: {
		companyId: string;
		companyName: string;
		serviceLine: string;
		onclose: () => void;
		ondrafted?: (subject: string) => void;
	} = $props();

	let channel = $state<'email' | 'inmail'>('email');
	let draft = $state<Outreach | null>(null);
	let loading = $state(false);
	let error = $state<string | null>(null);
	let copied = $state(false);
	let marked = $state(false);
	let subject = $state('');
	let body = $state('');

	$effect(() => {
		const chosen = channel;
		const line = serviceLine;
		loading = true;
		error = null;
		fetchOutreach(companyId, line, chosen)
			.then((result) => {
				if (channel !== chosen) return;
				draft = result;
				subject = result.subject;
				body = result.body;
			})
			.catch((cause) => {
				if (channel === chosen) error = cause.message;
			})
			.finally(() => {
				if (channel === chosen) loading = false;
			});
	});

	const words = $derived(body.trim() ? body.trim().split(/\s+/).length : 0);
	const edited = $derived(draft !== null && (subject !== draft.subject || body !== draft.body));

	function mailto(): string {
		return `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
	}

	function send() {
		handled();
		window.location.href = mailto();
		toasts.push('Opening your mail client with the draft filled in.', 'info');
	}

	$effect(() => {
		const onKey = (event: KeyboardEvent) => {
			if (event.key === 'Escape') onclose();
		};
		window.addEventListener('keydown', onKey);
		return () => window.removeEventListener('keydown', onKey);
	});

	const asText = $derived(
		draft
			? `Subject: ${subject}\n\n${body}\n\n${draft.citations
					.map((c) => `${c.marker} ${c.quote} — ${c.sourceUrl}`)
					.join('\n')}`
			: ''
	);

	async function copy() {
		try {
			await navigator.clipboard.writeText(asText);
			copied = true;
			handled();
			setTimeout(() => (copied = false), 2000);
		} catch {
			copied = false;
		}
	}

	/**
	 * Recording the lead is a consequence of acting on the draft, not of looking
	 * at it: opening this sheet used to mark the company contacted on its own.
	 */
	function handled() {
		if (!marked) {
			marked = true;
			ondrafted?.(subject);
		}
	}

	async function openSource(url: string) {
		window.open(url, '_blank', 'noopener');
	}
</script>

<div
	class="scrim"
	role="presentation"
	onclick={(event) => {
		if (event.target === event.currentTarget) onclose();
	}}
>
	<div class="sheet" role="dialog" aria-modal="true" aria-label="Outreach draft">
		<header>
			<div>
				<p class="label">Outreach draft · {companyName}</p>
				{#if draft}<p class="sub dim">Aimed at {draft.persona}</p>{/if}
			</div>
			<div class="head-tools">
				<Menu
					text={channel === 'email' ? 'Email' : 'LinkedIn InMail'}
					label="Channel"
					title="The channel the draft is written for"
					align="end"
					items={[
						{ id: 'email', label: 'Email', hint: 'sign-off and signature', checked: channel === 'email' },
						{ id: 'inmail', label: 'LinkedIn InMail', hint: 'under 90 words', checked: channel === 'inmail' }
					]}
					onselect={(id) => (channel = id as 'email' | 'inmail')}
				/>
				<button class="icon-btn" onclick={onclose} aria-label="Close the draft" title="Close">
					<Icon name="close" size={16} />
				</button>
			</div>
		</header>

		<div class="body">
			{#if error}
				<p class="note warn">{error}</p>
			{:else if !draft}
				{#if loading}
					<div class="veil">
						<span class="spinner"></span>
						<span>Composing the first draft</span>
					</div>
				{/if}
			{:else}
				<div class="cols" class:waiting={loading}>
					<div class="col">
						<div class="col-head">
							<p class="label">{channel === 'email' ? 'Subject' : 'Message'}</p>
							<span class="sub dim num">
								{words} words{#if edited} · edited{/if}
							</span>
						</div>

						{#if channel === 'email'}
							<input class="input subject" bind:value={subject} aria-label="Subject" />
						{/if}

						<textarea class="textarea message" bind:value={body} aria-label="Message body"></textarea>

						<div class="col-foot">
							<button class="btn btn-sm" onclick={copy} disabled={!draft}>
								<Icon name="copy" size={14} />
								{copied ? 'Copied' : 'Copy with citations'}
							</button>
							<button class="btn btn-sm" onclick={() => { subject = draft?.subject ?? ''; body = draft?.body ?? ''; }} disabled={!edited}>
								Reset
							</button>
							<span class="spacer"></span>
							<span class="sub dim">Written from the ledger — edit freely before sending.</span>
						</div>
					</div>

					<aside class="col cites">
						<div class="col-head">
							<p class="label">Sources</p>
							<span class="sub dim num">{draft.citations.length}</span>
						</div>
						<div class="cite-list">
							{#each draft.citations as citation (citation.marker)}
								<div class="cite">
									<div class="cite-head">
										<span class="key marker">{citation.marker}</span>
										<span class="sub dim">{host(citation.sourceUrl)}</span>
										<button
											class="icon-btn icon-btn-sm"
											title="Open the source"
											aria-label={`Open ${host(citation.sourceUrl)}`}
											onclick={() => openSource(citation.sourceUrl)}
										>
											<Icon name="external" size={13} />
										</button>
									</div>
									<p class="quote">“{citation.quote}”</p>
									<p class="sub dim">{citation.question}</p>
								</div>
							{/each}
							{#if draft.citations.length === 0}
								<p class="sub dim">
									No positive evidence cleared the floor, so the draft makes no claim. Send it as a research
									question instead.
								</p>
							{/if}
						</div>
					</aside>
				</div>

				{#if loading}
					<div class="veil">
						<span class="spinner"></span>
						<span>Rewriting for {channel === 'email' ? 'email' : 'LinkedIn'}</span>
					</div>
				{/if}
			{/if}
		</div>

		<footer>
			<button class="btn" onclick={onclose}>Close</button>
			{#if channel === 'email'}
				<button class="btn btn-ink" onclick={send} disabled={!draft}>
					<Icon name="mail" size={15} />
					Send
				</button>
			{:else}
				<button class="btn btn-ink" onclick={copy} disabled={!draft}>
					<Icon name="copy" size={15} />
					{copied ? 'Copied' : 'Copy for LinkedIn'}
				</button>
			{/if}
		</footer>
	</div>
</div>

<style>
	.scrim {
		animation: fade-in var(--ease-enter) both;
		position: fixed;
		inset: 0;
		background: rgb(18 16 13 / 0.34);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: 4vh 20px;
		z-index: 150;
	}

	.sheet {
		animation: rise var(--ease-enter) both;
		width: min(1080px, 100%);
		height: min(760px, 92vh);
		background: var(--surface);
		border: 1px solid var(--ink);
		display: flex;
		flex-direction: column;
		min-height: 0;
	}

	header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--sp-4);
		padding: var(--sp-3);
		border-bottom: 1px solid var(--ink);
	}

	.head-tools {
		display: flex;
		align-items: center;
		gap: var(--sp-2);
	}

	.body {
		position: relative;
		flex: 1;
		min-height: 0;
		display: flex;
		overflow: hidden;
	}

	.veil {
		position: absolute;
		inset: 0;
		z-index: 3;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--sp-2);
		background: color-mix(in oklab, var(--paper) 72%, transparent);
		color: var(--ink-3);
		font-size: var(--fs-md);
	}

	.cols.waiting {
		opacity: 0.4;
	}

	.col {
		display: flex;
		flex-direction: column;
		min-height: 0;
		padding: var(--sp-4);
		gap: var(--sp-2);
	}

	.col:first-child {
		flex: 1.55;
	}

	.cites {
		flex: 1;
		min-width: 260px;
		border-left: 1px solid var(--rule);
		padding-left: var(--sp-4);
	}

	.col-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--sp-2);
		flex: none;
	}

	.subject {
		flex: none;
		font-family: var(--font-display);
		font-size: var(--fs-lg);
		font-weight: 500;
	}

	.message {
		flex: 1;
		min-height: 0;
		resize: none;
		font-family: var(--font-ui);
		font-size: var(--fs-md);
		line-height: 1.6;
	}

	.col-foot {
		display: flex;
		align-items: center;
		gap: var(--sp-1);
		flex: none;
	}

	.col-foot .spacer {
		margin-left: auto;
	}

	.cite-list {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		scrollbar-width: thin;
		display: flex;
		flex-direction: column;
		gap: var(--sp-3);
		padding-right: var(--sp-1);
	}

	footer {
		display: flex;
		justify-content: flex-end;
		gap: var(--sp-2);
		padding: var(--sp-2) var(--sp-3);
		border-top: 1px solid var(--rule);
		background: var(--paper);
	}

	.note {
		display: flex;
		align-items: center;
		gap: var(--sp-2);
		font-size: var(--fs-md);
		color: var(--ink-3);
	}

	.note.warn {
		color: var(--oxblood);
	}

	.cols {
		display: grid;
		grid-template-columns: minmax(0, 1.55fr) minmax(240px, 1fr);
		gap: var(--sp-5);
	}

	.cite {
		border-top: 1px solid var(--rule);
		padding-top: var(--sp-2);
	}

	.cite-head {
		display: flex;
		align-items: baseline;
		gap: var(--sp-2);
	}

	.marker {
		font-size: var(--fs-micro);
		color: var(--orange-ink);
	}

	.quote {
		margin-top: var(--sp-1);
		font-family: var(--font-display);
		font-size: var(--fs-md);
		font-style: italic;
		line-height: 1.45;
		color: var(--ink-2);
	}

	.sub {
		font-size: var(--fs-sm);
		line-height: 1.45;
		color: var(--ink-3);
		margin-top: 2px;
		display: block;
	}

	@media (max-width: 780px) {
		.body {
			flex-direction: column;
			overflow-y: auto;
		}
		.col {
			flex: none;
			padding: var(--sp-3);
		}
		.message {
			min-height: 180px;
		}
		.cites {
			border-left: 0;
			border-top: 1px solid var(--rule);
			padding-left: var(--sp-3);
		}
		.cite-list {
			overflow-y: visible;
		}
	}
</style>
