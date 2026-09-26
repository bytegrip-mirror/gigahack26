<script lang="ts">
	import Menu from './Menu.svelte';
</script>

<Menu
	icon="bell"
	align="end"
	label="Notifications"
	title="Notifications"
	items={[{ id: 'none', label: 'Nothing new', disabled: true }]}
	onselect={() => {}}
/>
