<script lang="ts" module>
	export type Tab = 'state' | 'people' | 'documents' | 'hubspot';
</script>

<script lang="ts">
	import Tabs from '$lib/ui/Tabs.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import Panel from '$lib/ui/Panel.svelte';
	import Button from '$lib/ui/Button.svelte';
	import IconButton from '$lib/ui/IconButton.svelte';
	import Menu from '$lib/ui/Menu.svelte';
	import type { MenuItem } from '$lib/ui/menu';
	import Hubspot from '$lib/components/Hubspot.svelte';
	import DocumentText from '$lib/components/DocumentText.svelte';
	import DimensionProof from '$lib/components/DimensionProof.svelte';
	import ContactStrip from '$lib/components/ContactStrip.svelte';
	import PeopleList from '$lib/components/PeopleList.svelte';
	import type { Component } from 'svelte';
	import type { Company, Icp, Person } from '$lib/mock/types';
	import { SOURCE_LABEL } from '$lib/mock/companies';
	import { events } from '$lib/mock/events';
	import { documentsOf, summary, type Contact, type Lead } from '$lib/mock/engine';
	import { hubspotCsv, recordFor } from '$lib/mock/hubspot';
	import { copy, save } from '$lib/export';
	import { toasts } from '$lib/toast.svelte';
	import { date, host, timestamp } from '$lib/format';

	let { lead, tab, ontab, onclose }: { lead: Lead; tab: Tab; ontab: (tab: Tab) => void; onclose: () => void } = $props();

	type ComposerProps = { open: boolean; company: Company; person: Person; icp: Icp; lead: Lead };
	const composers = import.meta.glob<{ default: Component<ComposerProps, object, 'open'> }>('../components/outreach/OutreachComposer.svelte', {
		eager: true
	});
	const Composer = Object.values(composers)[0]?.default ?? null;

	let composing = $state(false);
	let person = $state<Person | null>(null);
	let openDocument = $state<string | null>(null);
	let focusQuote = $state<string | null>(null);

	const docs = $derived(documentsOf(lead.company.id));
	const shownDocument = $derived(docs.find((document) => document.id === openDocument) ?? docs[0]);
	const quotesIn = $derived.by(() => {
		if (!shownDocument) return [];
		const fromEvents = events.filter((event) => event.documentId === shownDocument.id).map((event) => event.quote);
		const fromPeople = lead.contacts.filter((contact) => contact.person.documentId === shownDocument.id).map((contact) => contact.person.quote);
		return [...fromEvents, ...fromPeople];
	});
	const record = $derived(recordFor(lead));

	const tabs = $derived([
		{ value: 'state' as Tab, label: 'Signals', count: lead.moves.length },
		{ value: 'people' as Tab, label: 'People', count: lead.contacts.length },
		{ value: 'documents' as Tab, label: 'Documents', count: docs.length },
		{ value: 'hubspot' as Tab, label: 'HubSpot' }
	]);

	const overflow: MenuItem[] = [
		{ id: 'summary', label: 'Copy evidence summary', icon: 'copy' },
		{ id: 'link', label: 'Copy link', icon: 'link' },
		{ id: 'csv', label: 'Download HubSpot CSV', icon: 'download' },
		{ id: 'site', label: 'Open website', icon: 'external', separatorBefore: true },
		{ id: 'close', label: 'Close', icon: 'close' }
	];

	async function copyText(value: string, message: string) {
		const done = await copy(value);
		toasts.push(done ? message : 'Copy failed. Select the text by hand.', done ? 'success' : 'warn', 3000);
	}

	function downloadCsv() {
		save(`hubspot-${lead.company.id}-${lead.icp.id}.csv`, hubspotCsv([lead]));
		toasts.push('CSV saved in HubSpot import format', 'success', 3000);
	}

	function runOverflow(id: string) {
		if (id === 'summary') copyText(summary(lead), 'Evidence summary copied');
		if (id === 'link') copyText(window.location.href, 'Link copied');
		if (id === 'csv') downloadCsv();
		if (id === 'site') window.open(`https://${lead.company.domain}`, '_blank', 'noopener');
		if (id === 'close') onclose();
	}

	function openInDocument(documentId: string, quote: string) {
		openDocument = documentId;
		focusQuote = quote;
		ontab('documents');
	}

	function write(contact: Contact) {
		if (!Composer) return;
		person = contact.person;
		composing = true;
	}
</script>

<div class="flex h-full min-h-0 flex-col">
	<header class="flex min-h-head flex-none items-center justify-between gap-2 border-b border-ink px-4 py-3">
		<div class="min-w-0 flex-1">
			<h2 class="type-display text-xl leading-tight">{lead.company.name}</h2>
			<p class="type-sub mt-0.5 truncate">
				<a href="https://{lead.company.domain}" target="_blank" rel="noreferrer noopener">{lead.company.domain}</a>
				· {lead.company.city} · {lead.company.industry} · {lead.company.size} ·
				<span class="font-medium text-orange-ink">{lead.icp.serviceLine}</span>
			</p>
		</div>
		<div class="flex flex-none items-center gap-3">
			<Menu items={overflow} onselect={runOverflow} label={`More actions for ${lead.company.name}`} />
			<IconButton icon="close" label="Close detail" onclick={onclose} />
		</div>
	</header>

	<ContactStrip {lead} available={Composer !== null} onpick={write} onall={() => ontab('people')} />

	<Tabs value={tab} onchange={ontab} {tabs} label="Company detail" class="min-h-0 flex-1">
		{#snippet children(value)}
			<div class="scroll h-full">
				{#if value === 'state'}
					{#if lead.flags.length > 0}
						<section class="flex flex-col gap-1 border-b border-rule bg-oxblood-wash px-4 py-3">
							{#each lead.flags as flag (flag.id)}
								<p class="flex items-start gap-2 text-base leading-snug text-oxblood">
									<Tag tone="oxblood">Flag</Tag>
									<span>Openness {flag.base.toFixed(2)}: {flag.trigger.text}. “{flag.event.quote}”</span>
								</p>
							{/each}
						</section>
					{/if}
					<DimensionProof {lead} onopen={openInDocument} />
				{:else if value === 'people'}
					<PeopleList {lead} available={Composer !== null} onwrite={write} />
				{:else if value === 'documents'}
					<div class="flex flex-col">
						<ul class="flex flex-col border-b border-rule">
							{#each docs as document (document.id)}
								<li>
									<button
										class={[
											'flex w-full items-baseline gap-2 border-l-2 px-4 py-2 text-left transition-colors hover:bg-paper',
											shownDocument?.id === document.id ? 'border-l-orange bg-orange-wash' : 'border-transparent'
										]}
										onclick={() => {
											openDocument = document.id;
											focusQuote = null;
										}}
									>
										<span class="min-w-0 flex-1 truncate text-base">{document.title}</span>
										<span class="num flex-none text-micro text-ink-4">{host(document.url)}</span>
									</button>
								</li>
							{/each}
						</ul>
						{#if shownDocument}
							<Panel>
								<p class="text-md font-medium leading-snug">{shownDocument.title}</p>
								<dl class="grid-kv gap-x-3 gap-y-1 text-base">
									<dt class="text-ink-3">Source</dt>
									<dd class="text-right">
										<a href={shownDocument.url} target="_blank" rel="noreferrer noopener">{host(shownDocument.url)}</a>
										<span class="text-ink-4">· {SOURCE_LABEL[shownDocument.kind]}</span>
									</dd>
									<dt class="text-ink-3">Published</dt>
									<dd class="num text-right">{date(shownDocument.publishedAt)}</dd>
									<dt class="text-ink-3">Captured</dt>
									<dd class="num text-right">{timestamp(shownDocument.capturedAt)}</dd>
									<dt class="text-ink-3">Length</dt>
									<dd class="num text-right">{shownDocument.text.length} chars</dd>
								</dl>
								<p class="type-sub">Kept exactly as fetched. Highlights are the quotes behind events and named people.</p>
								{#key shownDocument.id + (focusQuote ?? '')}
									<DocumentText text={shownDocument.text} quotes={quotesIn} focus={focusQuote} scrollTo />
								{/key}
							</Panel>
						{/if}
					</div>
				{:else}
					<Panel>
						<div class="flex items-center justify-between gap-2">
							<span class="flex items-center gap-2"><Hubspot size={17} /><h3 class="type-label">What lands in HubSpot</h3></span>
							<Tag tone="dashed">Preview</Tag>
						</div>
						<p class="type-sub">
							One company record with the five levels, one contact per named person linked to it, and one note per changelog entry. Nothing is sent from the demo.
						</p>
						<Button variant="primary" icon="download" class="w-full" onclick={downloadCsv}>Download CSV (HubSpot import format)</Button>
					</Panel>
					<Panel title="Company record" heading="label">
						<dl class="flex flex-col">
							{#each record.company as property (property.name)}
								<div class="flex items-baseline gap-3 border-b border-rule py-1 last:border-b-0">
									<dt class="w-2/5 min-w-0 flex-none">
										<span class="block text-base text-ink-2">{property.label}</span>
										<span class="type-key block">{property.name}</span>
									</dt>
									<dd class={['min-w-0 flex-1 text-base leading-normal', !property.value && 'text-ink-4']}>{property.value || 'empty'}</dd>
								</div>
							{/each}
						</dl>
					</Panel>
					<Panel title={`Contacts (${record.contacts.length})`} heading="label">
						{#each record.contacts as contact (contact.name)}
							<div class="flex items-baseline gap-3 border-b border-rule py-1 last:border-b-0">
								<span class="w-2/5 min-w-0 flex-none text-base font-medium">{contact.name}</span>
								<span class="min-w-0 flex-1 text-base text-ink-2">{contact.role} <span class="num text-sm text-ink-3">· {contact.status} · {contact.messages} sent</span></span>
								<a class="flex-none text-sm" href={contact.url} target="_blank" rel="noreferrer noopener">{host(contact.url)}</a>
							</div>
						{:else}
							<p class="type-sub">No named person in the sources, so no contacts.</p>
						{/each}
					</Panel>
					<Panel title={`Notes, one per changelog entry (${record.notes.length})`} heading="label">
						{#each record.notes as note (note.title)}
							<div class="border-l-2 border-orange-line pl-3">
								<p class="text-base font-medium leading-snug">{note.title}</p>
								<p class="num text-sm text-ink-2">{note.moves}</p>
								<p class="text-base leading-normal text-ink-2">{note.body}</p>
								<a class="text-sm" href={note.url} target="_blank" rel="noreferrer noopener">{host(note.url)}</a>
							</div>
						{:else}
							<p class="type-sub">No changelog entries, so no notes.</p>
						{/each}
					</Panel>
				{/if}
			</div>
		{/snippet}
	</Tabs>
</div>

{#if Composer && person}
	<Composer bind:open={composing} company={lead.company} {person} icp={lead.icp} {lead} />
{/if}
