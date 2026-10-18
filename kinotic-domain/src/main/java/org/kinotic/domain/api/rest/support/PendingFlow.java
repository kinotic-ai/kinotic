package org.kinotic.domain.api.rest.support;

/**
 * What a callback handler may learn about the flow a callback belongs to before the callback is validated:
 * enough to send the browser to the right page when validation fails and the flow's result never comes.
 *
 * @param purpose     what the flow is for
 * @param origin      the page that started the flow, or {@code null} when none was named
 * @param inviteToken the invitation the flow accepts, or {@code null} for any other purpose
 */
public record PendingFlow(OidcFlowPurpose purpose, String origin, String inviteToken) {
}
