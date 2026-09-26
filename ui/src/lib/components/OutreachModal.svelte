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

	function mailto(): string {
		return `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
	}

	function send() {
		handled();
		window.location.href = mailto();
		toasts.push('Opening your mail client with the draft filled in.', 'info');
	}

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

<Dialog bind:open kind="dialog" title={`Outreach draft · ${companyName}`} closeLabel="Close the draft" {onclose}>
	{#snippet subtitle()}{#if draft}Aimed at {draft.persona}{/if}{/snippet}

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

	<div class="relative flex min-h-0 flex-1 flex-col">
		{#if error}
			<Note tone="warn">{error}</Note>
		{:else if !draft}
			<LoadingVeil show={loading} label="Composing the first draft" />
		{:else}
			<div class={['flex min-h-0 flex-1 gap-5 overflow-hidden max-sm:flex-col max-sm:overflow-y-auto', loading ? 'opacity-40' : '']}>
				<div class="flex min-h-0 w-3/5 flex-none flex-col gap-2 p-4 max-sm:w-full max-sm:p-3">
					<div class="flex flex-none items-baseline justify-between gap-2">
						<p class="type-label">{channel === 'email' ? 'Subject' : 'Message'}</p>
						<span class="num type-sub">
							{words} words{#if edited} · edited{/if}
						</span>
					</div>

					{#if channel === 'email'}
						<Input bind:value={subject} aria-label="Subject" variant="display" class="w-full flex-none" />
					{/if}

					<Textarea bind:value={body} aria-label="Message body" resize={false} class="min-h-0 flex-1" />

					<div class="flex flex-none items-center gap-1">
						<Button size="sm" icon="copy" onclick={copy} disabled={!draft}>{copied ? 'Copied' : 'Copy with citations'}</Button>
						<Button
							size="sm"
							onclick={() => {
								subject = draft?.subject ?? '';
								body = draft?.body ?? '';
							}}
							disabled={!edited}>Reset</Button
						>
						<span class="ml-auto type-sub">Written from the ledger — edit freely before sending.</span>
					</div>
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
