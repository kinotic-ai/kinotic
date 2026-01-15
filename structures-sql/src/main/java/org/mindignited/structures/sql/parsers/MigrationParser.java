package org.mindignited.structures.sql.parsers;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.mindignited.structures.sql.domain.MigrationContent;
import org.mindignited.structures.sql.domain.Statement;
import org.mindignited.structures.sql.parser.StructuresSQLBaseVisitor;
import org.mindignited.structures.sql.parser.StructuresSQLLexer;
import org.mindignited.structures.sql.parser.StructuresSQLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Parses migration files (.sql) into a Migration object.
 * Uses StatementParser implementations to process individual statements.
 * The version is extracted from the filename in the format V<number>__<description>.sql
 */
@Component
@RequiredArgsConstructor
public class MigrationParser {
    private final List<StatementParser> statementParsers;
    private static final Logger log = LoggerFactory.getLogger(MigrationParser.class);

    public MigrationContent parse(Resource resource) throws IOException {
        byte[] bytes = resource.getInputStream().readAllBytes();
        return parse(bytes);
    }

    public MigrationContent parse(String sql) {
        return parse(CharStreams.fromString(sql));
    }

    public MigrationContent parse(byte[] bytes) throws IOException {
        return parse(CharStreams.fromStream(new java.io.ByteArrayInputStream(bytes)));
    }

    private MigrationContent parse(CharStream input) {
        StructuresSQLLexer lexer = new StructuresSQLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        StructuresSQLParser parser = new StructuresSQLParser(tokens);
        StructuresSQLParser.MigrationsContext tree = parser.migrations();
        return new MigrationVisitor(statementParsers).visit(tree);
    }

    private static class MigrationVisitor extends StructuresSQLBaseVisitor<MigrationContent> {
        private final List<StatementParser> statementParsers;

        MigrationVisitor(List<StatementParser> statementParsers) {
            this.statementParsers = statementParsers;
        }

        @Override
        public MigrationContent visitMigrations(StructuresSQLParser.MigrationsContext ctx) {
            List<StructuresSQLParser.StatementContext> statements = ctx.statement();
            log.debug("Found {} statements in migration file", statements.size());
            List<Statement> parsedStatements = new java.util.ArrayList<>();
            for (StructuresSQLParser.StatementContext stmtCtx : statements) {
                Statement stmt = parseStatement(stmtCtx);
                if (stmt != null) {
                    parsedStatements.add(stmt);
                }
            }
            return new MigrationContent(parsedStatements);
        }

        private Statement parseStatement(StructuresSQLParser.StatementContext stmtCtx) {
            String statementText = stmtCtx.getText();
            log.debug("Processing statement: {}", statementText);
            
            List<StatementParser> supportingParsers = statementParsers.stream()
                    .filter(p -> p.supports(stmtCtx))
                    .toList();
            
            if (supportingParsers.isEmpty()) {
                throw new IllegalStateException("No parser found for statement: " + statementText);
            }
            if (supportingParsers.size() > 1) {
                throw new IllegalStateException("Multiple parsers found for statement: " + statementText + 
                    ". Parsers: " + supportingParsers.stream()
                        .map(p -> p.getClass().getSimpleName())
                        .collect(Collectors.joining(", ")));
            }
            
            return supportingParsers.get(0).parse(stmtCtx);
        }
    }
} 