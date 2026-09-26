export type Tone = 'info' | 'success' | 'warn' | 'error';

export type Toast = {
	id: number;
	message: string;
	tone: Tone;
};

class Toasts {
	items = $state<Toast[]>([]);
	#next = 1;

	push(message: string, tone: Tone = 'info', ttl = 7000) {
		const id = this.#next++;
		this.items = [...this.items, { id, message, tone }];
		if (ttl > 0) {
			setTimeout(() => this.dismiss(id), ttl);
		}
		return id;
	}

	dismiss(id: number) {
		this.items = this.items.filter((item) => item.id !== id);
	}
}

export const toasts = new Toasts();
