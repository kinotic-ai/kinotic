package org.kinotic.management.internal.api.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.OrganizationParticipant;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.config.GithubInternalAppProperties;
import org.kinotic.management.api.config.GithubProperties;
import org.kinotic.management.api.model.feedback.FeedbackSubmission;
import org.kinotic.management.api.model.github.CreatedIssue;
import org.kinotic.management.api.services.FeedbackService;
import org.kinotic.management.internal.api.services.github.client.GitHubApiClient;
import org.kinotic.management.internal.api.services.github.client.GitHubAppJwtFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ConditionalOnProperty("kinotic.managementApi.github.internalApp.appPrivateKey")
public class DefaultFeedbackService implements FeedbackService {

    private static final String FEEDBACK_REPO = "kinotic-ai/kinotic";
    private static final String FEEDBACK_LABEL = "feedback";
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_TEXT_LENGTH = 10_000;
    private static final int SENDS_PER_HOUR = 10;

    private final SecurityContext securityContext;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubAppJwtFactory internalAppJwtFactory;

    // a fixed one-hour window per organization, opened by its first send; each node counts its own sends
    private final Cache<String, AtomicInteger> sendsByOrganization = Caffeine.newBuilder()
                                                                             .expireAfterWrite(Duration.ofHours(1))
                                                                             .maximumSize(10_000)
                                                                             .build();

    public DefaultFeedbackService(SecurityContext securityContext,
                                  GitHubApiClient gitHubApiClient,
                                  GithubProperties githubProperties) {
        this.securityContext = securityContext;
        this.gitHubApiClient = gitHubApiClient;
        GithubInternalAppProperties internalApp = githubProperties.getInternalApp();
        this.internalAppJwtFactory = new GitHubAppJwtFactory(internalApp.getAppId(), internalApp.getAppPrivateKey());
    }

    @Override
    public Future<CreatedIssue> send(FeedbackSubmission submission) {
        Validate.notNull(submission, "submission is required");
        Validate.notNull(submission.getType(), "type is required");
        Validate.notBlank(submission.getTitle(), "title is required");
        Validate.isTrue(submission.getTitle().length() <= MAX_TITLE_LENGTH,
                        "title must be at most %d characters", MAX_TITLE_LENGTH);
        Validate.notBlank(submission.getDescription(), "description is required");
        Map<String, String> sections = issueSections(submission);
        for (String text : sections.values()) {
            Validate.isTrue(text.length() <= MAX_TEXT_LENGTH, "each field must be at most %d characters", MAX_TEXT_LENGTH);
        }

        OrganizationParticipant sender = securityContext.requireParticipant(OrganizationParticipant.class);
        int sent = sendsByOrganization.get(sender.getOrganizationId(), _ -> new AtomicInteger()).incrementAndGet();
        Validate.validState(sent <= SENDS_PER_HOUR,
                            "Your organization has sent %d pieces of feedback in the last hour; try again later", SENDS_PER_HOUR);

        String title = submission.getTitle().strip();
        String body = issueBody(sections, sender);
        String appJwt = internalAppJwtFactory.getAppJwt();
        return gitHubApiClient.findRepositoryInstallationId(appJwt, FEEDBACK_REPO)
                              .compose(installationId -> gitHubApiClient.createInstallationToken(appJwt,
                                                                                                 installationId,
                                                                                                 GitHubApiClient.WRITE_ISSUES))
                              .compose(token -> gitHubApiClient.createIssue(token.getToken(),
                                                                            FEEDBACK_REPO,
                                                                            title,
                                                                            body,
                                                                            List.of(submission.getType().getIssueLabel(), FEEDBACK_LABEL)))
                              // the issue is public, so the organization and sender are recorded only here
                              .onSuccess(issue -> log.info("Feedback from participant {} of organization {} filed as {}",
                                                           sender.getId(), sender.getOrganizationId(), issue.getUrl()));
    }

    // the issue's sections by heading, in the order the body shows them, each blank when not given
    private static Map<String, String> issueSections(FeedbackSubmission submission) {
        Map<String, String> ret = new LinkedHashMap<>();
        ret.put("Description", StringUtils.defaultString(submission.getDescription()));
        ret.put("Steps to reproduce", StringUtils.defaultString(submission.getStepsToReproduce()));
        ret.put("Expected behavior", StringUtils.defaultString(submission.getExpectedBehavior()));
        ret.put("Actual behavior", StringUtils.defaultString(submission.getActualBehavior()));
        ret.put("Where", StringUtils.defaultString(submission.getLocation()));
        return ret;
    }

    private static String issueBody(Map<String, String> sections, OrganizationParticipant sender) {
        StringBuilder body = new StringBuilder();
        sections.forEach((heading, text) -> {
            if (StringUtils.isNotBlank(text)) {
                body.append("### ").append(heading).append("\n\n").append(text.strip()).append("\n\n");
            }
        });
        body.append("---\n_Sent from Kinotic by ").append(senderDescription(sender)).append("._\n");
        return body.toString();
    }

    // names only an agent's client, never a person or machine, since the issue is public
    private static String senderDescription(OrganizationParticipant sender) {
        String type = sender.getMetadata().get(ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY);
        String ret;
        if (ParticipantConstants.PARTICIPANT_TYPE_DELEGATE.equals(type)) {
            ret = "an AI agent (" + DomainUtil.displayNameOf(sender) + ")";
        } else if (ParticipantConstants.PARTICIPANT_TYPE_MACHINE.equals(type)) {
            ret = "a machine identity";
        } else {
            ret = "a user";
        }
        return ret;
    }

}
