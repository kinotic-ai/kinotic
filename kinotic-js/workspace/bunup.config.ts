import { defineWorkspace } from 'bunup'

// https://bunup.dev/docs/guide/workspaces

export default defineWorkspace(
	[
		{
			name: 'core',
			root: 'packages/core'
		},
		{
			name: 'idl',
			root: 'packages/idl'
		},
		{
			name: 'os-api',
			root: 'packages/os-api'
		},
		{
			name: 'persistence',
			root: 'packages/persistence'
		}
	],
	{
		format: ["esm", "cjs"],
		exports: true,
	}
)
