import type {UndeclaredVariable} from './UndeclaredVariable'

/**
 * The outcome of {@link SpawnEngine#lint}: the variables a spawn's templates
 * reference that are not declared in propertySchema or globals. A spawn is
 * lint-clean when {@code undeclared} is empty.
 */
export interface SpawnLintResult {

  /** Referenced-but-undeclared variables, sorted by name. */
  undeclared: UndeclaredVariable[]
}
