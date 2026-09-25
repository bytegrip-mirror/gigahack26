<script lang="ts">
	import { fetchOutreach, type Outreach } from '$lib/api';
	import { host } from '$lib/format';
	import Icon from './Icon.svelte';

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

	$effect(() => {
		const chosen = channel;
		const line = serviceLine;
		loading = true;
		error = null;
		fetchOutreach(companyId, line, chosen)
			.then((result) => {
				if (channel !== chosen) return;
				draft = result;
				ondrafted?.(result.subject);
			})
			.catch((cause) => {
				if (channel === chosen) error = cause.message;
			})
			.finally(() => {
				if (channel === chosen) loading = false;
			});
	});

	$effect(() => {
		const onKey = (event: KeyboardEvent) => {
			if (event.key === 'Escape') onclose();
		};
		window.addEventListener('keydown', onKey);
		return () => window.removeEventListener('keydown', onKey);
	});

	const asText = $derived(
		draft
			? `Subject: ${draft.subject}\n\n${draft.body}\n\n${draft.citations
					.map((c) => `${c.marker} ${c.quote} — ${c.sourceUrl}`)
					.join('\n')}`
			: ''
	);

	async function copy() {
		try {
			await navigator.clipboard.writeText(asText);
			copied = true;
			setTimeout(() => (copied = false), 2000);
		} catch {
			copied = false;
		}
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
				<div class="seg" role="tablist" aria-label="Channel">
					<button role="tab" aria-selected={channel === 'email'} onclick={() => (channel = 'email')}>
						Email
					</button>
					<button role="tab" aria-selected={channel === 'inmail'} onclick={() => (channel = 'inmail')}>
						InMail
					</button>
				</div>
				<button class="icon-btn" onclick={onclose} aria-label="Close the draft" title="Close">
					<Icon name="close" size={16} />
				</button>
			</div>
		</header>

		<div class="body">
			{#if error}
				<p class="note warn">{error}</p>
			{:else if loading && !draft}
				<p class="note"><span class="spinner"></span> Composing</p>
			{:else if draft}
				<div class="cols">
					<div class="draft">
						<p class="label">Subject</p>
						<p class="subject dsp">{draft.subject}</p>
						<p class="label mt">Message</p>
						<pre>{draft.body}</pre>
						<p class="sub dim mt">
							Sentences are filled from computed values and verbatim passages. Nothing here can claim
							something the ledger does not.
						</p>
					</div>

					<aside class="cites">
						<p class="label">Citations</p>
						{#each draft.citations as citation (citation.marker)}
							<div class="cite">
								<div class="cite-head">
									<span class="key marker">{citation.marker}</span>
									<span class="sub dim">{host(citation.sourceUrl)}</span>
								</div>
								<p class="quote">“{citation.quote}”</p>
								<p class="sub dim">{citation.question}</p>
								<a class="sub" href={citation.sourceUrl} target="_blank" rel="noreferrer noopener">Open source</a>
							</div>
						{/each}
						{#if draft.citations.length === 0}
							<p class="sub dim">
								No positive evidence cleared the floor, so the draft makes no claim. Send it as a research
								question instead.
							</p>
						{/if}
					</aside>
				</div>
			{/if}
		</div>

		<footer>
			<button class="btn" onclick={onclose}>Close</button>
			<button class="btn btn-ink" onclick={copy} disabled={!draft}>{copied ? 'Copied' : 'Copy draft'}</button>
		</footer>
	</div>
</div>

<style>
	.scrim {
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
		width: min(1040px, 100%);
		max-height: 92vh;
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
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		padding: var(--sp-4);
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

	.draft .subject {
		font-size: var(--fs-lg);
		margin: 3px 0 0;
	}

	.mt {
		margin-top: var(--sp-4);
	}

	pre {
		margin: 3px 0 0;
		font-family: var(--font-ui);
		font-size: var(--fs-md);
		line-height: 1.6;
		white-space: pre-wrap;
		word-break: break-word;
	}

	.cites {
		border-left: 1px solid var(--rule);
		padding-left: var(--sp-4);
		display: flex;
		flex-direction: column;
		gap: var(--sp-3);
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
		.cols {
			grid-template-columns: 1fr;
		}
		.cites {
			border-left: 0;
			border-top: 1px solid var(--rule);
			padding-left: 0;
			padding-top: var(--sp-4);
		}
	}
</style>
