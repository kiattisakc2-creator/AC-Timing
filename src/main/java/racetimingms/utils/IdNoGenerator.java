package racetimingms.utils;

import java.security.SecureRandom;
import java.time.Instant;

public class IdNoGenerator {

    private IdNoGenerator() {
        throw new IllegalStateException("Utility class");
    }

    public static String generateUniqueIdNo() {
        String prefix = "DRAFT";

        long timestamp = Instant.now().toEpochMilli();

        String randomComponent = generateRandomString(4);

        return prefix +randomComponent + timestamp;
    }

    private static String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
}
