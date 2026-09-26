<script lang="ts">
	import { apiBase, fetchEvidence, type EvidenceDetail } from '$lib/api';
	import { confidence, date, host, plural, sourceKind, tier, timestamp } from '$lib/format';
	import Dialog from '$lib/ui/Dialog.svelte';
	import Button from '$lib/ui/Button.svelte';
	import Tag from '$lib/ui/Tag.svelte';
	import Note from '$lib/ui/Note.svelte';
	import Spinner from '$lib/ui/Spinner.svelte';

	let {
		evidenceId,
		focus,
		onclose
	}: { evidenceId: string; focus: { start: number; end: number } | null; onclose: () => void } = $props();

	let open = $state(true);
	let detail = $state<EvidenceDetail | null>(null);
	let error = $state<string | null>(null);
	let whole = $state(false);

	const CONTEXT = 320;

	$effect(() => {
		const id = evidenceId;
		let cancelled = false;
		detail = null;
		error = null;
		fetchEvidence(id)
			.then((result) => {
				if (!cancelled) detail = result;
			})
			.catch((cause) => {
				if (!cancelled) error = cause.message;
			});
		return () => {
			cancelled = true;
		};
	});

	let pane = $state<HTMLPreElement | null>(null);

	$effect(() => {
		const node = pane;
		const highlight = capture.span;
		if (!node || !highlight) return;
		const mark = node.querySelector('mark');
		if (mark) {
			mark.scrollIntoView({ block: 'center' });
		}
	});

	const capture = $derived.by(() => {
		const text = detail?.text ?? '';
		if (!detail || !text) return { before: '', span: '', after: '', truncated: false, whole: true };
		if (whole) return { before: '', span: '', after: text, truncated: false, whole: true };
		if (!focus) {
			const end = Math.min(text.length, CONTEXT * 2);
			return {
				before: text.slice(0, end),
				span: '',
				after: '',
				truncated: end < text.length,
				whole: end >= text.length
			};
		}
		const start = Math.max(0, focus.start - CONTEXT);
		const end = Math.min(text.length, focus.end + CONTEXT);
		return {
			before: text.slice(start, focus.start),
			span: text.slice(focus.start, focus.end),
			after: text.slice(focus.end, end),
			truncated: end < text.length || start > 0,
			whole: start === 0 && end === text.length
		};
	});
</script>

<Dialog bind:open kind="sheet" title={detail?.title ?? 'Reading'} label="Stored capture" closeLabel="Close the capture" {onclose}>
	{#snippet subtitle()}Stored capture{/snippet}

	{#snippet footer()}
		<a
			href={`${apiBase}/api/evidence/${evidenceId}/capture`}
			target="_blank"
			rel="noreferrer noopener"
			class="inline-flex h-ctl-sm items-center justify-center gap-1 rounded-sm border border-rule-strong bg-surface px-2 text-sm font-medium whitespace-nowrap text-ink transition-colors hover:border-ink-3 hover:bg-sunken hover:no-underline"
			>Raw capture</a
		>
		{#if detail}
			<a
				href={detail.sourceUrl}
				target="_blank"
				rel="noreferrer noopener"
				class="inline-flex h-ctl-sm items-center justify-center gap-1 rounded-sm border border-rule-strong bg-surface px-2 text-sm font-medium whitespace-nowrap text-ink transition-colors hover:border-ink-3 hover:bg-sunken hover:no-underline"
				>Open source</a
			>
		{/if}
		<Button variant="ink" size="sm" onclick={onclose}>Close</Button>
	{/snippet}

	<div class="scroll min-h-0 flex-1 p-4">
		{#if error}
			<Note tone="warn">{error}</Note>
		{:else if !detail}
			<Note><Spinner /> Loading</Note>
		{:else}
			<dl class="grid-kv gap-x-3 gap-y-1 text-base">
				<dt class="text-ink-3">Source</dt>
				<dd class="text-right">
					<a href={detail.sourceUrl} target="_blank" rel="noreferrer noopener">{host(detail.sourceUrl)}</a>
				</dd>
				<dt class="text-ink-3">Authority</dt>
				<dd class="num text-right">×{detail.authority.toFixed(2)}</dd>
				<dt class="text-ink-3">Published</dt>
				<dd class="text-right">
					{date(detail.publishedAt)}
					<span class="text-ink-4">· {detail.anchorKind === 'published' ? 'drives decay' : 'undated, fetch time used'}</span>
				</dd>
				<dt class="text-ink-3">Captured</dt>
				<dd class="num text-right">{timestamp(detail.observedAt)}</dd>
				<dt class="text-ink-3">Hash</dt>
				<dd class="num text-right text-ink-4">{detail.contentHash ? detail.contentHash.slice(0, 30) : '—'}</dd>
				<dt class="text-ink-3">Length</dt>
				<dd class="num text-right">{plural(detail.charLength, 'char')}</dd>
			</dl>

			{#if detail.signals.length > 0}
				<section class="mt-4 border-t border-rule pt-3">
					<h3 class="type-label mb-2">Signals in this document</h3>
					<ul class="flex flex-col gap-3">
						{#each detail.signals as signal (signal.questionId + String(signal.spanStart))}
							<li class="border-l-2 border-orange-line pl-3">
								<div class="flex flex-wrap items-center gap-2">
									<Tag tone="wash">{tier(signal.tier)}</Tag>
									<span class="type-key" title={signal.questionKey}>{signal.questionTitle}</span>
									{#if signal.blocking}<Tag tone="oxblood">disqualifying</Tag>{/if}
									<span class="num text-micro text-ink-4">c {confidence(signal.confidence)} · chars {signal.spanStart}–{signal.spanEnd}</span>
								</div>
								<p class="mt-1 text-base text-ink-2">{signal.questionText}</p>
								{#if signal.rationale}<p class="mt-1 type-sub">{signal.rationale}</p>{/if}
							</li>
						{/each}
					</ul>
				</section>
			{/if}

			<section class="mt-4 border-t border-rule pt-3">
				<div class="mb-2 flex items-center justify-between">
					<h3 class="type-label">Capture</h3>
					<Button size="sm" onclick={() => (whole = !whole)}>
						{whole ? 'Passage only' : `All ${detail.charLength} chars`}
					</Button>
				</div>
				<pre
					bind:this={pane}
					class="m-0 overflow-auto border border-rule bg-paper p-3 text-base leading-relaxed break-words whitespace-pre-wrap"
					>{#if !capture.whole && !whole && capture.before}<span class="text-ink-4">…</span>{/if}{capture.before}{#if capture.span}<mark>{capture.span}</mark>{/if}{capture.after}{#if capture.truncated}<span class="text-ink-4">…</span>{/if}</pre>
				<p class="mt-1 type-sub">
					{#if capture.span}
						Characters {focus?.start}–{focus?.end} of the stored capture. The quote is a slice of what the
						crawler fetched, not text a model wrote.
					{:else}
						No signal cleared the floor for this document under the current policy. It is kept so you can see
						what was read and rejected.
					{/if}
				</p>
			</section>
		{/if}
	</div>
</Dialog>
