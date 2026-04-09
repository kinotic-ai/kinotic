package org.kinotic.os.api.services.iam;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.api.model.iam.OidcConfiguration;

@Publish
public interface OidcConfigurationService extends IdentifiableCrudService<OidcConfiguration, String> {
}
