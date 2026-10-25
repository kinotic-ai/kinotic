package org.kinotic.domain.api.rest.support;

/**
 * What a callback handler may learn about the flow a callback belongs to before the callback is validated:
 * enough to send the browser to the right page when validation fails and the flow's result never comes, and to
 * find what the flow signs in to.
 *
 * @param purpose       what the flow is for
 * @param orgId         the organization the flow signs in to, or {@code null} when the flow stashed none
 * @param applicationId the application within it the flow signs in to, or {@code null} when the flow stashed none
 * @param origin        the page that started the flow, or {@code null} when none was named
 * @param inviteToken   the invitation the flow accepts, or {@code null} for any other purpose
 */
public record PendingFlow(OidcFlowPurpose purpose, String orgId, String applicationId, String origin, String inviteToken) {
}
