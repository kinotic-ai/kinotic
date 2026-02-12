

package org.kinotic.rpc.internal.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.function.Function;

/**
 *
 * Created by Navid Mitchell on 6/10/20
 */
public class ContinuumUtil {

    public static String safeEncodeURI(String uri){
        String encoded = URLEncoder.encode(uri, StandardCharsets.UTF_8);
        return encoded.replaceAll("_", "-");
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
