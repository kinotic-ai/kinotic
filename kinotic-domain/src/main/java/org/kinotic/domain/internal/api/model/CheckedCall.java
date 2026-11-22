package org.kinotic.domain.internal.api.model;

import org.kinotic.domain.api.model.security.participant.ScopedParticipant;

import java.util.function.Function;

/**
 * A call being authorized: the function called, its caller, the store answering for the caller, and the request's
 * arguments.
 *
 * @param spec        what the authorizer keeps of the function's contract
 * @param participant the caller
 * @param store       the store answering for the caller, named as its record is
 * @param arguments   reads from the request the value a parameter reference names; null for a value the request
 *                    does not carry
 */
public record CheckedCall(FunctionSpec spec,
                          ScopedParticipant participant,
                          String store,
                          Function<ParameterReference, String> arguments) {
}
