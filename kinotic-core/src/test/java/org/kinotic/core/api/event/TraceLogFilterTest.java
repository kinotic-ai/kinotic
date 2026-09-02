package org.kinotic.core.api.event;

import org.junit.jupiter.api.Test;
import org.kinotic.core.api.config.KinoticProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how {@code kinotic.traceLogExcludes} patterns resolve against the CRIs they are written for.
 */
public class TraceLogFilterTest {

    private static final String SERVICE = "srv://system-api~org.kinotic.system.api.services.VmNodeOrchestrationService";

    @Test
    public void wildcardPatternExcludesEveryMethodOfTheService() {
        TraceLogFilter filter = filterFor(SERVICE + "/*");

        assertTrue(filter.isExcluded(SERVICE + "/heartbeat"));
        assertTrue(filter.isExcluded(SERVICE + "/registerNode"));
    }

    @Test
    public void rawPatternExcludesOnlyTheMethodItNames() {
        TraceLogFilter filter = filterFor(SERVICE + "/heartbeat");

        assertTrue(filter.isExcluded(SERVICE + "/heartbeat"));
        assertFalse(filter.isExcluded(SERVICE + "/registerNode"));
    }

    @Test
    public void anotherServiceInTheSameZoneIsNotExcluded() {
        TraceLogFilter filter = filterFor(SERVICE + "/*");

        assertFalse(filter.isExcluded("srv://system-api~org.kinotic.system.api.services.KinoticClusterInfoService/findAll"));
    }

    @Test
    public void aScopedInvocationNeedsTheScopeWildcard() {
        String scopedCri = "srv://dev-node-1@system-api~org.kinotic.system.api.services.VmNodeOrchestrationService/heartbeat";

        assertFalse(filterFor(SERVICE + "/*").isExcluded(scopedCri));
        assertTrue(filterFor("srv://*@system-api~org.kinotic.system.api.services.VmNodeOrchestrationService/*").isExcluded(scopedCri));
    }

    @Test
    public void nothingIsExcludedWhenNoPatternsAreConfigured() {
        TraceLogFilter filter = new TraceLogFilter(new KinoticProperties());

        assertFalse(filter.isExcluded(SERVICE + "/heartbeat"));
    }

    @Test
    public void aReplyIsExcludedByTheMarkerItCarriesRatherThanByItsOwnCri() {
        TraceLogFilter filter = filterFor(SERVICE + "/*");
        String replyCri = "reply://0b5b4516:a8a95015@kinotic.js.EventBus/replyHandler";

        assertFalse(filter.isExcluded(replyCri));

        Metadata metadata = Metadata.create();
        metadata.put(EventConstants.TRACE_EXCLUDED_HEADER, "true");
        assertTrue(filter.isExcluded(Event.create(replyCri, metadata, new byte[0])));
    }

    @Test
    public void patternsCanBeReplacedWhileTheNodeRuns() {
        TraceLogFilter filter = filterFor(SERVICE + "/heartbeat");
        assertTrue(filter.isExcluded(SERVICE + "/heartbeat"));

        filter.setExcludes(List.of(SERVICE + "/registerNode"));
        assertFalse(filter.isExcluded(SERVICE + "/heartbeat"));
        assertTrue(filter.isExcluded(SERVICE + "/registerNode"));

        filter.setExcludes(List.of());
        assertFalse(filter.isExcluded(SERVICE + "/registerNode"));
    }

    @Test
    public void aBlankPatternIsRejected() {
        TraceLogFilter filter = filterFor(SERVICE + "/heartbeat");

        assertThrows(IllegalArgumentException.class, () -> filter.setExcludes(List.of("   ")));
        assertTrue(filter.isExcluded(SERVICE + "/heartbeat"));
    }

    private TraceLogFilter filterFor(String... patterns) {
        return new TraceLogFilter(new KinoticProperties().setTraceLogExcludes(List.of(patterns)));
    }

}
