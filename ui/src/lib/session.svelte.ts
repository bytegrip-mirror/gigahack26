const STORAGE_KEY = 'gigahack26.serviceLine';
const REGION_KEY = 'gigahack26.region';
const ALL_KEY = 'gigahack26.allLines';

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

function readAll(): boolean {
	try {
		return localStorage.getItem(ALL_KEY) === 'true';
	} catch {
		return false;
	}
}

function write(key: string, value: string) {
	try {
		localStorage.setItem(key, value);
	} catch {
		/* the app works without persistence */
	}
}

class Session {
	serviceLine = $state<string>(read() ?? '');

	allLines = $state<boolean>(readAll());

	region = $state<string>(readRegion());

	set(value: string) {
		this.serviceLine = value;
		this.allLines = false;
		write(STORAGE_KEY, value);
		write(ALL_KEY, 'false');
	}

	setAll() {
		this.allLines = true;
		write(ALL_KEY, 'true');
	}

	setRegion(value: string) {
		this.region = value;
		write(REGION_KEY, value);
	}

	scopeLabel(): string {
		return this.allLines ? 'All service lines' : this.serviceLine;
	}
}

export const session = new Session();
