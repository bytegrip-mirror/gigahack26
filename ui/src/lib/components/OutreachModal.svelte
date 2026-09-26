<script lang="ts">
	import { fetchOutreach, rewriteOutreach, type Outreach } from '$lib/api';
	import { toasts } from '$lib/toast.svelte';
	import { host } from '$lib/format';
	import { portal } from '$lib/portal';
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

	const PROMPTS = [
		{ label: 'Shorter', ask: 'Cut it to under 60 words' },
		{ label: 'Lead with cost', ask: 'Lead with the cost angle' },
		{ label: 'Warmer', ask: 'Warmer, less formal' },
		{ label: 'Ask for a call', ask: 'Ask for fifteen minutes next week' },
		{ label: 'No ask', ask: 'Softer — drop the ask' },
		{ label: 'Industry proof', ask: 'Say we have done this in their industry' }
	];

	let channel = $state<'email' | 'inmail'>('email');
	let draft = $state<Outreach | null>(null);
	let loading = $state(false);
	let rewriting = $state(false);
	let error = $state<string | null>(null);
	let copied = $state(false);
	let marked = $state(false);
	let subject = $state('');
	let body = $state('');
	let instruction = $state('');
	let to = $state('');
	let applied = $state<string | null>(null);

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
				to = result.recipientEmail ?? '';
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
	const writeable = $derived(draft !== null && draft.engine.includes('@draft'));

	const provenance = $derived.by(() => {
		if (!draft) return '';
		if (draft.engine.includes('@draft')) return 'Written by AI from the evidence';
		const reason = draft.engine.split(',').slice(1).join(',').trim();
		if (reason.includes('no model')) return 'Template, no writing model';
		if (reason.includes('failed')) return 'Template, model not reachable';
		return 'Template';
	});

	async function steer(prompt: string) {
		const asked = prompt.trim();
		if (!asked || rewriting) return;
		applied = asked;
		await rewrite(asked);
	}

	async function rewrite(asked: string) {
		rewriting = true;
		try {
			const result = await rewriteOutreach({ companyId, serviceLine, channel, instruction: asked });
			draft = result;
			subject = result.subject;
			body = result.body;
			if (result.recipientEmail) to = result.recipientEmail;
			if (!result.engine.includes('@draft')) {
				toasts.push('No writing model is configured, so the wording could not be changed. Edit it by hand below.', 'warn');
			}
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			rewriting = false;
		}
	}

	function mailto(): string {
		const who = to.trim() ? encodeURIComponent(to.trim()) : '';
		return `mailto:${who}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
	}

	function send() {
		handled();
		window.location.href = mailto();
		toasts.push('Opening your mail client with the draft filled in.', 'info');
	}

	$effect(() => {
		const onKey = (event: KeyboardEvent) => {
			if (event.key !== 'Escape') return;
			event.preventDefault();
			event.stopPropagation();
			onclose();
		};
		window.addEventListener('keydown', onKey, true);
		return () => window.removeEventListener('keydown', onKey, true);
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
	use:portal
	role="presentation"
	onclick={(event) => {
		if (event.target === event.currentTarget) onclose();
	}}
>
	<div class="sheet" role="dialog" aria-modal="true" aria-label="Outreach draft">
		<header>
			<div class="ident">
				<h2 class="dsp title">Outreach to {companyName}</h2>
				{#if draft}
					<p class="sub">For {draft.persona} · {provenance}</p>
				{/if}
			</div>
			<button class="icon-btn" onclick={onclose} aria-label="Close the draft" title="Close">
				<Icon name="close" size={16} />
			</button>
		</header>

		<div class="body">
			<section class="mail" class:waiting={loading || rewriting}>
				{#if error}
					<p class="note warn">{error}</p>
				{:else if !draft}
					<p class="note"><span class="spinner"></span> Writing the first draft</p>
				{:else}
					{#if channel === 'email'}
						<label class="field">
							<span class="label">To</span>
							<input class="input" bind:value={to} placeholder="Type an address" aria-label="Who this goes to" />
						</label>
						<p class="sub hint">
							{#if draft.recipientName}
								{draft.recipientName}{draft.recipientEmail ? ' · on file' : ' · no address on file'}
							{:else}
								No contact on file
							{/if}
						</p>
						<label class="field">
							<span class="label">Subject</span>
							<input class="input subject" bind:value={subject} aria-label="Subject" />
						</label>
					{/if}

					<textarea class="textarea message" bind:value={body} aria-label="Message body"></textarea>

					<div class="mail-foot">
						<span class="sub num">{words} words{#if edited} · edited{/if}</span>
						<span class="spacer"></span>
						<button class="btn btn-sm btn-quiet" onclick={() => { subject = draft?.subject ?? ''; body = draft?.body ?? ''; }} disabled={!edited}>
							Reset
						</button>
						<button class="btn btn-sm" onclick={copy}>
							<Icon name="copy" size={14} />
							{copied ? 'Copied' : 'Copy'}
						</button>
					</div>
				{/if}

				{#if loading || rewriting}
					<div class="veil">
						<span class="spinner"></span>
						<span>{rewriting ? 'Rewriting' : 'Writing'}</span>
					</div>
				{/if}
			</section>

			<aside class="side">
				<div class="group">
					<p class="label">Channel</p>
					<div class="seg" role="tablist" aria-label="Channel">
						<button role="tab" aria-selected={channel === 'email'} onclick={() => (channel = 'email')}>
							<Icon name="mail" size={13} /> Email
						</button>
						<button role="tab" aria-selected={channel === 'inmail'} onclick={() => (channel = 'inmail')}>
							LinkedIn
						</button>
					</div>
				</div>

				<div class="group">
					<p class="label">Quick edits</p>
					<div class="options">
						{#each PROMPTS as prompt (prompt.label)}
							<button
								class="option"
								aria-pressed={applied === prompt.ask}
								title={prompt.ask}
								disabled={rewriting || loading || !writeable}
								onclick={() => steer(prompt.ask)}
							>
								{prompt.label}
							</button>
						{/each}
					</div>
					<form
						class="ask"
						onsubmit={(event) => {
							event.preventDefault();
							applied = null;
							void rewrite(instruction);
						}}
					>
						<input
							class="input input-sm"
							bind:value={instruction}
							placeholder="Or type a change"
							aria-label="What to change in the draft"
							disabled={!writeable}
						/>
						<button class="btn btn-sm btn-ink" type="submit" disabled={rewriting || loading || !instruction.trim() || !writeable}>
							Go
						</button>
					</form>
					{#if draft && !writeable}
						<p class="sub">No writing model here. Edit the text by hand.</p>
					{/if}
				</div>

				<div class="group sources">
					<p class="label">Sources <span class="num dim">{draft?.citations.length ?? 0}</span></p>
					<div class="cite-list">
						{#each draft?.citations ?? [] as citation (citation.marker)}
							<button class="cite" title={citation.question} onclick={() => openSource(citation.sourceUrl)}>
								<span class="cite-head">
									<span class="key">{citation.marker}</span>
									<span class="sub">{host(citation.sourceUrl)}</span>
									<Icon name="external" size={12} />
								</span>
								<span class="quote">“{citation.quote}”</span>
							</button>
						{:else}
							<p class="sub">No sources, so the draft makes no claims.</p>
						{/each}
					</div>
				</div>
			</aside>
		</div>

		<footer>
			<span class="sub">
				{channel === 'email' ? 'Opens your mail app. Nothing is sent from here.' : 'Copies the text for LinkedIn.'}
			</span>
			<span class="spacer"></span>
			<button class="btn" onclick={onclose}>Close</button>
			{#if channel === 'email'}
				<button class="btn btn-primary" onclick={send} disabled={!draft}>
					<Icon name="mail" size={15} />
					Send
				</button>
			{:else}
				<button class="btn btn-primary" onclick={copy} disabled={!draft}>
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
		background: rgb(0 0 0 / 0.45);
		display: flex;
		align-items: center;
		justify-content: center;
		padding: var(--spacing-5);
		z-index: 150;
	}

	.sheet {
		animation: rise var(--ease-enter) both;
		width: min(1040px, 100%);
		height: min(680px, 100%);
		background: var(--color-surface);
		border: 1px solid var(--color-ink);
		display: flex;
		flex-direction: column;
		min-height: 0;
	}

	header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--spacing-4);
		padding: var(--spacing-3) var(--spacing-4);
		border-bottom: 1px solid var(--color-rule);
		flex: none;
	}

	.ident {
		min-width: 0;
	}

	.title {
		font-size: var(--text-xl);
		line-height: 1.1;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.body {
		flex: 1;
		min-height: 0;
		display: grid;
		grid-template-columns: minmax(0, 1fr) 280px;
	}

	.mail {
		position: relative;
		display: flex;
		flex-direction: column;
		gap: var(--spacing-2);
		padding: var(--spacing-4);
		min-height: 0;
	}

	.mail.waiting > :not(.veil) {
		opacity: 0.4;
	}

	.field {
		display: grid;
		grid-template-columns: 56px minmax(0, 1fr);
		align-items: center;
		gap: var(--spacing-2);
	}

	.hint {
		margin: -4px 0 0 64px;
	}

	.subject {
		font-weight: 700;
	}

	.message {
		flex: 1;
		min-height: 0;
		resize: none;
		font-family: var(--font-ui);
		font-size: var(--text-md);
		font-weight: 400;
		line-height: 1.6;
	}

	.mail-foot {
		display: flex;
		align-items: center;
		gap: var(--spacing-1);
		flex: none;
	}

	.spacer {
		margin-left: auto;
	}

	.veil {
		position: absolute;
		inset: 0;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: var(--spacing-2);
		color: var(--color-ink-3);
		font-size: var(--text-md);
	}

	.side {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-4);
		padding: var(--spacing-4);
		background: var(--color-paper);
		border-left: 1px solid var(--color-rule);
		min-height: 0;
	}

	.group {
		display: flex;
		flex-direction: column;
		gap: var(--spacing-2);
	}

	.seg {
		align-self: flex-start;
	}

	.options {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--spacing-1);
	}

	.option {
		height: var(--spacing-ctl);
		padding: 0 var(--spacing-2);
		border: 1px solid var(--color-rule-strong);
		background: var(--color-surface);
		color: var(--color-ink);
		font-size: var(--text-base);
		font-weight: 700;
		text-align: left;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		transition: border-color var(--ease), background var(--ease);
	}

	.option:hover:not(:disabled) {
		border-color: var(--color-ink);
	}

	.option[aria-pressed='true'] {
		border-color: var(--color-orange);
		background: var(--color-orange-wash);
	}

	.option:disabled {
		color: var(--color-ink-4);
		cursor: not-allowed;
	}

	.ask {
		display: flex;
		gap: var(--spacing-1);
	}

	.ask .input {
		flex: 1;
		min-width: 0;
	}

	.sources {
		flex: 1;
		min-height: 0;
	}

	.cite-list {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		scrollbar-width: thin;
		display: flex;
		flex-direction: column;
		gap: var(--spacing-1);
	}

	.cite {
		display: flex;
		flex-direction: column;
		gap: 2px;
		padding: var(--spacing-2);
		border: 1px solid var(--color-rule);
		background: var(--color-surface);
		text-align: left;
		color: var(--color-ink-2);
	}

	.cite:hover {
		border-color: var(--color-ink);
	}

	.cite-head {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
	}

	.cite-head .sub {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.quote {
		font-size: var(--text-sm);
		line-height: 1.4;
		display: -webkit-box;
		-webkit-line-clamp: 2;
		line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	footer {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		padding: var(--spacing-2) var(--spacing-4);
		border-top: 1px solid var(--color-rule);
		flex: none;
	}

	.note {
		display: flex;
		align-items: center;
		gap: var(--spacing-2);
		font-size: var(--text-md);
		color: var(--color-ink-3);
	}

	.note.warn {
		color: var(--color-oxblood);
	}

	@media (max-width: 780px) {
		.scrim {
			padding: 0;
		}

		.sheet {
			height: 100%;
		}

		.body {
			grid-template-columns: 1fr;
			overflow-y: auto;
		}

		.message {
			min-height: 200px;
		}

		.side {
			border-left: 0;
			border-top: 1px solid var(--color-rule);
		}
	}
</style>
