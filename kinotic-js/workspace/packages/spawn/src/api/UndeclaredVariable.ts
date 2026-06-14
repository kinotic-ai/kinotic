/**
 * A variable referenced in a spawn's templates that is declared neither in
 * propertySchema nor in globals, reported by {@link SpawnEngine#lint}.
 *
 * @see SpawnLintResult
 */
export interface UndeclaredVariable {

  /** The variable name as referenced in the templates. */
  name: string

  /**
   * The spawn paths where the variable appears — file contents for
   * {@code .liquid} files, and path templates for any file whose path contains
   * the variable.
   */
  files: string[]
}
