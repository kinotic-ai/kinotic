/**
 * Tells the latest of the requests a caller starts from the ones started before it, whose answers may arrive
 * after its own and must not be shown.
 *
 * @returns a function to call as each request starts; it returns a check that is true until a later request starts
 */
export function latestOnly(): () => () => boolean {
  let latest = 0
  return () => {
    const started = ++latest
    return () => started === latest
  }
}
