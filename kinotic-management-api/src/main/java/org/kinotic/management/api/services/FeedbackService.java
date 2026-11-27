package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.McpTool;
import org.kinotic.management.api.model.github.CreatedIssue;
import org.kinotic.management.api.model.feedback.FeedbackSubmission;

/**
 * Sends bug reports and feature requests about Kinotic itself to the Kinotic team, for AI agents working
 * through Kinotic's MCP tools.
 */
@Publish
@McpTool
public interface FeedbackService {

    /**
     * Sends a bug report or feature request about Kinotic to the Kinotic team, who track it as a
     * public GitHub issue in the Kinotic repository. Send one when Kinotic gets in the way of the work:
     * a tool or page that fails, returns something wrong or is confusing, or a capability the work
     * needs that Kinotic lacks. Describe it so the team can act without asking back: for a bug, the
     * steps, the expected result and the actual result or error message; for a request, what is wanted
     * and why. The issue is public, so never include secrets, tokens, credentials, personal data or
     * the names of the organization's applications and projects. An organization may send ten per hour.
     *
     * @param submission the bug report or feature request
     * @return a future emitting the issue it was filed as
     */
    // filing a GitHub issue reaches outside Kinotic, and only the annotation can say so
    @McpTool(openWorldHint = true)
    Future<CreatedIssue> send(FeedbackSubmission submission);

}
