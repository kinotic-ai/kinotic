

package org.kinotic.idl.internal.directory;

import org.kinotic.idl.api.directory.ConversionContext;
import org.kinotic.idl.api.directory.ResolvableTypeConverter;

import org.apache.commons.lang3.StringUtils;
import org.kinotic.idl.api.schema.C3Type;
import org.kinotic.idl.api.schema.ComplexC3Type;
import org.kinotic.idl.api.schema.ObjectC3Type;
import org.kinotic.idl.api.schema.PropertyDefinition;
import org.kinotic.idl.api.schema.ReferenceC3Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;

import java.util.*;

/**
 * Default implementation of {@link ConversionContext}
 * Created by navid on 2019-07-01.
 */
public class DefaultConversionContext implements ConversionContext {

    private static final Logger log = LoggerFactory.getLogger(DefaultConversionContext.class);

    private final ResolvableTypeConverter resolvableTypeConverter;

    private final Deque<ResolvableType> circularReferenceCheckStack = new ArrayDeque<>();

    private final Deque<ResolvableType> errorStack = new ArrayDeque<>();

    private final Map<String, C3Type> schemaCache = new HashMap<>();

    // converted complex types keyed by qualified name, the name every ReferenceC3Type resolves through
    private final Map<String, ObjectC3Type> complexC3Types = new HashMap<>();

    private final boolean shouldCreateReferences;

    /**
     * Creates a new {@link ConversionContext}
     * @param resolvableTypeConverter the converter to use to be used for conversion. Typically, this will be a {@link ResolvableTypeConverterComposite}
     * @param shouldCreateReferences if true, {@link ObjectC3Type}'s will be added to the {@link #getComplexC3Types()} set and {@link ReferenceC3Type}'s will be returned when appropriate
     */
    public DefaultConversionContext(ResolvableTypeConverter resolvableTypeConverter,
                                    boolean shouldCreateReferences) {
        this.resolvableTypeConverter = resolvableTypeConverter;
        this.shouldCreateReferences = shouldCreateReferences;
    }

    public C3Type convertInternal(ResolvableType resolvableType) {

        if(circularReferenceCheckStack.contains(resolvableType)){
            IllegalStateException ise = new IllegalStateException("Circular reference detected for "+resolvableType);
            logException(ise);
            throw ise;
        }
        C3Type ret;
        try {

            circularReferenceCheckStack.addFirst(resolvableType);

            // FIXME: verify this cache logic!
            // Since decorators and metadata could differ on the final C3Type we need to make sure they don't share a java reference when they shouldn't
            String key = resolvableType.toString();
            if (schemaCache.containsKey(key)) {
                ret = schemaCache.get(key);
            } else {
                ret = resolvableTypeConverter.convert(resolvableType, this);
                // We only cache object types
                if(ret instanceof ObjectC3Type){
                    schemaCache.put(key, ret);
                }
            }

        } catch (Exception e){
            logException(e);
            throw e;
        } finally {
            circularReferenceCheckStack.removeFirst();
        }
        return ret;
    }

    @Override
    public C3Type convert(ResolvableType resolvableType) {
        C3Type c3Type = convertInternal(resolvableType);
        if(c3Type instanceof ObjectC3Type objectC3Type && shouldCreateReferences){
            registerComplexType(objectC3Type);
            c3Type = new ReferenceC3Type(objectC3Type.getQualifiedName());
        }
        return c3Type;
    }

    @Override
    public Set<ComplexC3Type> getComplexC3Types() {
        return new HashSet<>(complexC3Types.values());
    }

    /**
     * Registers a converted type under its qualified name, which every {@link ReferenceC3Type} minted for it
     * resolves through.
     * @param objectC3Type to register
     * @throws IllegalStateException if a structurally different type is already registered under the same
     *         qualified name, since every reference to that name would resolve to an arbitrary one of the two
     */
    private void registerComplexType(ObjectC3Type objectC3Type) {
        ObjectC3Type existing = complexC3Types.putIfAbsent(objectC3Type.getQualifiedName(), objectC3Type);
        // the schemaCache returns one instance per instantiation, so a repeat registration is normally the
        // same instance; a distinct, structurally different one means two Java types claim this C3 name
        if (existing != null && existing != objectC3Type && !sameStructure(existing, objectC3Type)) {
            throw new IllegalStateException("Two structurally different types both convert to '"
                    + objectC3Type.getQualifiedName() + "': " + existing + " and " + objectC3Type);
        }
    }

    // ObjectC3Type equality is scoped to the qualified name and PropertyDefinition equality to the property
    // name, so a structural comparison must check every property's type itself. One level deep is
    // sufficient: convert() replaces every nested object with a ReferenceC3Type, and a referenced type
    // passed this same guard before its container registered — a deeper mismatch throws at the depth where
    // it lives.
    private static boolean sameStructure(ObjectC3Type existing, ObjectC3Type candidate) {
        boolean ret = Objects.equals(existing.getParent(), candidate.getParent())
                && existing.getProperties().size() == candidate.getProperties().size();
        if (ret) {
            Iterator<PropertyDefinition> candidateProperties = candidate.getProperties().iterator();
            for (PropertyDefinition property : existing.getProperties()) {
                PropertyDefinition candidateProperty = candidateProperties.next();
                if (!Objects.equals(property.getName(), candidateProperty.getName())
                        || !Objects.equals(property.getType(), candidateProperty.getType())) {
                    ret = false;
                    break;
                }
            }
        }
        return ret;
    }

    /**
     * Log an exception when appropriate dealing with only logging once even when recursion has occurred
     * @param e to log
     */
    private void logException(Exception e){
        if(log.isDebugEnabled() || log.isTraceEnabled()){
            // This indicates this is the first time logException has been called for this context.
            // This would occur at the furthest call depth so at this point the circularReferenceCheckStack has the complete stack
            if(errorStack.isEmpty()){
                // We loop vs add all to keep stack intact
                for(ResolvableType resolvableType: circularReferenceCheckStack){
                    errorStack.addFirst(resolvableType);
                }
            }
            if(circularReferenceCheckStack.size() == 1) { // we are at the top of the stack during recursion
                StringBuilder sb = new StringBuilder("Error occurred during conversion.\n" + e.getMessage() + "\n");
                int objectCount = 1;
                for (ResolvableType resolvableType : errorStack) {
                    sb.append(StringUtils.leftPad("", objectCount, '\t'));
                    sb.append("- ");
                    sb.append(resolvableType.toString());
                    sb.append("\n");
                    objectCount++;
                }
                log.debug(sb.toString());
                errorStack.clear(); // we have printed reset
            }
        }
    }

}
