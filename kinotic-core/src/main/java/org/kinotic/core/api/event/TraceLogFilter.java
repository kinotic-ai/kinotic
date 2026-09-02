package org.kinotic.core.api.event;

import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Answers whether traffic addressed to a given {@link CRI} is excluded from trace logging by
 * {@code kinotic.traceLogExcludes}.
 *
 * Call sites test {@code log.isTraceEnabled()} first, so the patterns cost nothing unless trace
 * logging is on.
 */
@Component
public class TraceLogFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final List<String> patterns;

    public TraceLogFilter(KinoticProperties kinoticProperties) {
        this.patterns = List.copyOf(kinoticProperties.getTraceLogExcludes());
    }

    /**
     * Whether the event belongs to an exchange excluded from trace logging.
     * A reply carries the exclusion of the request it answers, so this holds for both directions of
     * an excluded invocation.
     *
     * @param event the event about to be logged
     * @return true if the event must not be trace logged
     */
    public boolean isExcluded(Event<?> event) {
        return event.metadata().contains(EventConstants.TRACE_EXCLUDED_HEADER)
                || isExcluded(event.cri().raw());
    }

    /**
     * Whether the given {@link CRI} matches one of the configured exclusion patterns.
     *
     * @param rawCri the fully qualified {@link CRI} the traffic is addressed to
     * @return true if traffic addressed to the {@link CRI} must not be trace logged
     */
    public boolean isExcluded(String rawCri) {
        boolean ret = false;
        for (String pattern : patterns) {
            if (matcher.match(pattern, rawCri)) {
                ret = true;
                break;
            }
        }
        return ret;
    }

}
