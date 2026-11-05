import { initClarity } from '~/utils/clarity'
import { applyConsent, NO_CONSENT, readConsent } from '~/utils/consent'
import { initGoogleAnalytics } from '~/utils/googleAnalytics'

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()
  const projectId = (config.public.clarityProjectId as string | undefined) || ''

  // Both providers start in denied/cookieless mode (Clarity through Consent Mode
  // in its project settings) and only upgrade once applyConsent grants storage.
  if (projectId) initClarity(projectId)
  initGoogleAnalytics()

  applyConsent(readConsent() ?? NO_CONSENT)
})
