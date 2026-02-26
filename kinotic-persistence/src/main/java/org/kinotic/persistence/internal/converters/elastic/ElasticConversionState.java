package org.kinotic.persistence.internal.converters.elastic;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.idl.decorators.EntityDecorator;
import org.kinotic.persistence.api.model.idl.decorators.EsIndexConfigurationDecorator;
import org.kinotic.persistence.internal.converters.common.BaseConversionState;
import org.kinotic.persistence.api.model.DecoratedProperty;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Navíd Mitchell 🤪 on 5/9/23.
 */
@Getter
@Setter
@Accessors(chain = true)
public class ElasticConversionState extends BaseConversionState {

    private final List<DecoratedProperty> decoratedProperties = new LinkedList<>();

    /**
     * The {@link EntityDecorator} that was found while converting the structure
     */
    private EntityDecorator entityDecorator;

    private EsIndexConfigurationDecorator esIndexConfigurationDecorator;

    private String idFieldName;

    private String versionFieldName;

    private String timeReferenceFieldName;

    private String tenantIdFieldName;

    /**
     * If true the index will be created, if false the index will not be created for a given field.
     */
    private boolean shouldIndex = true;

    public ElasticConversionState(PersistenceProperties persistenceProperties) {
        super(persistenceProperties);
    }

}
