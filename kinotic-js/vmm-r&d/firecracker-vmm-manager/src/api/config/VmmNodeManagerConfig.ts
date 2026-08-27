import { z, type ZodIssue } from 'zod'

/**
 * Global config for the vmm-node-manager. Loaded once at process start from environment via Bun's native .env.
 * Extensible for other vmm-node-manager settings later.
 *
 * Env vars: VMM_CONTINUUM_HOST, VMM_KERNEL_IMAGE_PATH, VMM_ROOTFS_PATH, VMM_MASTER_OVERLAY_PATH, VMM_OVERLAY_OUTPUT_DIR
 * Bun loads .env, .env.local, .env.{NODE_ENV} automatically. For custom: bun --env-file=.env.custom run ...
 */

const VmmNodeManagerConfigSchema = z.object({
	continuumHost: z
		.string({ required_error: 'VMM_CONTINUUM_HOST must be set' })
		.min(1, 'VMM_CONTINUUM_HOST must be non-empty'),
	kernelImagePath: z
		.string({ required_error: 'VMM_KERNEL_IMAGE_PATH must be set' })
		.min(1, 'VMM_KERNEL_IMAGE_PATH must be non-empty'),
	rootfsPath: z
		.string({ required_error: 'VMM_ROOTFS_PATH must be set' })
		.min(1, 'VMM_ROOTFS_PATH must be non-empty'),
	masterOverlayPath: z
		.string({ required_error: 'VMM_MASTER_OVERLAY_PATH must be set' })
		.min(1, 'VMM_MASTER_OVERLAY_PATH must be non-empty'),
	overlayOutputDir: z
		.string({ required_error: 'VMM_OVERLAY_OUTPUT_DIR must be set' })
		.min(1, 'VMM_OVERLAY_OUTPUT_DIR must be non-empty'),
})

export type VmmNodeManagerConfig = z.infer<typeof VmmNodeManagerConfigSchema>

function loadVmmNodeManagerConfig(): VmmNodeManagerConfig {
	const env = typeof Bun !== 'undefined' ? Bun.env : process.env
	const raw = {
		continuumHost: env.VMM_CONTINUUM_HOST,
		kernelImagePath: env.VMM_KERNEL_IMAGE_PATH,
		rootfsPath: env.VMM_ROOTFS_PATH,
		masterOverlayPath: env.VMM_MASTER_OVERLAY_PATH,
		overlayOutputDir: env.VMM_OVERLAY_OUTPUT_DIR,
	}

	const result = VmmNodeManagerConfigSchema.safeParse(raw)
	if (!result.success) {
		const msg = result.error.issues
			.map((i: ZodIssue) => `  - ${i.path.join('.')}: ${i.message}`)
			.join('\n')
		throw new Error(
			`VmmNodeManagerConfig: validation failed:\n${msg}\nSet them in .env or the environment. See .env.example.`,
		)
	}
	return result.data
}

/** Loaded once when this module is first imported. Throws if required env vars are missing. */
export const vmmNodeManagerConfig: VmmNodeManagerConfig = loadVmmNodeManagerConfig()
