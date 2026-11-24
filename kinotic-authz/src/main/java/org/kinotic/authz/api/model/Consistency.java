package org.kinotic.authz.api.model;

/**
 * How current a check's answer must be. The engine caches the subproblems a check resolves and invalidates
 * them as its controller sees writes, so a cached answer can predate a write by the controller's interval.
 * Created by Navíd Mitchell 🤪on 10/4/26
 */
public enum Consistency {
    /** Answers from the engine's caches: the cost of every check a request makes. */
    MINIMIZE_LATENCY,
    /** Answers from the stored relationships alone, for a question a stale allow would make a security event. */
    HIGHER_CONSISTENCY
}
