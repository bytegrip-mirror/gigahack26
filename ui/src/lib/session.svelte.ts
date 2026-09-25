const STORAGE_KEY = 'gigahack26.serviceLine';

function read(): string | null {
	try {
		return localStorage.getItem(STORAGE_KEY);
	} catch {
		return null;
	}
}

function write(value: string) {
	try {
		localStorage.setItem(STORAGE_KEY, value);
	} catch {
		/* the app works without persistence */
	}
}

class Session {
	serviceLine = $state<string>(read() ?? '');
	meta = $state<import('./api').Meta | null>(null);

	set(value: string) {
		this.serviceLine = value;
		write(value);
	}

	resolve(fallback: string) {
		if (!this.serviceLine) {
			this.serviceLine = fallback;
		}
		return this.serviceLine;
	}
}

export const session = new Session();
