package org.kinotic.core.api.event;

import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.config.KinoticProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * Answers whether traffic addressed to a given {@link CRI} is excluded from trace logging.
 *
 * The patterns start as {@code kinotic.traceLogExcludes} and can be replaced while the node runs.
 * Call sites test {@code log.isTraceEnabled()} first, so they cost nothing unless trace logging is on.
 */
@Component
public class TraceLogFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * -- GETTER --
     *  The CRI patterns currently excluded from trace logging on this node.
     */
    @Getter
    private volatile List<String> excludes;

    public TraceLogFilter(KinoticProperties kinoticProperties) {
        this.excludes = List.copyOf(kinoticProperties.getTraceLogExcludes());
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
        for (String pattern : excludes) {
            if (matcher.match(pattern, rawCri)) {
                ret = true;
                break;
            }
        }
        return ret;
    }

    /**
     * Replaces the CRI patterns excluded from trace logging, effective on the next event logged.
     * The patterns live only in the running process, so a restart returns to the
     * {@code kinotic.traceLogExcludes} the node was configured with.
     *
     * @param excludes the patterns to exclude, or empty to exclude nothing
     */
    public void setExcludes(List<String> excludes) {
        Validate.notNull(excludes, "excludes must not be null");
        for (String pattern : excludes) {
            Validate.notBlank(pattern, "A trace log exclude pattern must not be blank");
        }
        // Replaced wholesale with an immutable copy, so an isExcluded scan running concurrently
        // finishes against the list it started on rather than seeing a half-applied change
        this.excludes = List.copyOf(excludes);
    }

}
