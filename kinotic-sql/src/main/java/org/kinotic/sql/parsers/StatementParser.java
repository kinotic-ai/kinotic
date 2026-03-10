package org.kinotic.sql.parsers;

import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.parser.KinoticSQLParser;

/**
 * Interface for parsing SQL-like statements from the ANTLR-generated parse tree.
 * Implementations handle specific statement types (e.g., CREATE TABLE, UPDATE).
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public interface StatementParser {
    boolean supports(KinoticSQLParser.StatementContext ctx);
    Statement parse(KinoticSQLParser.StatementContext ctx);
}