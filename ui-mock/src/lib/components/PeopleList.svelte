<script lang="ts">
	import Tag from '$lib/ui/Tag.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Select from '$lib/ui/Select.svelte';
	import Empty from '$lib/ui/Empty.svelte';
	import Icon from '$lib/ui/Icon.svelte';
	import type { Contact, Lead } from '$lib/mock/engine';
	import { APPROACH_LABEL, approachOf } from '$lib/mock/approach';
	import { STATUSES, STATUS_LABEL, outreachLog, setStatus, type OutreachStatus } from '$lib/outreach-log.svelte';
	import { host } from '$lib/format';

	let { lead, available, onwrite }: { lead: Lead; available: boolean; onwrite: (contact: Contact) => void } = $props();

	let expanded = $state<string | null>(null);

	const statusOptions = STATUSES.map((status) => ({ value: status, label: STATUS_LABEL[status] }));
</script>

{#if lead.contacts.length === 0}
	<Empty>
		<Icon name="inbox" size="xl" />
		<p>No named person at {lead.company.name} in the stored sources.</p>
	</Empty>
{:else}
	<table class="table-grid">
		<thead>
			<tr>
				<th scope="col">Name and role</th>
				<th scope="col" class="w-1/8">Approach</th>
				<th scope="col" class="w-1/4">Status</th>
				<th scope="col" class="w-1/12"><span class="block text-right">Sent</span></th>
				<th scope="col" class="w-1/6"><span class="sr-only">Write</span></th>
			</tr>
		</thead>
		<tbody>
			{#each lead.contacts as contact (contact.person.id)}
				{@const person = contact.person}
				{@const approach = approachOf(person, lead)}
				{@const log = outreachLog.get(person.id)}
				{@const open = expanded === person.id}
				<tr class="cursor-pointer transition-colors hover:bg-paper" onclick={() => (expanded = open ? null : person.id)}>
					<td class="text-base font-medium">
						<span class="flex items-center gap-1">
							<span class="flex-none text-ink-3"><Icon name={open ? 'chevronDown' : 'chevronRight'} size="sm" /></span>
							<span class="truncate">{person.name}</span>
						</span>
						<span class="block truncate pl-4 text-sm font-normal text-ink-3" title={person.role}>{person.role}</span>
						{#if open}
							<a class="mt-1 block truncate pl-4 text-sm font-normal" href={person.sourceUrl} target="_blank" rel="noreferrer noopener" onclick={(event) => event.stopPropagation()}>
								Role source: {host(person.sourceUrl)}
							</a>
						{/if}
					</td>
					<td><Tag tone={approach.level === 'WARM' ? 'wash' : approach.level === 'AWARE' ? 'default' : 'dashed'}>{APPROACH_LABEL[approach.level]}</Tag></td>
					<td onclick={(event) => event.stopPropagation()}>
						<Select size="sm" label={`Outreach status for ${person.name}`} options={statusOptions} value={log.status} onchange={(status: OutreachStatus) => setStatus(person.id, status)} />
					</td>
					<td class="num text-right text-base">{log.messages}</td>
					<td onclick={(event) => event.stopPropagation()}>
						<Button
							size="sm"
							icon="mail"
							disabled={!available}
							title={available ? `Write to ${person.name}` : 'The email composer is not in this build yet'}
							onclick={() => onwrite(contact)}>Write</Button
						>
					</td>
				</tr>
			{/each}
		</tbody>
	</table>
{/if}
