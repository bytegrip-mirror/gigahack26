<script lang="ts">
	import { addBookmark, removeBookmark } from '$lib/api';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import Icon from './Icon.svelte';

	let {
		companyId,
		companyName,
		serviceLine,
		saved = false,
		size = 15,
		onsaved
	}: {
		companyId: string;
		companyName: string;
		serviceLine: string;
		saved?: boolean;
		size?: number;
		onsaved?: (saved: boolean) => void;
	} = $props();

	let busy = $state(false);

	async function toggle() {
		if (busy) return;
		busy = true;
		const wanted = !saved;
		try {
			if (wanted) {
				await addBookmark(companyId, serviceLine);
				toasts.push(`${companyName} saved.`, 'success');
			} else {
				await removeBookmark(companyId, serviceLine);
				toasts.push(`${companyName} removed from saved.`, 'info');
			}
			onsaved?.(wanted);
			workspace.savedChanged();
		} catch (cause) {
			toasts.push((cause as Error).message, 'error');
		} finally {
			busy = false;
		}
	}
</script>

<button
	class="icon-btn icon-btn-sm star"
	class:on={saved}
	class:busy
	type="button"
	aria-pressed={saved}
	title={saved ? `Remove ${companyName} from saved` : `Save ${companyName} for later`}
	aria-label={saved ? `Remove ${companyName} from saved` : `Save ${companyName} for later`}
	onclick={(event) => {
		event.stopPropagation();
		toggle();
	}}
>
	<Icon name="star" size={size} filled={saved} />
</button>

<style>
	.star {
		color: var(--ink-4);
	}

	.star:hover {
		color: var(--orange-deep);
	}

	.star.on {
		color: var(--orange);
	}

	.star.busy {
		opacity: 0.5;
	}
</style>
