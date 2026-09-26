<script lang="ts">
	import { untrack } from 'svelte';
	import Button from '$lib/ui/Button.svelte';
	import Checkbox from '$lib/ui/Checkbox.svelte';
	import Empty from '$lib/ui/Empty.svelte';
	import Field from '$lib/ui/Field.svelte';
	import Input from '$lib/ui/Input.svelte';
	import Panel from '$lib/ui/Panel.svelte';
	import Segmented from '$lib/ui/Segmented.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import Textarea from '$lib/ui/Textarea.svelte';
	import Hubspot from '$lib/components/Hubspot.svelte';
	import { icpStore } from '$lib/icps.svelte';
	import { copy } from '$lib/export';
	import { date } from '$lib/format';
	import { toasts } from '$lib/toast.svelte';
	import { logMessage } from '$lib/outreach-log.svelte';
	import { APPROACH_LABEL, type Approach } from '$lib/mock/approach';
	import { DIMENSION_LABEL, signed, type Lead } from '$lib/mock/engine';
	import {
		BLOCKS,
		LEVELS,
		LEVEL_HINT,
		body,
		ctaById,
		ctasFor,
		defaultSignal,
		draft,
		email,
		hubspotNote,
		mailto,
		signalOptions,
		strongestMove,
		words,
		type BlockKey,
		type Draft,
		type Level
	} from '$lib/mock/outreach';
	import type { Company, Icp, Person, Service } from '$lib/mock/types';

	let { company, person, icp, lead, approach }: { company: Company; person: Person; icp: Icp; lead: Lead; approach: Approach } =
		$props();

	const LIMIT = 120;
	const MAX_SIGNALS = 2;

	const initial = untrack(() => ({
		signal: defaultSignal(person, lead),
		service: icp.serviceId ?? icpStore.services[0]?.id ?? '',
		level: approach.level
	}));

	let selected = $state<string[]>(initial.signal ? [initial.signal] : []);
	let serviceId = $state(initial.service);
	let level = $state<Level>(initial.level);
	let ctaId = $state(ctasFor(initial.level)[0].id);
	let overrides = $state<Partial<Draft>>({});
	let confirming = $state(false);
	let showNote = $state(false);
	let loggedDraft = $state<string | null>(null);

	const options = $derived(signalOptions(person, lead));
	const chosen = $derived(selected.flatMap((id) => options.filter((option) => option.entry.event.id === id).map((option) => option.entry)));
	const service = $derived<Service>(
		icpStore.service(serviceId) ?? { id: serviceId, name: icp.serviceLine, description: '', outcome: '' }
	);
	const cta = $derived(ctaById(ctaId));
	const generated = $derived(draft({ person, service, entries: chosen, cta }));
	const blocks = $derived<Draft>({ ...generated, ...overrides });
	const text = $derived(body(person, blocks));
	const wordCount = $derived(words(text));
	const ready = $derived(chosen.length > 0);
	const note = $derived(hubspotNote(company, person, blocks, chosen));
	const message = $derived(email(person, blocks));
	const firstName = $derived(person.name.split(' ')[0]);

	function toggle(id: string) {
		selected = selected.includes(id) ? selected.filter((other) => other !== id) : [...selected, id].slice(-MAX_SIGNALS);
	}

	function edited(key: BlockKey): boolean {
		return overrides[key] !== undefined && overrides[key] !== generated[key];
	}

	function write(key: BlockKey, value: string | number | null | undefined) {
		overrides[key] = String(value ?? '');
	}

	function reset(key: BlockKey) {
		delete overrides[key];
	}

	function pickLevel(next: Level) {
		ctaId = ctasFor(next)[0].id;
	}

	function record() {
		if (loggedDraft === message) return;
		loggedDraft = message;
		logMessage(person.id);
	}

	async function copyEmail() {
		const ok = await copy(message);
		if (ok) record();
		toasts.push(ok ? `Copied the email to ${person.name}.` : 'The browser blocked the clipboard. Select the preview text and copy it.', ok ? 'success' : 'warn');
	}

	function openMail() {
		window.location.href = mailto(person, blocks);
	}

	function logNote() {
		confirming = false;
		record();
		toasts.push(`Mock: a HubSpot email activity for ${person.name} at ${company.name} is logged with ${chosen.length === 1 ? 'the signal and its source' : 'both signals and their sources'}.`, 'success');
	}
</script>

<div class="flex min-h-0 flex-1">
	<section class="scroll flex w-list flex-none flex-col border-r border-rule" aria-label="Inputs">
		<Panel title="Signal" tools={signalTools}>
			<p class="type-sub">Pick one or two. Signals from a source that names {firstName} come first.</p>
			{#if options.length === 0}
				<p class="text-base text-ink-3">No signal on this lead gives a reason to write. Wait for the next event.</p>
			{:else}
				<ul class="flex flex-col gap-3">
					{#each options as option (option.entry.event.id)}
						{@const entry = option.entry}
						{@const move = strongestMove(entry)}
						{@const checked = selected.includes(entry.event.id)}
						<li>
							<Checkbox {checked} onchange={() => toggle(entry.event.id)} disabled={!checked && selected.length >= MAX_SIGNALS}>
								<span class="flex flex-col gap-1">
									<span class="flex flex-wrap items-center gap-1 text-sm text-ink-3">
										<span class="num">{date(entry.event.date)}</span>
										<span class={['num', move.points < 0 ? 'text-oxblood' : 'text-ink']}>{DIMENSION_LABEL[move.dimension]} {signed(move.points, 2)}</span>
										{#if option.owned}<Tag tone="ink">Names {firstName}</Tag>{/if}
									</span>
									<span class="font-bold text-ink">{entry.event.summary}</span>
									<span class="block border-l-2 border-orange-line pl-2 text-sm leading-normal text-ink-2">“{entry.event.quote}”</span>
								</span>
							</Checkbox>
						</li>
					{/each}
				</ul>
			{/if}
		</Panel>
		<Panel title="Service">
			<Field label="What we offer" for="outreach-service" hint={service.outcome || undefined}>
				<Select id="outreach-service" label="Service" bind:value={serviceId} options={icpStore.services.map((item) => ({ value: item.id, label: item.name }))} />
			</Field>
		</Panel>
		<Panel title="Ask">
			<Segmented
				label="Approach level"
				fill
				bind:value={level}
				onchange={pickLevel}
				options={LEVELS.map((item) => ({ value: item, label: item === approach.level ? `${APPROACH_LABEL[item]} (fits)` : APPROACH_LABEL[item] }))}
			/>
			<p class="type-sub">{LEVEL_HINT[level]} Recommended for {firstName}: {APPROACH_LABEL[approach.level]}.</p>
			<Select label="Call to action" bind:value={ctaId} options={ctasFor(level).map((item) => ({ value: item.id, label: item.label }))} />
		</Panel>
	</section>

	<section class="scroll flex min-w-0 flex-1 flex-col border-r border-rule" aria-label="Draft">
		{#each BLOCKS as block (block.key)}
			<div class="flex flex-col gap-1 border-b border-rule px-4 py-3">
				<div class="flex min-h-ctl-sm items-center justify-between gap-2">
					<label for={`outreach-${block.key}`} class="type-label">{block.label}</label>
					{#if edited(block.key)}
						<span class="flex items-center gap-1">
							<Tag tone="bare">Edited</Tag>
							<Button size="sm" variant="quiet" icon="refresh" onclick={() => reset(block.key)}>Regenerate</Button>
						</span>
					{/if}
				</div>
				{#if block.key === 'subject'}
					<Input id="outreach-subject" bind:value={() => blocks.subject, (value) => write('subject', value)} />
				{:else}
					<Textarea
						id={`outreach-${block.key}`}
						rows={block.key === 'signoff' || block.key === 'cta' ? 2 : block.key === 'trigger' ? 6 : 4}
						bind:value={() => blocks[block.key], (value) => write(block.key, value)}
					/>
				{/if}
				<p class="text-sm leading-normal text-ink-4">{block.hint}</p>
			</div>
		{/each}
	</section>

	<section class="scroll flex min-w-0 flex-1 flex-col" aria-label="Preview and evidence">
		{#if !ready}
			<Empty>
				<p>Pick a signal to write the draft. The email starts from something {company.name} said in public.</p>
			</Empty>
		{:else}
			{#if confirming && showNote}
				<Panel title="HubSpot note">
					<p class="text-base text-ink-2">{note.title}</p>
					<ul class="flex flex-col gap-1 text-sm">
						{#each note.lines as line (line.label)}
							<li class="break-words text-ink-3"><span class="font-bold text-ink-2">{line.label}:</span> {line.value}</li>
						{/each}
					</ul>
				</Panel>
			{/if}
			<Panel title="Preview" tools={previewTools}>
				<p class="font-bold text-ink">{blocks.subject}</p>
				<p class="text-md leading-relaxed break-words whitespace-pre-wrap text-ink-2">{text}</p>
			</Panel>
			<Panel title="Evidence to check before sending">
				<ul class="flex flex-col gap-3">
					{#each chosen as entry (entry.event.id)}
						<li class="flex flex-col gap-1">
							<span class="block border-l-2 border-orange-line pl-2 text-base leading-normal text-ink-2">“{entry.event.quote}”</span>
							<span class="flex flex-wrap gap-x-2 text-sm text-ink-3">
								<a href={entry.document.url} target="_blank" rel="noreferrer noopener">{entry.document.publisher}</a>
								<span>· {entry.document.publishedAt ? `published ${date(entry.document.publishedAt)}` : 'no publish date'}</span>
								<span>· captured {date(entry.document.capturedAt)}</span>
							</span>
						</li>
					{/each}
				</ul>
				<p class="type-sub">The trigger quotes only these sentences. The diagnosis and value are general patterns for {service.name}, not claims about {company.name}.</p>
			</Panel>
		{/if}
	</section>
</div>

{#if confirming}
	<div class="flex flex-none items-center gap-2 border-t border-ink bg-sunken px-3 py-2" role="region" aria-label="HubSpot note">
		<Hubspot size={14} title="" />
		<p class="min-w-0 flex-1 truncate text-base text-ink-2">
			<span class="font-bold text-ink">Mock:</span> create an email activity on {company.name} for {person.name}, with {chosen.length === 1 ? 'the signal and its source' : 'both signals and their sources'}?
		</p>
		<Button variant="quiet" size="sm" onclick={() => (showNote = !showNote)}>{showNote ? 'Hide note' : 'Show note'}</Button>
		<Button variant="ink" size="sm" onclick={logNote}>Create note</Button>
		<Button variant="quiet" size="sm" onclick={() => (confirming = false)}>Cancel</Button>
	</div>
{/if}

<footer class="flex flex-none items-center gap-2 border-t border-rule p-3">
	<p class="min-w-0 flex-1 truncate text-sm text-ink-3">No email address is stored. The recipient stays empty. Find it in HubSpot or Apollo.</p>
	<Button disabled={!ready} onclick={() => (confirming = !confirming)}><Hubspot size={14} title="" />Log to HubSpot</Button>
	<Button icon="mail" disabled={!ready} onclick={openMail}>Open in mail app</Button>
	<Button variant="primary" icon="copy" disabled={!ready} onclick={copyEmail}>Copy email</Button>
</footer>

{#snippet signalTools()}
	<span class="num text-sm text-ink-3">{selected.length} of {MAX_SIGNALS}</span>
{/snippet}

{#snippet previewTools()}
	<Tag tone={wordCount > LIMIT ? 'oxblood' : 'default'} title="Keep the body under about {LIMIT} words">{wordCount} words</Tag>
{/snippet}
