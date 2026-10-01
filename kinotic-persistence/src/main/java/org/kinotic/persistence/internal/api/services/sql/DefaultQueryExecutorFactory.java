package org.kinotic.persistence.internal.api.services.sql;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.DomainPersistenceProperties;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.domain.api.model.persistence.NamedQueriesDefinition;
import org.kinotic.domain.api.model.persistence.idl.decorators.QueryDecorator;
import org.kinotic.domain.api.services.EntityStatementResolver;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.idl.api.schema.FunctionDefinition;
import org.kinotic.persistence.api.model.NamedQueryOperation;
import org.kinotic.persistence.api.services.security.AuthorizationService;
import org.kinotic.persistence.api.services.security.AuthorizationServiceFactory;
import org.kinotic.persistence.internal.api.hooks.ReadPostProcessor;
import org.kinotic.persistence.internal.api.hooks.ReadPreProcessor;
import org.kinotic.persistence.internal.api.services.sql.elasticsearch.ElasticVertxClient;
import org.kinotic.persistence.internal.api.services.sql.executors.AggregateQueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.ParameterProcessorExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.PreAuthorizationExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.QueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.SelectQueryExecutor;
import org.kinotic.persistence.internal.api.services.sql.executors.StatementQueryExecutor;
import org.kinotic.persistence.internal.utils.QueryUtils;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.DeleteStatement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.domain.statements.SelectStatement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.executor.StatementExecutor;
import org.kinotic.sql.parsers.MigrationParser;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Navíd Mitchell 🤪 on 4/28/24.
 */
@Component
@RequiredArgsConstructor
public class DefaultQueryExecutorFactory implements QueryExecutorFactory {

    // a single-quoted string is matched whole, so a colon inside one is not a parameter; nor is a `::` cast
    private static final Pattern PARAMETER = Pattern.compile("'(?:[^']|'')*'|(?<!:):([A-Za-z_][A-Za-z0-9_]*)");

    private final ElasticVertxClient elasticVertxClient;
    private final DomainPersistenceProperties domainPersistenceProperties;
    private final AuthorizationServiceFactory authorizationServiceFactory;
    private final MigrationParser migrationParser;
    private final EntityStatementResolver entityStatementResolver;
    private final List<StatementExecutor<?, ?>> statementExecutors;
    private final CrudServiceTemplate crudServiceTemplate;
    private final ReadPreProcessor readPreProcessor;
    private final ReadPostProcessor readPostProcessor;
    private final JsonMapper jsonMapper;

    public QueryExecutor createQueryExecutor(EntityDescriptor entityDescriptor,
                                             String queryName,
                                             NamedQueriesDefinition namedQueriesDefinition){
        FunctionDefinition namedQuery = null;
        for(FunctionDefinition queries : namedQueriesDefinition.getNamedQueries()){
            if(queries.getName().equals(queryName)){
                namedQuery = queries;
                break;
            }
        }
        if(namedQuery == null){
            throw new IllegalArgumentException("No query found with name " + queryName);
        }

        // Sanity check, but should never happen if using the CLI
        QueryDecorator queryDecorator = namedQuery.findDecorator(QueryDecorator.class);
        if(queryDecorator == null
                || queryDecorator.getStatements() == null){
            throw new IllegalArgumentException("No Query defined");
        }

        QueryExecutor queryExecutor = createQueryExecutorForStatement(entityDescriptor, queryName, queryDecorator.getStatements());
        AuthorizationService<NamedQueryOperation> authorizationService =
                authorizationServiceFactory.createNamedQueryAuthorizationService(namedQuery)
                                           .toCompletionStage().toCompletableFuture().join();
        return new ParameterProcessorExecutor(entityDescriptor,
                                              namedQuery,
                                              new PreAuthorizationExecutor(entityDescriptor,
                                                                           authorizationService,
                                                                           queryExecutor));
    }

    private QueryExecutor createQueryExecutorForStatement(EntityDescriptor entityDescriptor,
                                                          String queryName,
                                                          String statements) {
        QueryExecutor ret;
        if(QueryUtils.isAggregate(statements)){
            // an aggregate runs on Elasticsearch SQL as written, apart from the entity it names and its parameters
            ret = new AggregateQueryExecutor(entityDescriptor,
                                             elasticVertxClient,
                                             positional(addressAggregate(statements, entityDescriptor, queryName)),
                                             parameterNames(statements),
                                             domainPersistenceProperties);
        }else{
            Statement statement = entityStatementResolver.resolve(parse(statements, queryName), entityDescriptor).getFirst();
            ret = switch (statement) {
                case SelectStatement select -> new SelectQueryExecutor(entityDescriptor, queryName, select, crudServiceTemplate,
                                                                       readPreProcessor, readPostProcessor);
                case InsertStatement _, UpdateStatement _, DeleteStatement _ -> write(entityDescriptor, statement);
                default -> throw new IllegalArgumentException("Named query " + queryName
                                                                      + ": a named query is a SELECT, INSERT, UPDATE or DELETE, so "
                                                                      + statement.getClass().getSimpleName().replace("Statement", "")
                                                                      + " is not allowed");
            };
        }
        return ret;
    }

    private List<Statement> parse(String statements, String queryName) {
        // a named query is one statement, usually written without the terminator the grammar requires
        String sql = statements.strip();
        List<Statement> ret = migrationParser.parse(sql.endsWith(";") ? sql : sql + ";", queryName).statements();
        Validate.isTrue(ret.size() == 1, "Named query %s must be one statement", queryName);
        return ret;
    }

    @SuppressWarnings("unchecked")
    private QueryExecutor write(EntityDescriptor entityDescriptor, Statement statement) {
        StatementExecutor<Statement, ?> executor = (StatementExecutor<Statement, ?>) statementExecutors
                .stream()
                .filter(candidate -> candidate.supports(statement))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No executor found for statement: " + statement.getClass().getSimpleName()));
        return new StatementQueryExecutor(entityDescriptor, statement, entityStatementResolver, executor, jsonMapper);
    }

    /**
     * Replaces the entity an aggregate names in FROM with the entity's index. Every FROM must name the entity the named
     * query belongs to, bare or double-quoted.
     */
    private static String addressAggregate(String statement, EntityDescriptor entityDescriptor, String queryName) {
        // The statement is scanned by Elasticsearch SQL's lexical rules, so a FROM inside a quoted string or identifier
        // is skipped and every FROM Elasticsearch reads is checked. A comment or a backslash could make this scan and
        // Elasticsearch disagree on where a quoted span ends, which would hide a FROM, so both are refused.
        Validate.isTrue(statement.indexOf('\\') < 0, "Named query %s cannot contain a backslash", queryName);
        StringBuilder ret = new StringBuilder(statement.length());
        boolean namesEntity = false;
        int i = 0;
        while (i < statement.length()) {
            char c = statement.charAt(i);
            int next;
            if (c == '\'' || c == '"' || c == '`') {
                next = quotedEnd(statement, i, queryName);
                ret.append(statement, i, next);
            } else if (statement.startsWith("--", i) || statement.startsWith("/*", i)) {
                throw new IllegalArgumentException("Named query " + queryName + " cannot contain a comment");
            } else if (isFromKeyword(statement, i)) {
                next = entityEnd(statement, skipWhitespace(statement, i + 4), entityDescriptor);
                ret.append("FROM \"").append(entityDescriptor.itemIndex()).append('"');
                namesEntity = true;
            } else {
                ret.append(c);
                next = i + 1;
            }
            i = next;
        }
        Validate.isTrue(namesEntity, "Named query %s names no entity in FROM", queryName);
        return ret.toString();
    }

    /**
     * The index just past the quoted span opening at {@code start}, where a doubled quote character stands for itself.
     */
    private static int quotedEnd(String statement, int start, String queryName) {
        char quote = statement.charAt(start);
        int ret = -1;
        int i = start + 1;
        while (ret < 0 && i < statement.length()) {
            if (statement.charAt(i) != quote) {
                i++;
            } else if (i + 1 < statement.length() && statement.charAt(i + 1) == quote) {
                i += 2;
            } else {
                ret = i + 1;
            }
        }
        Validate.isTrue(ret >= 0, "Named query %s has an unterminated %s", queryName, quote);
        return ret;
    }

    /**
     * Whether the FROM keyword starts at {@code i}, rather than appearing inside a longer identifier.
     */
    private static boolean isFromKeyword(String statement, int i) {
        // only a letter or underscore before FROM continues an identifier, so every FROM Elasticsearch could read as a
        // keyword is checked
        int after = i + 4;
        return statement.regionMatches(true, i, "FROM", 0, 4)
                && (i == 0 || !(isAsciiLetter(statement.charAt(i - 1)) || statement.charAt(i - 1) == '_'))
                && (after == statement.length() || !isIdentifierPart(statement.charAt(after)));
    }

    /**
     * The index just past the entity name starting at {@code start}, which must be the entity's name, bare or
     * double-quoted, followed by whitespace, a semicolon, a closing parenthesis or the end of the statement.
     */
    private static int entityEnd(String statement, int start, EntityDescriptor entityDescriptor) {
        String name;
        int ret;
        if (start < statement.length() && statement.charAt(start) == '"') {
            int close = statement.indexOf('"', start + 1);
            name = close < 0 ? "" : statement.substring(start + 1, close);
            ret = close < 0 ? statement.length() : close + 1;
        } else {
            ret = start;
            while (ret < statement.length() && isIdentifierPart(statement.charAt(ret))) {
                ret++;
            }
            name = statement.substring(start, ret);
        }
        boolean terminated = ret == statement.length()
                || isWhitespace(statement.charAt(ret))
                || statement.charAt(ret) == ';'
                || statement.charAt(ret) == ')';
        int wordEnd = start;
        while (wordEnd < statement.length() && !isWhitespace(statement.charAt(wordEnd))) {
            wordEnd++;
        }
        Validate.isTrue(terminated && entityDescriptor.name().equalsIgnoreCase(name), "A named query of %s acts on %s, not %s",
                        entityDescriptor.name(), entityDescriptor.name(), statement.substring(start, wordEnd));
        return ret;
    }

    private static int skipWhitespace(String statement, int start) {
        int ret = start;
        while (ret < statement.length() && isWhitespace(statement.charAt(ret))) {
            ret++;
        }
        return ret;
    }

    // the whitespace Elasticsearch SQL's lexer skips
    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    private static boolean isIdentifierPart(char c) {
        return isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * The names of an aggregate's {@code :name} parameters, in the order they appear.
     */
    private static List<String> parameterNames(String statement) {
        List<String> ret = new ArrayList<>();
        Matcher matcher = PARAMETER.matcher(statement);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                ret.add(matcher.group(1));
            }
        }
        return ret;
    }

    /**
     * The aggregate with each {@code :name} parameter replaced by the {@code ?} placeholder Elasticsearch SQL takes.
     */
    private static String positional(String statement) {
        return PARAMETER.matcher(statement)
                        .replaceAll(match -> match.group(1) != null ? "?" : Matcher.quoteReplacement(match.group()));
    }
}
