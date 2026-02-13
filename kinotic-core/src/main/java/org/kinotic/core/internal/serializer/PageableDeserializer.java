package org.kinotic.core.internal.serializer;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.services.crud.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by navid on 2/4/20
 */
public class PageableDeserializer extends ValueDeserializer<Pageable> {

    @Override
    public Pageable deserialize(JsonParser jp, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = jp.objectReadContext().readTree(jp);
        Validate.isTrue(node.has("pageSize"), "pageSize missing from Pageable");
        int pageSize = node.get("pageSize").intValue();

        Sort sort = null;
        if(node.has("sort")){
            JsonNode ordersNode = node.get("sort").get("orders");
            if(ordersNode != null && ordersNode.isArray()){
                List<Order> orders = deserializeOrders(ordersNode);
                sort = Sort.by(orders);
            }
        }

        Integer pageNumber = null;
        if(node.has("pageNumber")){
            pageNumber = node.get("pageNumber").intValue();
        }

        // We do this check with a boolean, because a null cursor indicates the first page
        String cursor = null;
        boolean cursorPresent = false;
        if(node.has("cursor")){
            cursorPresent = true;
            cursor = node.get("cursor").stringValue();
        }

        if(!cursorPresent && pageNumber == null){
            throw new IllegalArgumentException("Pageable must have either a cursor or pageNumber");
        } else if (cursorPresent && pageNumber != null) {
            throw new IllegalArgumentException("Pageable cannot have both a cursor and a pageNumber");
        }

        Pageable ret;
        if(pageNumber == null){
            ret = Pageable.create(cursor, pageSize, sort);
        }else{
            ret = Pageable.create(pageNumber, pageSize, sort);
        }
        return ret;
    }

    private List<Order> deserializeOrders(JsonNode ordersNode){
        List<Order> ret = new LinkedList<>();
        for(JsonNode node: ordersNode){

            Validate.isTrue(node.has("direction"), "direction missing from Order");
            Direction direction = Direction.fromString(node.get("direction").stringValue());

            Validate.isTrue(node.has("property"), "property missing from Order");
            String property = node.get("property").stringValue();

            if(node.has("nullHandling")){
                NullHandling nullHandling = NullHandling.valueOf(node.get("nullHandling").stringValue());
                ret.add(new Order(direction, property, nullHandling));
            }else {
                ret.add(new Order(direction, property));
            }
        }
        return ret;
    }


}
