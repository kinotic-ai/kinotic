import { computed, type WritableComputedRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * The active tab of a tabbed page, kept in the {@code tab} query parameter so a tab can be
 * linked to and survives a reload. An unknown or missing value selects the first tab, and
 * selecting the first tab clears the parameter.
 */
export function useQueryTab<T extends string>(tabs: readonly T[]): WritableComputedRef<T> {
  const route = useRoute()
  const router = useRouter()
  const fallback = tabs[0]

  return computed<T>({
    get: () => {
      const requested = route.query.tab
      return typeof requested === 'string' && (tabs as readonly string[]).includes(requested) ? requested as T : fallback
    },
    set: (tab: T) => {
      const query = { ...route.query }
      if (tab === fallback) {
        delete query.tab
      } else {
        query.tab = tab
      }
      router.replace({ query }).catch(() => {})
    }
  })
}
