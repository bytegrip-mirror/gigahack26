const FORMULA_START = /^[=+@\t\r]/;
const NUMBER = /^-?\d+(\.\d+)?([eE][+-]?\d+)?$/;

function cell(value: unknown): string {
	if (value === null || value === undefined) return '';
	const text = String(value);
	const risky = FORMULA_START.test(text) || (text.startsWith('-') && !NUMBER.test(text));
	return `"${(risky ? `'${text}` : text).replace(/"/g, '""')}"`;
}

export function csv(header: string[], rows: unknown[][]): string {
	return [header, ...rows].map((row) => row.map(cell).join(',')).join('\r\n');
}

export function save(filename: string, text: string, type = 'text/csv;charset=utf-8') {
	const url = URL.createObjectURL(new Blob([text], { type }));
	const link = document.createElement('a');
	link.href = url;
	link.download = filename;
	document.body.appendChild(link);
	link.click();
	link.remove();
	URL.revokeObjectURL(url);
}

export async function copy(text: string): Promise<boolean> {
	try {
		await navigator.clipboard.writeText(text);
		return true;
	} catch {
		return false;
	}
}
