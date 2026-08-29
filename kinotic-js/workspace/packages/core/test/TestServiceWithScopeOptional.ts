import { Publish, Scope, ScopeOptional } from "../src"

/**
 * Scoped service with one ScopeOptional method, exercising the shared unscoped address: the
 * opted-in method answers with or without a scope, the other only when the scope names this
 * instance.
 */
@Publish("com.example")
export class TestServiceWithScopeOptional {

    @Scope
    get serviceScope(): string {
        return "opt-tenant"
    }

    @ScopeOptional
    anyInstanceValue(): string {
        return "any instance can answer this"
    }

    instanceValue(): string {
        return "only the named instance can answer this"
    }
}
