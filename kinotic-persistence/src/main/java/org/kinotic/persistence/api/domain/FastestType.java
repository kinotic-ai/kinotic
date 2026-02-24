package org.kinotic.persistence.api.domain;

import org.kinotic.domain.api.model.RawJson;

/**
 * Fastest type allows for the system to work with the fastest type possible while converting data to and from elastic.
 * This is used so we can still have an API that supports generics while remaining flexible for this case.
 * This will typically contain a {@link java.util.Map} or {@link RawJson} object.
 * Created By Navíd Mitchell 🤪on 2/3/25
 */
public record FastestType(Object data) {
}
