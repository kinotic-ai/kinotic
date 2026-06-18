package org.kinotic.sql.domain.statements;

/**
 * The conditions that trigger a rollover of a data stream's write index in the ILM hot phase.
 * Each condition is optional; a null value means that condition is not applied.
 * Created by Navíd Mitchell 🤝 Grok on 6/18/26.
 */
public record RolloverConditions(String maxAge, String maxSize, Integer maxDocs) {
}
