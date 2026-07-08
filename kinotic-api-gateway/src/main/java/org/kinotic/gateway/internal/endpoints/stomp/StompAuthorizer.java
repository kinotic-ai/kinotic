package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.CRI;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Authorizes STOMP sends and subscriptions for a connected participant.
 */
@Slf4j
public class StompAuthorizer {

    private static final int MAX_TEMPORARY_PATTERNS = 1000;

    private final PathContainer.Options parseOptions;
    private final List<PathPattern> sendPathPatterns;
    private final List<PathPattern> subscribePathPatterns;
    private final Function<String, PathPattern> pathPatternResolver;
    private final LinkedList<PathPattern> temporarySendPathPatterns = new LinkedList<>();

    public StompAuthorizer(PathContainer.Options parseOptions,
                           List<PathPattern> sendPathPatterns,
                           List<PathPattern> subscribePathPatterns,
                           Function<String, PathPattern> pathPatternResolver) {
        this.parseOptions = parseOptions;
        this.sendPathPatterns = sendPathPatterns;
        this.subscribePathPatterns = subscribePathPatterns;
        this.pathPatternResolver = pathPatternResolver;
    }

    public void addTemporarySendAllowed(String criPattern) {
        if (temporarySendPathPatterns.size() == MAX_TEMPORARY_PATTERNS) {
            temporarySendPathPatterns.removeFirst();
            log.warn("Reached Max Temporary patterns some messages may be dropped");
        }
        temporarySendPathPatterns.add(pathPatternResolver.apply(criPattern));
    }

    public boolean sendAllowed(CRI cri) {
        Validate.notNull(cri, "The CRI must not be null");
        int result = -1;

        if (!temporarySendPathPatterns.isEmpty()) {
            result = checkMatches(cri, temporarySendPathPatterns);
            if (result != -1) {
                temporarySendPathPatterns.remove(result);
            }
        }

        if (result == -1) {
            result = checkMatches(cri, sendPathPatterns);
        }
        return result != -1;
    }

    public boolean subscribeAllowed(CRI cri) {
        Validate.notNull(cri, "The CRI must not be null");
        return checkMatches(cri, subscribePathPatterns) != -1;
    }

    private int checkMatches(CRI cri, List<PathPattern> patterns) {
        PathContainer raw = PathContainer.parsePath(cri.raw(), parseOptions);
        // Zone authorization is independent of scope, and a MESSAGE_ROUTE '*' cannot span a
        // dotted scope (e.g. an FQDN node id), so also match a scope-stripped form of the CRI.
        // The scope-bearing reply patterns still match against the raw form above.
        PathContainer deScoped = cri.hasScope()
                ? PathContainer.parsePath(cri.raw().replaceFirst(Pattern.quote(cri.scope() + "@"), ""), parseOptions)
                : raw;
        int ret = -1;
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i).matches(raw) || patterns.get(i).matches(deScoped)) {
                ret = i;
                break;
            }
        }
        return ret;
    }

}
