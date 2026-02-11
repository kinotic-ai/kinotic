/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.internal.utils;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.BiFunction;

/**
 *
 * Created by Navid Mitchell on 3/11/20
 */
public class SecurityUtil {

    private static final String SHA256_SIGNATURE_METHOD = "HmacSHA256";

    private static final String CHAR_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHAR_UPPERCASE = CHAR_LOWERCASE.toUpperCase();
    private static final String DIGIT = "0123456789";
    private static final String ALPHA_NUMERIC = CHAR_LOWERCASE + CHAR_UPPERCASE + DIGIT;
    private static final String SYMBOL = "_=+*!{}[]()^|<>?#$";

    private static final String PASSWORD_ALLOW = CHAR_LOWERCASE + CHAR_UPPERCASE + DIGIT + SYMBOL;

    private static final SecureRandom random = new SecureRandom();


    /**
     * Verifies a OTP that is created using a shared secret
     * The otp must be in the format HMAC_seconds
     *      Where the HMAC is generated with the (seconds + stringToSign) and the sharedSecret
     *      Seconds it the time that the OTP was generated in seconds since the epoch
     *
     * @param stringToSign the string that was used when generating the HMAC
     * @param sharedSecret the secret known to both parties
     * @param otp the one time pass provided to be verified
     */
    public static boolean canOtpSha256Authenticate(String stringToSign, byte[] sharedSecret, String otp){
        return SecurityUtil.canOtpAuthenticate(stringToSign, sharedSecret, otp, SecurityUtil::hmacSHA256);
    }

    /**
     * Verifies a OTP that is created using a shared secret
     * The otp must be in the format HMAC_seconds
     *      Where the HMAC is generated with the (seconds + stringToSign) and the sharedSecret
     *      Seconds it the time that the OTP was generated in seconds since the epoch
     *
     * @param stringToSign the string that was used when generating the HMAC
     * @param sharedSecret the secret known to both parties
     * @param otp the one time pass provided to be verified
     * @param hmacFunction the function to be used to create the hmac
     */
    private static boolean canOtpAuthenticate(String stringToSign,
                                              byte[] sharedSecret,
                                              String otp,
                                              BiFunction<byte[], byte[], String> hmacFunction){
        // secret is in the format OTP_Seconds where the seconds is the epoch time value used for signing
        String[] parts = otp.split("_");
        if(parts.length != 2){
            throw new IllegalArgumentException("otp format incorrect");
        }
        String signed = parts[0];
        String seconds = parts[1];

        boolean ret = false;
        if(SecurityUtil.verifyTimeInRange(Long.valueOf(seconds), 2, 2)){
            String toSignFull = seconds + stringToSign;
            ret = signed.equals(hmacFunction.apply(toSignFull.getBytes(StandardCharsets.UTF_8), sharedSecret));
        }
        return ret;
    }

    /**
     * Generates a HmacSHA256 for the given data.
     *
     * @param data to sign
     * @param key to be used during the signing process
     * @return the signature in Base64 encoding
     */
    public static String hmacSHA256(byte[] data, byte[] key ) {
        try {
            Mac mac = Mac.getInstance(SHA256_SIGNATURE_METHOD);
            mac.init( new SecretKeySpec(key, SHA256_SIGNATURE_METHOD) );
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        }catch ( Exception e ) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Ensure the given time, as {@link Long} value in seconds, is in the given range.
     * @param epochTimeSeconds the number of seconds since the epoch to verify
     * @param lowerRange the number of minutes to subtract from the current time to use as the beginning of the range.
     * @param upperRange the number of minutes to add to the current time to use as the end of the range.
     * @return true if the time s within the range false if not
     */
    public static boolean verifyTimeInRange(Long epochTimeSeconds, int lowerRange, int upperRange){
        Calendar begin = Calendar.getInstance();
        begin.add( Calendar.MINUTE, -1 * lowerRange);

        Calendar end = Calendar.getInstance();
        end.add(Calendar.MINUTE, upperRange);

        return epochTimeSeconds >= begin.getTimeInMillis() / 1000
                   && epochTimeSeconds <= end.getTimeInMillis() / 1000;
    }

    public static byte[] generateSecretKey(int length){
        try {

            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(length);
            SecretKey secretKey = keyGenerator.generateKey();
            return secretKey.getEncoded();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a random alphanumeric string that can be used as a userid
     * The first 2 character will always be a lowercase letter
     * @param passwordLength the length of the password if longer than 3 other wise the password will be 3 chars in length
     * @return the random string
     */
    public static String generateRandomAlphaNumericUser(int passwordLength){
        passwordLength = Math.max(passwordLength, 3);
        StringBuilder result = new StringBuilder(passwordLength);
        String strLowerCase = generateRandomString(CHAR_LOWERCASE, 2);
        result.append(strLowerCase);
        String strOther = generateRandomString(ALPHA_NUMERIC, passwordLength - 2);
        result.append(strOther);

        return shuffleString(result.toString());
    }

    /**
     * Generates a random alphanumeric string that can be used as a userid
     * The first 2 character will always be a lowercase letter
     * @param passwordLength the length of the password if longer than 3 other wise the password will be 3 chars in length
     * @return the random string
     */
    public static String generateRandomAlphaUser(int passwordLength){
        passwordLength = Math.max(passwordLength, 3);
        StringBuilder result = new StringBuilder(passwordLength);
        String strLowerCase = generateRandomString(CHAR_LOWERCASE, 2);
        result.append(strLowerCase);
        String strOther = generateRandomString(CHAR_LOWERCASE + CHAR_UPPERCASE, passwordLength - 2);
        result.append(strOther);

        return shuffleString(result.toString());
    }

    /**
     * Will generate a password with the given length or 8 chars if the length provided is less than 8
     * @param passwordLength length of the returned password
     * @return the random password
     */
    public static String generateRandomPassword(int passwordLength) {
        passwordLength = Math.max(passwordLength, 8);
        StringBuilder result = new StringBuilder(passwordLength);

        // at least 2 chars (lowercase)
        String strLowerCase = generateRandomString(CHAR_LOWERCASE, 2);
        result.append(strLowerCase);

        // at least 2 chars (uppercase)
        String strUppercaseCase = generateRandomString(CHAR_UPPERCASE, 2);
        result.append(strUppercaseCase);

        // at least 2 digits
        String strDigit = generateRandomString(DIGIT, 2);
        result.append(strDigit);

        // at least 2 special characters
        String strSpecialChar = generateRandomString(SYMBOL, 2);
        result.append(strSpecialChar);

        // remaining, just random
        String strOther = generateRandomString(PASSWORD_ALLOW, passwordLength - 8);
        result.append(strOther);

        return shuffleString(result.toString());
    }



    private static String generateRandomString(String input, int size) {

        if (input == null || input.isEmpty())
            throw new IllegalArgumentException("Invalid input.");
        if (size < 1) throw new IllegalArgumentException("Invalid size.");

        StringBuilder result = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            // produce a random order
            int index = random.nextInt(input.length());
            result.append(input.charAt(index));
        }
        return result.toString();
    }

    private static String shuffleString(String input) {
        List<String> result = Arrays.asList(input.split(""));
        Collections.shuffle(result);
        return String.join("", result);
    }


}
