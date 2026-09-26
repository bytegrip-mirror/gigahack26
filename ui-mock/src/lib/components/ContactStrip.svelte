<script lang="ts">
	import Tag from '$lib/ui/Tag.svelte';
	import Button from '$lib/ui/Button.svelte';
	import type { Contact, Lead } from '$lib/mock/engine';
	import { APPROACH_LABEL, approachOf } from '$lib/mock/approach';
	import { STATUS_LABEL, outreachLog } from '$lib/outreach-log.svelte';

	let {
		lead,
		limit = 3,
		available,
		onpick,
		onall
	}: { lead: Lead; limit?: number; available: boolean; onpick: (contact: Contact) => void; onall: () => void } = $props();

	const shown = $derived(lead.contacts.slice(0, limit));
</script>

<section class="flex flex-none flex-col border-b border-rule py-1" aria-label="Contacts">
	<div class="flex items-baseline justify-between gap-2 px-4 pt-1">
		<h3 class="type-label">Who to write to</h3>
		{#if lead.contacts.length > limit}
			<button type="button" class="text-sm text-orange-deep hover:underline" onclick={onall}>All {lead.contacts.length} people</button>
		{/if}
	</div>
	{#if shown.length === 0}
		<p class="px-4 py-1 text-sm text-ink-3">No named person at {lead.company.name} in the stored sources.</p>
	{/if}
	<ul class="flex flex-col">
		{#each shown as contact (contact.person.id)}
			{@const approach = approachOf(contact.person, lead)}
			{@const log = outreachLog.get(contact.person.id)}
			<li class="flex items-center gap-2 px-4 py-1">
				<span class="flex-none text-base font-medium">{contact.person.name}</span>
				<span class="min-w-0 flex-1 truncate text-sm text-ink-3">{contact.person.role}</span>
				<Tag tone={approach.level === 'WARM' ? 'wash' : approach.level === 'AWARE' ? 'default' : 'dashed'}>{APPROACH_LABEL[approach.level]}</Tag>
				<span class="num flex-none text-micro text-ink-3">{STATUS_LABEL[log.status]} · {log.messages} sent</span>
				<Button
					size="sm"
					icon="mail"
					disabled={!available}
					title={available ? `Write to ${contact.person.name}` : 'The email composer is not in this build yet'}
					onclick={() => onpick(contact)}>Write</Button
				>
			</li>
		{/each}
	</ul>
</section>
