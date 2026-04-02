export default defineAppConfig({
  seo: {
    // Docus augments AppConfig but not AppConfigInput, so titleTemplate is unknown to the input type.
    // At runtime useSeoMeta accepts a function here, avoiding "Kinotic - Kinotic" on the home page.
    // @ts-expect-error – AppConfigInput.seo does not declare titleTemplate
    titleTemplate: (title: string) =>
      title === 'Kinotic' ? 'Kinotic' : `${title} - Kinotic`,
  },
})
