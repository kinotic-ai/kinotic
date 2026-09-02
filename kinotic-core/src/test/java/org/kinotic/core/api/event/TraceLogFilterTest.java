package org.kinotic.core.api.event;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.TraceLogProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how {@code kinotic.traceLog} patterns resolve against the CRIs they are written for.
 */
public class TraceLogFilterTest {

    private static final String SERVICE = "srv://system-api~org.kinotic.system.api.services.VmNodeOrchestrationService";
    private static final String OTHER_SERVICE = "srv://system-api~org.kinotic.system.api.services.KinoticClusterInfoService";

    @Test
    public void wildcardPatternExcludesEveryMethodOfTheService() {
        TraceLogFilter filter = excluding(SERVICE + "/*");

        assertTrue(filter.isExcluded(SERVICE + "/heartbeat"));
        assertTrue(filter.isExcluded(SERVICE + "/registerNode"));
    }

    @Test
    public void rawPatternExcludesOnlyTheMethodItNames() {
        TraceLogFilter filter = excluding(SERVICE + "/heartbeat");

        assertTrue(filter.isExcluded(SERVICE + "/heartbeat"));
        assertFalse(filter.isExcluded(SERVICE + "/registerNode"));
    }

    @Test
    public void anotherServiceInTheSameZoneIsNotExcluded() {
        TraceLogFilter filter = excluding(SERVICE + "/*");

        assertFalse(filter.isExcluded(OTHER_SERVICE + "/findAll"));
    }

    @Test
    public void aScopedInvocationNeedsTheScopeWildcard() {
        String scopedCri = "srv://dev-node-1@system-api~org.kinotic.system.api.services.VmNodeOrchestrationService/heartbeat";

        assertFalse(excluding(SERVICE + "/*").isExcluded(scopedCri));
        assertTrue(excluding("srv://*@system-api~org.kinotic.system.api.services.VmNodeOrchestrationService/*").isExcluded(scopedCri));
    }

    @Test
    public void nothingIsExcludedWhenNoPatternsAreConfigured() {
        TraceLogFilter filter = new TraceLogFilter(new KinoticProperties());

        assertFalse(filter.isExcluded(SERVICE + "/heartbeat"));
    }

    @Test
    public void anIncludeWinsOverAnExcludeThatAlsoMatches() {
        TraceLogFilter filter = filterFor(List.of(SERVICE + "/heartbeat"), List.of(SERVICE + "/*"));

        assertFalse(filter.isExcluded(SERVICE + "/heartbeat"));
        assertTrue(filter.isExcluded(SERVICE + "/registerNode"));
    }

    @Test
    public void excludingEverythingLeavesOnlyTheIncludes() {
        TraceLogFilter filter = filterFor(List.of(SERVICE + "/**"), List.of("**"));

        assertFalse(filter.isExcluded(SERVICE + "/heartbeat"));
        assertTrue(filter.isExcluded(OTHER_SERVICE + "/findAll"));
        assertTrue(filter.isExcluded("reply://0b5b4516:a8a95015@kinotic.js.EventBus/replyHandler"));
    }

    @Test
    public void patternsCanBeReplacedWhileTheNodeRuns() {
        TraceLogFilter filter = excluding(SERVICE + "/heartbeat");
        assertTrue(filter.isExcluded(SERVICE + "/heartbeat"));

        filter.setPatterns(new TraceLogProperties().setExcludes(List.of(SERVICE + "/registerNode")));
        assertFalse(filter.isExcluded(SERVICE + "/heartbeat"));
        assertTrue(filter.isExcluded(SERVICE + "/registerNode"));

        filter.setPatterns(new TraceLogProperties());
        assertFalse(filter.isExcluded(SERVICE + "/registerNode"));
    }

    @Test
    public void replacingPatternsDoesNotExposeTheFiltersOwnState() {
        TraceLogFilter filter = excluding(SERVICE + "/heartbeat");

        TraceLogProperties read = filter.getPatterns();
        read.setExcludes(List.of("**"));

        assertFalse(filter.isExcluded(OTHER_SERVICE + "/findAll"));
    }

    @Test
    public void aBlankPatternIsRejected() {
        TraceLogFilter filter = excluding(SERVICE + "/heartbeat");

        assertThrows(IllegalArgumentException.class,
                     () -> filter.setPatterns(new TraceLogProperties().setExcludes(List.of("   "))));
        assertTrue(filter.isExcluded(SERVICE + "/heartbeat"));
    }

    @Test
    public void aReplyFollowsTheVerdictReachedForItsRequest() {
        TraceLogFilter filter = filterFor(List.of(SERVICE + "/**"), List.of("**"));
        String replyCri = "reply://0b5b4516:a8a95015@kinotic.js.EventBus/replyHandler";

        assertFalse(filter.isExcluded(replyTo(replyCri, false)));
        assertTrue(filter.isExcluded(replyTo(replyCri, true)));
    }

    private Event<byte[]> replyTo(String replyCri, boolean excluded) {
        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.TRACE_EXCLUDED_HEADER, Boolean.toString(excluded));
        return Event.create(replyCri, metadata, new byte[0]);
    }

    private TraceLogFilter excluding(String... excludes) {
        return filterFor(List.of(), List.of(excludes));
    }

    private TraceLogFilter filterFor(List<String> includes, List<String> excludes) {
        TraceLogProperties traceLog = new TraceLogProperties().setIncludes(includes).setExcludes(excludes);
        return new TraceLogFilter(new KinoticProperties().setTraceLog(traceLog));
    }

}
