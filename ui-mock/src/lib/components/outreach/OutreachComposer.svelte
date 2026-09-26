<script lang="ts">
	import Dialog from '$lib/ui/Dialog.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import { date } from '$lib/format';
	import { APPROACH_LABEL, approachOf } from '$lib/mock/approach';
	import type { Lead } from '$lib/mock/engine';
	import type { Company, Icp, Person } from '$lib/mock/types';
	import { STATUS_LABEL, outreachLog } from '$lib/outreach-log.svelte';
	import ComposerBody from './ComposerBody.svelte';

	let {
		open = $bindable(false),
		company,
		person,
		icp,
		lead
	}: { open?: boolean; company: Company; person: Person; icp: Icp; lead: Lead } = $props();

	const approach = $derived(approachOf(person, lead));
	const history = $derived(outreachLog.get(person.id));
</script>

<Dialog bind:open title={person.name} closeLabel="Close the composer">
	{#snippet subtitle()}
		{person.role} at {company.name}. {approach.reason}
	{/snippet}
	{#snippet tools()}
		<Tag tone="bare">
			{STATUS_LABEL[history.status]} · {history.messages} {history.messages === 1 ? 'message' : 'messages'}{history.lastAt ? ` · last ${date(history.lastAt)}` : ''}
		</Tag>
		<Tag tone={approach.level === 'WARM' ? 'wash' : approach.level === 'AWARE' ? 'default' : 'dashed'} title={approach.reason}>
			{APPROACH_LABEL[approach.level]}
		</Tag>
	{/snippet}
	{#key `${person.id}:${lead.id}`}
		<ComposerBody {company} {person} {icp} {lead} {approach} />
	{/key}
</Dialog>
