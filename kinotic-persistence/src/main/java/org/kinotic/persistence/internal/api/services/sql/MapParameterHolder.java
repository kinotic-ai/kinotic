package org.kinotic.persistence.internal.api.services.sql;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.persistence.api.domain.ParameterHolder;

import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪 on 5/8/24.
 */
@RequiredArgsConstructor
@Getter
public class MapParameterHolder implements ParameterHolder {

    private final Map<String, Object> parameters;

    @Override
    public boolean isEmpty() {
        return parameters == null || parameters.isEmpty();
    }
}
