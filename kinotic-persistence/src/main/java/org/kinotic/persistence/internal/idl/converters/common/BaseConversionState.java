package org.kinotic.persistence.internal.idl.converters.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.kinotic.continuum.idl.api.schema.PropertyDefinition;
import org.kinotic.persistence.api.config.StructuresProperties;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Created by Navíd Mitchell 🤪 on 5/14/23.
 */
@Accessors(chain = true)
@RequiredArgsConstructor
public class BaseConversionState {

    private final Deque<String> propertyStack = new ArrayDeque<>();
    @Getter
    private final StructuresProperties structuresProperties;
    @Getter
    private String currentFieldName = null;
    @Getter
    private String currentJsonPath = null;

    /**
     * Must be called before processing a field.
     * This ensures the current field name and json path are set correctly
     * @param propertyDefinition the property definition to begin processing
     */
    public void beginProcessingField(PropertyDefinition propertyDefinition){
        currentFieldName = propertyDefinition.getName();
        // Store decorators for use later with their corresponding json path and type
        currentJsonPath = !propertyStack.isEmpty()
                ? propertyStack.peekFirst() + "." + propertyDefinition.getName() : propertyDefinition.getName();
        propertyStack.addFirst(currentJsonPath);
    }

    /**
     * Must be called after processing a field.
     */
    public void endProcessingField(){
        propertyStack.removeFirst();
        currentFieldName = null;
        currentJsonPath = propertyStack.peekFirst();
    }

    /**
     * @return the nesting depth of the current property being processed
     */
    public int fieldDepth(){
        return propertyStack.size();
    }

}
