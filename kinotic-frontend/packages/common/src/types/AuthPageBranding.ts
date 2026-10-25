/**
 * The images an app's auth pages show, so every sign-in, sign-up and consent page carries
 * that app's brand color.
 */
export interface AuthPageBranding {
  /** The background art shown beside the page content. */
  art: string
  /** The brand mark shown above the page content in dark mode. */
  darkBrandMark: string
}
