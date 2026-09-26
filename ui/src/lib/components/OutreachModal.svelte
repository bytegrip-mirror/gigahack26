<script lang="ts">
	import { fetchOutreach, type Outreach } from '$lib/api';
	import { toasts } from '$lib/toast.svelte';
	import { host } from '$lib/format';
	import Dialog from '$lib/ui/Dialog.svelte';
	import IconButton from '$lib/ui/IconButton.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import Note from '$lib/ui/Note.svelte';
	import LoadingVeil from '$lib/ui/LoadingVeil.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';

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

	let open = $state(true);
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

	const provenance = $derived.by(() => {
		if (!draft) return '';
		if (draft.engine.includes('@draft')) return 'Written by the model';
		const reason = draft.engine
			.split(',')
			.slice(1)
			.join(',')
			.trim();
		if (reason.includes('no model')) return 'Drafted from a template — no writing model is configured';
		if (reason.includes('failed')) return 'Drafted from a template — the writing model was not reachable';
		return 'Drafted from a template';
	});

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
				{#if draft}
					<p class="sub dim">Aimed at {draft.persona}</p>
					<p class="sub dim">{provenance}</p>
				{/if}
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

	{#snippet tools()}
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
	{/snippet}

	{#snippet footer()}
		<Button onclick={onclose}>Close</Button>
		{#if channel === 'email'}
			<Button variant="ink" icon="mail" onclick={send} disabled={!draft}>Send</Button>
		{:else}
			<Button variant="ink" icon="copy" onclick={copy} disabled={!draft}>{copied ? 'Copied' : 'Copy for LinkedIn'}</Button>
		{/if}
	{/snippet}

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
							<span class="sub dim">Edit freely before sending.</span>
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
									No source supports a claim, so the draft makes none. Send it as a research question
									instead.
								</p>
							{/if}
						</div>
					</aside>
				</div>

				<aside class="flex min-h-0 w-2/5 flex-none flex-col gap-2 border-l border-rule p-4 max-sm:w-full max-sm:border-l-0 max-sm:border-t max-sm:p-3">
					<div class="flex flex-none items-baseline justify-between gap-2">
						<p class="type-label">Sources</p>
						<span class="num type-sub">{draft.citations.length}</span>
					</div>
					<div class="scroll flex min-h-0 flex-1 flex-col gap-3 pr-1">
						{#each draft.citations as citation (citation.marker)}
							<div class="border-t border-rule pt-2">
								<div class="flex items-baseline gap-2">
									<span class="type-key">{citation.marker}</span>
									<span class="type-sub">{host(citation.sourceUrl)}</span>
									<IconButton
										icon="external"
										size="sm"
										title="Open the source"
										label={`Open ${host(citation.sourceUrl)}`}
										onclick={() => openSource(citation.sourceUrl)}
									/>
								</div>
								<p class="mt-1 font-display text-md text-ink-2 italic leading-snug">“{citation.quote}”</p>
								<p class="type-sub">{citation.question}</p>
							</div>
						{/each}
						{#if draft.citations.length === 0}
							<p class="type-sub">
								No positive evidence cleared the floor, so the draft makes no claim. Send it as a research
								question instead.
							</p>
						{/if}
					</div>
				</aside>
			</div>

			<LoadingVeil show={loading} label={`Rewriting for ${channel === 'email' ? 'email' : 'LinkedIn'}`} />
		{/if}
	</div>
</Dialog>
