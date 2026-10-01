package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.sql.domain.Statement;

import java.util.List;
import java.util.Map;

/**
 * Resolves the entity names that kinotic-sql statements carry to the entities' indices, so a migration or a named
 * query addresses an application's entities and nothing else, and gives a row the identity the entity service
 * gives one it saves.
 * Created by Navíd Mitchell 🤝 Claude on 10/1/26.
 */
public interface EntityStatementResolver {

    /**
     * Resolves a migration's statements against the published entities of an application. Every name a statement
     * carries must be one of the application's published entities and is replaced by the entity's index. An INSERT
     * is also given the document id and routing the entity service gives a row it saves, from the row's id field
     * and, on a {@link MultiTenancyType#SHARED} entity, its tenant field, so the row is reachable through the
     * entity's repository afterwards.
     *
     * @param statements  the statements to resolve
     * @param applicationKey the application whose entities the statements name
     * @return the resolved statements, in the order given; fails when a name is not one of the application's
     *         published entities, a statement does not act on an entity, an INSERT row lacks its id or tenant, or
     *         a REINDEX carries a SCRIPT
     */
    Future<List<Statement>> resolve(List<Statement> statements, ApplicationKey applicationKey);

    /**
     * Resolves a named query's statements against the entity the query belongs to. Every name a statement carries
     * must be that entity's name, compared ignoring case, and is replaced by the entity's index. A write is given
     * its identity when it runs, see {@link #confine}, since its row and tenant are only known then.
     *
     * @param statements the statements to resolve
     * @param entity     the entity the named query belongs to
     * @return the resolved statements, in the order given
     * @throws IllegalArgumentException if a name is not the entity's, a statement does not act on an entity, or a
     *                                  REINDEX carries a SCRIPT
     */
    List<Statement> resolve(List<Statement> statements, EntityDescriptor entity);

    /**
     * Confines one execution of a resolved write to a tenant. An INSERT is given the document id and routing the
     * entity service gives a row it saves, from the row's id, bound against the parameters, and the tenant, which
     * is also written into the row's tenant field. An UPDATE or DELETE acts only on the tenant's documents.
     *
     * @param statement  a resolved INSERT, UPDATE or DELETE
     * @param entity     the entity the statement acts on
     * @param tenantId   the tenant the execution is confined to, or {@code null} for an entity that is not
     *                   {@link MultiTenancyType#SHARED}
     * @param parameters the values supplied for this execution, keyed by parameter name
     * @return the confined statement
     * @throws IllegalArgumentException if the statement is not a write, an INSERT row lacks its id or names
     *                                  another tenant, or an UPDATE assigns the tenant field
     */
    Statement confine(Statement statement, EntityDescriptor entity, String tenantId, Map<String, Object> parameters);
}
