package org.kinotic.gateway.internal.endpoints.stomp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.EventConstants;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

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
        // A srv/stream scope is a node-targeting id (possibly a dotted FQDN a MESSAGE_ROUTE '*'
        // cannot span), so it is stripped and the message is authorized on the zone it routes to.
        // Matching the raw form for these would let a crafted scope prefix the string with an
        // allowed zone and target another zone after the '@'. Reply CRIs are the exception: their
        // scope carries the replyToId the reply pattern authorizes against, so they match raw.
        boolean deScope = cri.hasScope()
                && !EventConstants.REPLY_DESTINATION_SCHEME.equals(cri.scheme());
        PathContainer target = PathContainer.parsePath(deScope ? deScope(cri) : cri.raw(), parseOptions);
        int ret = -1;
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i).matches(target)) {
                ret = i;
                break;
            }
        }
        return ret;
    }

    // Removes the leading "scope@" from a scoped CRI without a per-call regex compile
    private static String deScope(CRI cri) {
        String raw = cri.raw();
        String prefix = cri.scheme() + "://" + cri.scope() + "@";
        return raw.startsWith(prefix) ? cri.scheme() + "://" + raw.substring(prefix.length()) : raw;
    }

}
