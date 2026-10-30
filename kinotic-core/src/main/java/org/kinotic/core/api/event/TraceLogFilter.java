package org.kinotic.core.api.event;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.TraceLogProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers whether traffic addressed to a given {@link CRI} is excluded from trace logging.
 *
 * The patterns start as {@code kinotic.traceLog} and can be replaced while the node runs.
 * Call sites test {@code log.isTraceEnabled()} first, so they cost nothing unless trace logging is on.
 */
@Component
public class TraceLogFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private volatile TraceLogProperties patterns;
    // the qualified names of the services published redacted, whose traffic carries credentials
    private final Set<String> redactedServices = ConcurrentHashMap.newKeySet();

    public TraceLogFilter(KinoticProperties kinoticProperties) {
        setPatterns(kinoticProperties.getTraceLog());
    }

    /**
     * @return the patterns currently deciding what this node trace logs
     */
    public TraceLogProperties getPatterns() {
        return copyOf(patterns);
    }

    /**
     * Whether the event belongs to an exchange excluded from trace logging: it was marked excluded
     * when the request entered, or its own {@link CRI} matches.
     *
     * @param event the event about to be logged
     * @return true if the event must not be trace logged
     */
    public boolean isExcluded(Event<?> event) {
        return event.metadata().contains(EventConstants.TRACE_EXCLUDED_HEADER)
                || isExcluded(event.cri().raw());
    }

    /**
     * Whether the given {@link CRI} is excluded from trace logging: it names a service published
     * redacted, or it matches one of the exclude patterns and none of the include patterns. A reply
     * destination is decided instead by the marker its request carried, which {@link #isExcluded(Event)} reads.
     *
     * @param rawCri the fully qualified {@link CRI} the traffic is addressed to
     * @return true if traffic addressed to the {@link CRI} must not be trace logged
     */
    public boolean isExcluded(String rawCri) {
        boolean ret = false;
        // A reply destination belongs to a connected client rather than naming what was invoked, so
        // matching one would let an exclude of ** drop replies the includes meant to keep
        if (!rawCri.startsWith(EventConstants.REPLY_DESTINATION_SCHEME + ":")) {
            // One read, so includes and excludes are weighed as the single setting they were set as
            TraceLogProperties current = patterns;
            ret = isRedacted(rawCri) || (!matchesAny(current.getIncludes(), rawCri) && matchesAny(current.getExcludes(), rawCri));
        }
        return ret;
    }

    /**
     * Keeps every exchange with the service out of trace logging, whatever the patterns say, for the
     * life of the node: the service's arguments or results carry credentials.
     *
     * @param qualifiedName the service's qualified name, as {@code ServiceIdentifier.qualifiedName()} returns it
     */
    public void redact(String qualifiedName) {
        Validate.notBlank(qualifiedName, "qualifiedName must not be blank");
        redactedServices.add(qualifiedName);
    }

    private boolean isRedacted(String rawCri) {
        boolean ret = false;
        if (!redactedServices.isEmpty()) {
            try {
                ret = redactedServices.contains(CRI.create(rawCri).resourceName());
            } catch (RuntimeException e) {
                // a destination that is not a CRI names no service
            }
        }
        return ret;
    }

    /**
     * Replaces the patterns deciding what this node trace logs, effective on the next event logged.
     * The patterns live only in the running process, so a restart returns to the
     * {@code kinotic.traceLog} the node was configured with.
     *
     * @param patterns the include and exclude patterns to apply
     */
    public void setPatterns(TraceLogProperties patterns) {
        Validate.notNull(patterns, "patterns must not be null");
        // Swapped as one immutable value, so an isExcluded scan running concurrently weighs the
        // includes and excludes it started on rather than a half-applied change
        this.patterns = copyOf(patterns);
    }

    private boolean matchesAny(List<String> patterns, String rawCri) {
        boolean ret = false;
        for (String pattern : patterns) {
            if (matcher.match(pattern, rawCri)) {
                ret = true;
                break;
            }
        }
        return ret;
    }

    private static TraceLogProperties copyOf(TraceLogProperties source) {
        return new TraceLogProperties().setIncludes(validated(source.getIncludes()))
                                       .setExcludes(validated(source.getExcludes()));
    }

    private static List<String> validated(List<String> patterns) {
        Validate.notNull(patterns, "Trace log patterns must not be null");
        for (String pattern : patterns) {
            Validate.notBlank(pattern, "A trace log pattern must not be blank");
        }
        return List.copyOf(patterns);
    }

}
