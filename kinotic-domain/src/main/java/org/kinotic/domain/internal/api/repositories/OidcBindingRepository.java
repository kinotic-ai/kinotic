package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.identity.OidcBinding;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OidcBindingRepository extends AbstractRepository<OidcBinding> {

    // the most provider identities one user signs in with
    private static final int MAX_BINDINGS = 50;
    // MAX_BINDINGS bindings each keeps a read under Elasticsearch's default result window of 10,000 hits
    private static final int IDENTITIES_PER_READ = 100;
    // the most organizations one provider identity signs in to
    private static final int MAX_ORGANIZATIONS = 100;

    public OidcBindingRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_oidc_binding", OidcBinding.class, crudServiceTemplate);
    }

    /** Deletes every binding of user {@code identityId}, visible to search when the future completes. */
    public Future<Void> deleteByIdentity(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return deleteByQuery(b -> b.query(termFilter("identityId", identityId)));
    }

    /** The bindings of user {@code identityId}, oldest first. */
    public Future<List<OidcBinding>> findByIdentity(String identityId) {
        Validate.notBlank(identityId, "identityId cannot be blank");
        return findAll(Pageable.ofSize(MAX_BINDINGS), b -> b.query(termFilter("identityId", identityId))
                                                             .sort(s -> s.field(f -> f.field("created"))))
                .map(Page::getContent);
    }

    /** The bindings of the provider identity {@code (oidcConfigId, subject)} to organizations' users, one in each organization at most. */
    public Future<List<OidcBinding>> findOrganizationBindings(String oidcConfigId, String subject) {
        Validate.notBlank(oidcConfigId, "oidcConfigId cannot be blank");
        Validate.notBlank(subject, "subject cannot be blank");
        return findAll(Pageable.ofSize(MAX_ORGANIZATIONS), b -> b.query(composeFilter(termFilter("oidcConfigId", oidcConfigId),
                                                                                       termFilter("subject", subject),
                                                                                       missingFilter("applicationId"))))
                .map(Page::getContent);
    }

    /** The bindings of the users {@code identityIds}. */
    public Future<List<OidcBinding>> findByIdentities(Collection<String> identityIds) {
        // a read of every binding of a chunk of users stays within the index's result window
        List<String> all = List.copyOf(identityIds);
        List<Future<List<OidcBinding>>> chunks = new ArrayList<>();
        for (int from = 0; from < all.size(); from += IDENTITIES_PER_READ) {
            List<FieldValue> ids = all.subList(from, Math.min(from + IDENTITIES_PER_READ, all.size())).stream().map(FieldValue::of).toList();
            chunks.add(findAll(Pageable.ofSize(MAX_BINDINGS * ids.size()),
                               b -> b.query(Query.of(q -> q.terms(t -> t.field("identityId").terms(v -> v.value(ids))))))
                               .map(Page::getContent));
        }
        return Future.all(chunks).map(cf -> cf.<List<OidcBinding>>list().stream().flatMap(List::stream).toList());
    }
}
