package org.kinotic.idl.api.converter;

/**
 * Thrown when a conversion fails. The message includes the chain of {@link org.kinotic.idl.api.schema.C3Type}s
 * being converted when the failure occurred, root first.
 */
public class C3ConversionException extends RuntimeException {

    public C3ConversionException(String message, Throwable cause) {
        super(message, cause);
    }

}
