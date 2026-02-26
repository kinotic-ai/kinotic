package org.kinotic.persistence.api.model.idl.decorators;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Set;

/**
 * Provides configuration for the Elasticsearch index.
 * This can be used to override the default index settings and mappings used by Structures.
 * Created By Navíd Mitchell 🤪on 3/18/25
 */
@Getter
@Setter
@Accessors(chain = true)
public class EsIndexConfigurationData {

    private Set<String> componentTemplates = Set.of();

}
