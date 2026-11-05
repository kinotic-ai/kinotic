import { applyConsent, persistConsent, readConsent, type ConsentChoice } from '~/utils/consent'

/**
 * The cookie banner's state, shared by the banner and the footer's Cookie settings link.
 *
 * Every rendered page carries the banner open, so a first visit paints it with the page;
 * {@link loadSavedChoice} closes it in the browser when the visitor has already chosen.
 */
export function useCookieConsent() {
  const visible = useState('cookie-consent-visible', () => true)
  const current = useState<ConsentChoice | null>('cookie-consent-current', () => null)

  /** Reads the visitor's saved choice and shows the banner only when there is none. Call once mounted. */
  const loadSavedChoice = () => {
    current.value = readConsent()
    visible.value = current.value === null
  }

  /** Shows the banner, opened to the category toggles when the visitor has already chosen. */
  const open = () => {
    visible.value = true
  }

  /** Saves and applies the visitor's choice, then closes the banner. */
  const save = (choice: ConsentChoice) => {
    current.value = choice
    persistConsent(choice)
    applyConsent(choice)
    visible.value = false
  }

  return { visible: readonly(visible), current: readonly(current), loadSavedChoice, open, save }
}
