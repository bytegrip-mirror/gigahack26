const STORAGE_KEY = 'gigahack26.serviceLine';
const REGION_KEY = 'gigahack26.region';

function readRegion(): string {
	try {
		return localStorage.getItem(REGION_KEY) ?? 'world';
	} catch {
		return 'world';
	}
}

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

	region = $state<string>(readRegion());

	set(value: string) {
		this.serviceLine = value;
		write(value);
	}

	setRegion(value: string) {
		this.region = value;
		try {
			localStorage.setItem(REGION_KEY, value);
		} catch {
			/* the app works without persistence */
		}
	}

	resolve(fallback: string) {
		if (!this.serviceLine) {
			this.serviceLine = fallback;
		}
		return this.serviceLine;
	}
}

export const session = new Session();
