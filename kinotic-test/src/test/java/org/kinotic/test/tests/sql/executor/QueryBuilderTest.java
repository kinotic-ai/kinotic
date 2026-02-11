package org.kinotic.test.tests.sql.executor;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.persistence.sql.domain.WhereClause;
import org.kinotic.persistence.sql.executor.QueryBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryBuilderTest {

    private Map<String, Object> parameters;

    @BeforeEach
    void setUp() {
        parameters = new HashMap<>();
    }

    @Test
    void whenBuildingSimpleTermQuery_thenCorrectQueryBuilt() {
        // Given
        WhereClause.Condition condition = new WhereClause.Condition("field", "==", "'value'");

        // When
        Query query = QueryBuilder.buildQuery(condition, parameters);

        // Then
        assertTrue(query.isTerm());
        TermQuery termQuery = query.term();
        assertEquals("field", termQuery.field());
        assertEquals("value", termQuery.value().stringValue());
    }

    @SuppressWarnings("null")
    @Test
    void whenBuildingRangeQuery_thenCorrectQueryBuilt() {
        // Given
        WhereClause.Condition condition = new WhereClause.Condition("field", ">", "10");

        // When
        Query query = QueryBuilder.buildQuery(condition, parameters);

        // Then
        assertTrue(query.isRange());
        RangeQuery rangeQuery = query.range();
        assertEquals("field", rangeQuery.number().field());
        assertNotNull(rangeQuery.number().gt());
        assertEquals(10.0, rangeQuery.number().gt().doubleValue());
    }

    @Test
    void whenBuildingAndClause_thenCorrectQueryBuilt() {
        // Given
        WhereClause.Condition condition1 = new WhereClause.Condition("field1", "==", "'value1'");
        WhereClause.Condition condition2 = new WhereClause.Condition("field2", "==", "'value2'");
        WhereClause.AndClause andClause = new WhereClause.AndClause(condition1, condition2);

        // When
        Query query = QueryBuilder.buildQuery(andClause, parameters);

        // Then
        assertTrue(query.isBool());
        BoolQuery boolQuery = query.bool();
        assertEquals(2, boolQuery.filter().size());
        
        TermQuery term1 = boolQuery.filter().get(0).term();
        assertEquals("field1", term1.field());
        assertEquals("value1", term1.value().stringValue());
        
        TermQuery term2 = boolQuery.filter().get(1).term();
        assertEquals("field2", term2.field());
        assertEquals("value2", term2.value().stringValue());
    }

    @Test
    void whenBuildingOrClause_thenCorrectQueryBuilt() {
        // Given
        WhereClause.Condition condition1 = new WhereClause.Condition("field1", "==", "'value1'");
        WhereClause.Condition condition2 = new WhereClause.Condition("field2", "==", "'value2'");
        WhereClause.OrClause orClause = new WhereClause.OrClause(condition1, condition2);

        // When
        Query query = QueryBuilder.buildQuery(orClause, parameters);

        // Then
        assertTrue(query.isBool());
        BoolQuery boolQuery = query.bool();
        assertEquals(2, boolQuery.should().size());
        assertEquals("1", boolQuery.minimumShouldMatch());
        
        TermQuery term1 = boolQuery.should().get(0).term();
        assertEquals("field1", term1.field());
        assertEquals("value1", term1.value().stringValue());
        
        TermQuery term2 = boolQuery.should().get(1).term();
        assertEquals("field2", term2.field());
        assertEquals("value2", term2.value().stringValue());
    }

    @Test
    void whenBuildingNestedClause_thenCorrectQueryBuilt() {
        // Given
        WhereClause.Condition condition1 = new WhereClause.Condition("field1", "==", "'value1'");
        WhereClause.Condition condition2 = new WhereClause.Condition("field2", "==", "'value2'");
        WhereClause.AndClause innerClause = new WhereClause.AndClause(condition1, condition2);
        
        WhereClause.Condition condition3 = new WhereClause.Condition("field3", "==", "'value3'");
        WhereClause.AndClause outerClause = new WhereClause.AndClause(innerClause, condition3);

        // When
        Query query = QueryBuilder.buildQuery(outerClause, parameters);

        // Then
        assertTrue(query.isBool());
        BoolQuery boolQuery = query.bool();
        assertEquals(2, boolQuery.filter().size());
        
        // Verify inner AND clause
        BoolQuery innerBool = boolQuery.filter().get(0).bool();
        assertEquals(2, innerBool.filter().size());
        
        // Verify outer term
        TermQuery outerTerm = boolQuery.filter().get(1).term();
        assertEquals("field3", outerTerm.field());
        assertEquals("value3", outerTerm.value().stringValue());
    }

    @Test
    void whenUsingParameterizedQuery_thenParameterSubstituted() {
        // Given
        WhereClause.Condition condition = new WhereClause.Condition("field", "==", "?");
        parameters.put("field", "'value'");

        // When
        Query query = QueryBuilder.buildQuery(condition, parameters);

        // Then
        assertTrue(query.isTerm());
        TermQuery termQuery = query.term();
        assertEquals("field", termQuery.field());
        assertEquals("'value'", termQuery.value().stringValue());
    }

    @Test
    void whenUsingParameterizedQueryWithoutParameters_thenExceptionThrown() {
        // Given
        WhereClause.Condition condition = new WhereClause.Condition("field", "==", "?");

        // When/Then
        assertThrows(IllegalStateException.class, () -> 
            QueryBuilder.buildQuery(condition, null)
        );
    }

    @Test
    void whenUsingParameterizedQueryWithMissingParameter_thenExceptionThrown() {
        // Given
        WhereClause.Condition condition = new WhereClause.Condition("field", "==", "?");

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> 
            QueryBuilder.buildQuery(condition, parameters)
        );
    }
} 