package org.kinotic.management.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.management.api.model.vulnerability.OsvImportProgress;
import org.springframework.stereotype.Component;

/**
 * Stores how far each ecosystem's advisories have been read from OSV, one {@link OsvImportProgress}
 * per ecosystem keyed by its name.
 */
@Component
public class OsvImportProgressRepository extends AbstractRepository<OsvImportProgress> {

    public OsvImportProgressRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_osv_import_progress", OsvImportProgress.class, crudServiceTemplate);
    }

}
