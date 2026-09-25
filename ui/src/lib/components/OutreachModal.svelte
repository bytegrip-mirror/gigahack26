<script lang="ts">
	import { fetchOutreach, type Outreach } from '$lib/api';
	import { host } from '$lib/format';
	import Modal from './Modal.svelte';

	let {
		companyId,
		companyName,
		serviceLine,
		onclose
	}: { companyId: string; companyName: string; serviceLine: string; onclose: () => void } = $props();

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
				if (channel === chosen) draft = result;
			})
			.catch((cause) => {
				if (channel === chosen) error = cause.message;
			})
			.finally(() => {
				if (channel === chosen) loading = false;
			});
	});

	const fullText = $derived(
		draft ? `Subject: ${draft.subject}\n\n${draft.body}\n\n${draft.citations.map((c) => `${c.marker} ${c.quote} — ${c.sourceUrl}`).join('\n')}` : ''
	);

	async function copy() {
		try {
			await navigator.clipboard.writeText(fullText);
			copied = true;
			setTimeout(() => (copied = false), 2000);
		} catch {
			copied = false;
		}
	}
</script>

<Modal
	title="Outreach draft"
	subtitle="{companyName} · every claim carries a citation to the capture it came from"
	wide
	{onclose}
>
	<div class="channel">
		<button class="btn" class:active={channel === 'email'} onclick={() => (channel = 'email')}>Email</button>
		<button class="btn" class:active={channel === 'inmail'} onclick={() => (channel = 'inmail')}>LinkedIn InMail</button>
		{#if draft}
			<span class="tiny muted" style="margin-left: auto">
				aimed at the {draft.persona}
			</span>
		{/if}
	</div>

	{#if error}
		<p class="card" style="padding:14px; color: var(--danger); border-color: var(--danger-line)">{error}</p>
	{:else if loading && !draft}
		<div class="row gap-8"><span class="spinner"></span><span class="muted">Composing from the ledger…</span></div>
	{:else if draft}
		<div class="grid">
			<div class="stack gap-12">
				<label class="stack gap-4">
					<span class="tiny muted">Subject</span>
					<input class="input" readonly value={draft.subject} />
				</label>
				<label class="stack gap-4">
					<span class="tiny muted">Message</span>
					<textarea class="textarea" rows="16" readonly value={draft.body}></textarea>
				</label>
				<p class="tiny muted">
					Composed from the scorecard's own ledger — the sentences are filled from computed values and verbatim
					passages, so nothing here can say something the evidence does not.
				</p>
			</div>

			<aside class="stack gap-12">
				<h3>Citations</h3>
				{#each draft.citations as citation (citation.marker)}
					<div class="citation">
						<div class="row gap-6">
							<span class="chip chip-orange mono">{citation.marker}</span>
							<span class="tiny muted">{host(citation.sourceUrl)}</span>
						</div>
						<p class="quote">“{citation.quote}”</p>
						<p class="tiny muted">{citation.question}</p>
						<a class="tiny" href={citation.sourceUrl} target="_blank" rel="noreferrer noopener">Open source ↗</a>
					</div>
				{/each}
				{#if draft.citations.length === 0}
					<p class="small muted">
						No positive evidence cleared the floor, so the draft makes no claim. Send it as a research
						question or leave this company out of the queue.
					</p>
				{/if}
			</aside>
		</div>
	{/if}

	{#snippet footer()}
		<button class="btn" onclick={onclose}>Close</button>
		<button class="btn btn-primary" onclick={copy} disabled={!draft}>
			{copied ? 'Copied' : 'Copy the draft'}
		</button>
	{/snippet}
</Modal>

<style>
	.channel {
		display: flex;
		gap: 6px;
		align-items: center;
		margin-bottom: 16px;
	}

	.channel .btn.active {
		background: var(--orange-050);
		border-color: var(--orange);
		color: var(--orange-ink);
	}

	.grid {
		display: grid;
		grid-template-columns: minmax(0, 1.5fr) minmax(240px, 1fr);
		gap: 22px;
	}

	aside {
		border-left: 1px solid var(--line);
		padding-left: 20px;
	}

	.citation {
		background: var(--surface-sunken);
		border: 1px solid var(--line);
		border-radius: var(--radius);
		padding: 10px;
		display: flex;
		flex-direction: column;
		gap: 5px;
	}

	.quote {
		font-size: 12.5px;
		line-height: 1.55;
		font-style: italic;
		color: var(--ink-2);
	}

	@media (max-width: 820px) {
		.grid {
			grid-template-columns: 1fr;
		}
		aside {
			border-left: 0;
			border-top: 1px solid var(--line);
			padding-left: 0;
			padding-top: 16px;
		}
	}
</style>
