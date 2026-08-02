package org.kinotic.core.api.utils;

import net.openhft.hashing.LongTupleHashFunction;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.function.Function;

/**
 *
 * Created by Navid Mitchell on 6/10/20
 */
public class KinoticUtil {

    public static String safeEncodeURI(String uri){
        String encoded = URLEncoder.encode(uri, StandardCharsets.UTF_8);
        return encoded.replace("_", "-");
    }

    /**
     * Returns the MCP tool name for a service function: the XXHash128 of the service's qualified name plus
     * the function name, in base 36. A qualified name is unique system wide, so its hash is too, and base 36
     * keeps the result to 25 characters or fewer whatever the service is called. Tool names are minted here
     * and never parsed back apart.
     * @param qualifiedName the service's qualified name, as {@code ServiceIdentifier.qualifiedName()} returns
     *                      it, such as {@code os-api~org.kinotic.os.api.services.ProjectService}
     * @param functionName the name of the function the tool calls
     * @return the tool name, made only of {@code [0-9a-z]}
     */
    public static String mcpToolName(String qualifiedName, String functionName) {
        // MCP allows characters in a tool name that LLM hosts do not — a dot most commonly. A host rewrites
        // each one, and then holds two names for the same tool: the advertised one and the callable one.
        // Layers that compare the wrong pair (a permission grant recorded against one, checked against the
        // other) break. Base 36 emits only [0-9a-z], so there is nothing left for a host to rewrite.
        long[] hash = LongTupleHashFunction.xx128().hashChars(qualifiedName + "/" + functionName);
        byte[] bytes = ByteBuffer.allocate(Long.BYTES * 2).putLong(hash[0]).putLong(hash[1]).array();
        // signum 1 reads the 16 bytes as an unsigned 128-bit magnitude, so a high bit set in hash[0] stays a
        // positive value rather than becoming a two's-complement negative one
        return new BigInteger(1, bytes).toString(36);
    }

    public static int getRandomNumberInRange(int max) {
        Random r = new Random();
        return r.ints(0, (max + 1)).findFirst().orElseThrow();
    }

    /**
     * Provides a {@link LinearConverter<Long>} to convert longs for the provided ranges
     * @param oldMin the old range min
     * @param oldMax the old range max
     * @param newMin the new range min
     * @param newMax the new range max
     * @return the new {@link LinearConverter<Long>} that can be used for conversion
     */
    public static LinearConverter<Long> linearConverter(long oldMin, long oldMax, long newMin, long newMax){
        return new LongLinearConverter(oldMin, oldMax, newMin, newMax);
    }


    public interface LinearConverter<T extends Number> {
        T convert(T value);
    }

    public static class LongLinearConverter implements LinearConverter<Long>{

        private final Function<Long, Long> conversionFunction;

        public LongLinearConverter(long oldMin, long oldMax, long newMin, long newMax) {

            long oldRange = (oldMax - oldMin);
            if (oldRange == 0) {
                conversionFunction = (oldValue) -> newMin;
            } else {
                long newRange = newMax - newMin;
                conversionFunction = (oldValue) -> (((oldValue - oldMin) * newRange) / oldRange) + newMin;
            }
        }

        public Long convert(Long value){
            return conversionFunction.apply(value);
        }
    }

}
