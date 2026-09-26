<script lang="ts">
	import { addBookmark, removeBookmark } from '$lib/api';
	import { workspace } from '$lib/workspace.svelte';
	import { toasts } from '$lib/toast.svelte';
	import Icon, { type IconSize } from '$lib/ui/Icon.svelte';

	let {
		companyId,
		companyName,
		serviceLine,
		saved = false,
		size = 'md',
		onsaved
	}: {
		companyId: string;
		companyName: string;
		serviceLine: string;
		saved?: boolean;
		size?: IconSize;
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
	class={[
		'inline-flex size-ctl-sm flex-none items-center justify-center rounded-sm border border-transparent transition-colors hover:bg-sunken',
		saved ? 'text-orange' : 'text-ink-4 hover:text-orange-deep',
		busy && 'opacity-50'
	]}
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
