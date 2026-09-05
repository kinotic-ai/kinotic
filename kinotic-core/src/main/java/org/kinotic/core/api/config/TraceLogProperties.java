package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * The CRI patterns that decide what trace logging prints.
 *
 * Each pattern is matched against the fully qualified CRI with Ant wildcards, where {@code *}
 * matches within one segment and {@code **} across segments, so
 * {@code srv://system-api~com.acme.HeartbeatService/*} covers every method of that service and
 * {@code srv://system-api~com.acme.HeartbeatService/ping} covers only that one.
 * An include wins over an exclude, so {@code excludes: ["**"]} plus the handful of includes worth
 * watching narrows trace logging to those services alone.
 * Patterns are consulted only while trace logging is enabled.
 */
@Getter
@Setter
@Accessors(chain = true)
public class TraceLogProperties {

    /**
     * CRIs kept in trace logging whatever the excludes say. Empty leaves the excludes to decide.
     */
    private List<String> includes = new ArrayList<>();

    /**
     * CRIs left out of trace logging, request and reply both, unless an include covers them.
     */
    private List<String> excludes = new ArrayList<>();

}
