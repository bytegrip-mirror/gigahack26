import type { IconName } from './icons';

export type MenuItem = {
	id: string;
	label: string;
	icon?: IconName;
	hint?: string;
	danger?: boolean;
	disabled?: boolean;
	checked?: boolean;
	separatorBefore?: boolean;
	heading?: boolean;
};
