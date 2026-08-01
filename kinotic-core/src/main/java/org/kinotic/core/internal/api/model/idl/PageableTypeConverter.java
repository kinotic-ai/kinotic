package org.kinotic.core.internal.api.model.idl;

import org.kinotic.core.api.crud.CursorPageable;
import org.kinotic.core.api.crud.Direction;
import org.kinotic.core.api.crud.NullHandling;
import org.kinotic.core.api.crud.OffsetPageable;
import org.kinotic.core.api.crud.Order;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.crud.Sort;
import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.SpecificTypeConverter;
import org.kinotic.idl.api.schema.ArrayC3Type;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.IntC3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.PropertyDefinition;
import org.kinotic.idl.api.schema.StringC3Type;
import org.kinotic.idl.api.schema.decorators.NotNullC3Decorator;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converts {@link Pageable} to the object callers send on the wire: a page size, exactly one of {@code pageNumber}
 * or {@code cursor}, and an optional {@link Sort} carrying its orders. Every property is described, so an LLM
 * calling a paged MCP tool can fill in a value the server accepts.
 */
@Component
public class PageableTypeConverter implements SpecificTypeConverter {

    private static final Class<?>[] supports = {Pageable.class, OffsetPageable.class, CursorPageable.class};

    @Override
    public Class<?>[] supports() {
        return supports;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType,
                          ConversionContext conversionContext) {
        // PageableDeserializer is the contract this mirrors; bean properties cannot express it since Pageable
        // exposes neither the page number nor the cursor, and Sort exposes its orders only through Iterable
        return new ObjectC3Type()
                .setNamespace(Pageable.class.getPackageName())
                .setName(Pageable.class.getSimpleName())
                .addProperty(required("pageSize", new IntC3Type(),
                                      "Maximum number of elements to return"))
                .addProperty(optional("pageNumber", new IntC3Type(),
                                      "Zero based page index, for offset paging. Send either this or cursor, never both"))
                .addProperty(optional("cursor", new StringC3Type(),
                                      "Cursor returned by the previous page, for cursor paging, or null for the first "
                                              + "page. Send either this or pageNumber, never both, and send a sort "
                                              + "alongside it"))
                .addProperty(optional("sort", sortType(conversionContext),
                                      "Order to sort the results by, or null to leave the order to the data store"));
    }

    private static ObjectC3Type sortType(ConversionContext conversionContext) {
        ObjectC3Type order = new ObjectC3Type()
                .setNamespace(Order.class.getPackageName())
                .setName(Order.class.getSimpleName())
                .addProperty(required("property", new StringC3Type(),
                                      "Name of the field to sort on"))
                .addProperty(required("direction", conversionContext.convert(ResolvableType.forClass(Direction.class)),
                                      "Direction to sort the field in"))
                .addProperty(optional("nullHandling", conversionContext.convert(ResolvableType.forClass(NullHandling.class)),
                                      "Where null values are placed, defaulting to NATIVE"));

        return new ObjectC3Type()
                .setNamespace(Sort.class.getPackageName())
                .setName(Sort.class.getSimpleName())
                .addProperty(required("orders", new ArrayC3Type(order),
                                      "Fields to sort on, applied in order"));
    }

    private static PropertyDefinition required(String name, C3Type type, String description) {
        PropertyDefinition ret = optional(name, type, description);
        ret.setDecorators(List.of(new NotNullC3Decorator()));
        return ret;
    }

    private static PropertyDefinition optional(String name, C3Type type, String description) {
        PropertyDefinition ret = new PropertyDefinition().setName(name).setType(type);
        ret.setMetadata(Map.of("description", description));
        return ret;
    }

}
