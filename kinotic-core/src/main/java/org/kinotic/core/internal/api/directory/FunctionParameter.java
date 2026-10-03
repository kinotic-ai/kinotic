package org.kinotic.core.internal.api.directory;

/**
 * A parameter of a published function as an authorization template may reference it.
 *
 * @param name      the parameter's name, as the schema and named-argument binding read it
 * @param type      the parameter's declared type on the implementing method
 * @param bodyIndex the parameter's position among the parameters a request body carries, or -1 for one the
 *                  platform supplies rather than the request
 */
record FunctionParameter(String name, Class<?> type, int bodyIndex) {
}
