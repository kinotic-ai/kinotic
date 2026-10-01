package org.kinotic.persistence.internal.api.hooks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.kinotic.core.api.crud.CursorPage;
import org.kinotic.core.api.crud.Page;
import org.kinotic.domain.api.config.DomainPersistenceProperties;
import org.kinotic.domain.api.model.RawJson;
import org.kinotic.domain.api.model.persistence.EntityDescriptor;
import org.kinotic.domain.api.model.persistence.idl.decorators.MultiTenancyType;
import org.kinotic.persistence.api.model.EntityContext;
import org.kinotic.persistence.api.model.FastestType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The paranoid check a read of a {@link MultiTenancyType#SHARED} entity passes through after the read's own
 * tenant filters: every item is checked against the tenants the read was confined to, and one outside them is
 * dropped and reported. It stays until those filters are trusted, then goes with its call sites.
 * Created by Navíd Mitchell 🤪on 6/13/23.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadPostProcessor {

    private final DomainPersistenceProperties domainPersistenceProperties;
    private final JsonMapper jsonMapper;

    /**
     * The check a page passes through after it is read: an item of a tenant the context does not read is
     * dropped from the page and reported.
     *
     * @param what the read, named in the report
     */
    public <T> Function<Page<T>, Page<T>> paranoidCheck(EntityDescriptor entityDescriptor, EntityContext context, String what){
        return page -> {
            // This is a temporary bit of code to make sure multi tenancy is working properly
            if(entityDescriptor.multiTenancyType() == MultiTenancyType.SHARED){
                String tenantIdFieldName
                        = entityDescriptor.isMultiTenantSelectionEnabled()
                        ? entityDescriptor.tenantIdFieldName() : domainPersistenceProperties.getTenantIdFieldName();

                List<Object> result = new ArrayList<>(page.getContent().size());
                Set<String> tenantIds = Collections.emptySet();
                if(context.hasTenantSelection()) {
                    tenantIds = new HashSet<>(context.getTenantSelection());
                }

                for(Object object : page.getContent()){
                    String tenant = extractTenant(object, tenantIdFieldName);
                    // Find and search methods will use the logged in tenant if no multi tenant selection is provided
                    if(context.hasTenantSelection()){
                        if(tenant != null && (context.selectsAllTenants() || tenantIds.contains(tenant))){
                            result.add(object);
                        }else{
                            log.error(
                                    "{} Multi tenancy is not working properly for EntityDefinition: {} and expected one of: {} got: {}\nData:\n{}",
                                    what,
                                    entityDescriptor,
                                    String.join(",", tenantIds),
                                    tenant,
                                    formatToPrintJson(object));
                        }
                    }else {
                        String tenantId = context.requireTenantId();
                        if (tenant != null && tenant.equals(tenantId)) {
                            result.add(object);
                        }else{
                            log.error(
                                    "{} Multi tenancy is not working properly for EntityDefinition: {} and expected tenant: {} got: {}\nData:\n{}",
                                    what,
                                    entityDescriptor,
                                    tenantId,
                                    tenant,
                                    formatToPrintJson(object));
                        }
                    }
                }

                if(page instanceof CursorPage){
                    @SuppressWarnings("unchecked")
                    Page<T> newPage = (Page<T>) new CursorPage<>(result, ((CursorPage<?>) page).getCursor(), page.getTotalElements());
                    return newPage;
                }else{
                    @SuppressWarnings("unchecked")
                    Page<T> newPage = (Page<T>) new Page<>(result, page.getTotalElements());
                    return newPage;
                }
            }else{
                return page;
            }
        };
    }

    private String extractTenant(Object object, String tenantIdFieldName){
        Object data = (object instanceof FastestType ? ((FastestType) object).data() : object);
        if(data instanceof RawJson rawJson){
            try {
                Map<?,?> converted = jsonMapper.readValue(rawJson.data(), Map.class);
                return (String) converted.get(tenantIdFieldName);
            } catch (JacksonException e) {
                throw new IllegalStateException("RawJson could not be deserialized for sanity check",e);
            }

        } else if (data instanceof Map<?,?> map) {
            return (String) map.get(tenantIdFieldName);
        }else{
            throw new NotImplementedException("Pojo Multi tenancy check is not implemented yet");
        }
    }

    private String formatToPrintJson(Object object){
        Object data = (object instanceof FastestType ? ((FastestType) object).data() : object);
        try {
            if(data instanceof Map<?,?> map){
                return jsonMapper.convertValue(map, ObjectNode.class).toPrettyString();
            }else if(data instanceof RawJson rawJson){
                return jsonMapper.readValue(rawJson.data(), ObjectNode.class).toPrettyString();
            }else{
                return jsonMapper.convertValue(data, ObjectNode.class).toPrettyString();
            }
        } catch (Exception e) {
            return data.toString();
        }
    }
}
