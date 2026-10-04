package org.kinotic.domain.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.internal.api.repositories.OrganizationRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class DefaultOrganizationService extends AbstractCrudService<Organization> implements OrganizationService {

    private final RelationshipService relationships;

    public DefaultOrganizationService(OrganizationRepository repository, RelationshipService relationships) {
        super(repository);
        this.relationships = relationships;
    }

    @Override
    protected Future<Void> beforeSave(Organization entity) {
        Validate.notNull(entity.getName(), "Organization name cannot be null");

        if (entity.getId() == null) {
            entity.setId(DomainUtil.slugifyId(entity.getName()));
            entity.setCreated(new Date());
        }
        // Validate only; re-minting an update's id would silently write a new document
        DomainUtil.validateOrganizationId(entity.getId());

        entity.setUpdated(new Date());
        return Future.succeededFuture();
    }

    /**
     * Creates a new organization, deriving its id from the slugified name. Fails with
     * {@link AlreadyExistsException} if an organization with that name (hence id) already
     * exists, rather than overwriting it.
     */
    @Override
    public Future<Organization> create(Organization entity) {
        Validate.notBlank(entity.getName(), "Organization name cannot be null");
        // Force the id to derive from the name; beforeSave mints it from the slug.
        entity.setId(null);
        return super.create(entity)
                    .recover(ex -> ex instanceof AlreadyExistsException
                            ? Future.failedFuture(new AlreadyExistsException(
                                    "An organization named '" + entity.getName() + "' already exists"))
                            : Future.failedFuture(ex))
                    .compose(this::contained);
    }

    @Override
    public Future<Organization> createSync(Organization entity) {
        Validate.notBlank(entity.getName(), "Organization name cannot be null");
        entity.setId(null);
        return super.createSync(entity).compose(this::contained);
    }

    // The organization's place in the graph, written once the record is, so a write that fails leaves an
    // organization nobody can reach rather than one nobody stores
    private Future<Organization> contained(Organization organization) {
        return relationships.platform()
                            .ensure(List.of(new RelationshipTuple(AuthzUtil.object(AuthzUtil.PLATFORM_TYPE, AuthzUtil.PLATFORM_OBJECT_ID),
                                                                  AuthzUtil.PLATFORM_TYPE,
                                                                  AuthzUtil.object(AuthzUtil.ORGANIZATION_TYPE, organization.getId()))))
                            .map(organization);
    }
}
