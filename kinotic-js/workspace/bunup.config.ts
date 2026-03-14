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
			name: 'domain',
			root: 'packages/domain'
		},
		{
			name: 'persistence',
			root: 'packages/persistence'
		},
		{
			name: 'kinotic',
			root: 'packages/kinotic'
		}
	],
	{
		format: ["esm", "cjs"],
		exports: true,
	}
)
