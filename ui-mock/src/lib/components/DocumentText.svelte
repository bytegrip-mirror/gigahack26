<script lang="ts">
	let {
		text,
		quotes,
		focus = null,
		whole = true,
		radius = 420,
		scrollTo = false
	}: { text: string; quotes: string[]; focus?: string | null; whole?: boolean; radius?: number; scrollTo?: boolean } = $props();

	function reveal(node: HTMLElement) {
		if (scrollTo) node.scrollIntoView({ block: 'center' });
	}

	type Segment = { text: string; marked: boolean; focused: boolean };

	const focusStart = $derived(focus ? text.indexOf(focus) : -1);

	const bounds = $derived.by(() => {
		if (whole || focusStart < 0 || !focus) return { from: 0, to: text.length };
		return { from: Math.max(0, focusStart - radius), to: Math.min(text.length, focusStart + focus.length + radius) };
	});

	const segments = $derived.by(() => {
		const ranges = quotes
			.map((quote) => ({ start: text.indexOf(quote), quote }))
			.filter((range) => range.start >= 0)
			.map((range) => ({ start: range.start, end: range.start + range.quote.length, focused: focus !== null && range.quote === focus }))
			.sort((a, b) => a.start - b.start);
		const parts: Segment[] = [];
		let cursor = bounds.from;
		for (const range of ranges) {
			const start = Math.max(range.start, cursor);
			const end = Math.min(range.end, bounds.to);
			if (end <= start) continue;
			if (start > cursor) parts.push({ text: text.slice(cursor, start), marked: false, focused: false });
			parts.push({ text: text.slice(start, end), marked: true, focused: range.focused });
			cursor = end;
		}
		if (cursor < bounds.to) parts.push({ text: text.slice(cursor, bounds.to), marked: false, focused: false });
		return parts;
	});
</script>

<pre
	class="m-0 overflow-auto border border-rule bg-paper p-3 font-ui text-base leading-relaxed break-words whitespace-pre-wrap">{#if bounds.from > 0}<span class="text-ink-4">… </span>{/if}{#each segments as segment, index (index)}{#if segment.marked && segment.focused}<mark class="font-medium outline-2 outline-orange" {@attach reveal}>{segment.text}</mark>{:else if segment.marked}<mark>{segment.text}</mark>{:else}{segment.text}{/if}{/each}{#if bounds.to < text.length}<span class="text-ink-4"> …</span>{/if}</pre>
