export type Mode = 'light' | 'dark';

const KEY = 'gigahack26.theme';

function initial(): Mode {
	try {
		const held = localStorage.getItem(KEY);
		if (held === 'light' || held === 'dark') return held;
	} catch {
		/* the app works without persistence */
	}
	if (typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
	return 'light';
}

class Theme {
	mode = $state<Mode>(initial());

	toggle() {
		this.set(this.mode === 'dark' ? 'light' : 'dark');
	}

	set(mode: Mode) {
		this.mode = mode;
		document.documentElement.dataset.theme = mode;
		try {
			localStorage.setItem(KEY, mode);
		} catch {
			/* the app works without persistence */
		}
	}
}

export const theme = new Theme();
