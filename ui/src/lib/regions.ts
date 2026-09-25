import type { RegionNode } from '$lib/api';

export function regionLabel(regions: RegionNode[], key: string): string {
	for (const continent of regions) {
		if (continent.key === key) return continent.name;
		for (const child of continent.children) {
			if (child.key === key) return child.name;
		}
	}
	return 'Worldwide';
}

export function regionChoices(regions: RegionNode[]): { key: string; label: string }[] {
	const out: { key: string; label: string }[] = [{ key: 'world', label: 'Worldwide' }];
	for (const continent of regions) {
		out.push({ key: continent.key, label: `${continent.name} — all` });
		for (const child of continent.children) {
			out.push({ key: child.key, label: `${continent.name} › ${child.name}` });
		}
	}
	return out;
}
