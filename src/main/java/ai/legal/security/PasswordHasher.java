package ai.legal.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 使用 PBKDF2WithHmacSHA256 做密码哈希（无第三方依赖）。
 */
public class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    public PasswordHasher(int iterations) {
        this.iterations = Math.max(10_000, iterations);
    }

    public PasswordHash hash(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = pbkdf2(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        String saltB64 = Base64.getEncoder().encodeToString(salt);
        String hashB64 = Base64.getEncoder().encodeToString(derived);
        return new PasswordHash(hashB64, saltB64, iterations, KEY_LENGTH_BITS);
    }

    public boolean verify(String password, String expectedHashBase64, String saltBase64) {
        if (password == null || expectedHashBase64 == null || saltBase64 == null) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(saltBase64);
            expected = Base64.getDecoder().decode(expectedHashBase64);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        return constantTimeEquals(expected, actual);
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败: " + e.getMessage(), e);
        }
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    public static class PasswordHash {
        private final String hashBase64;
        private final String saltBase64;
        private final int iterations;
        private final int keyLengthBits;

        public PasswordHash(String hashBase64, String saltBase64, int iterations, int keyLengthBits) {
            this.hashBase64 = hashBase64;
            this.saltBase64 = saltBase64;
            this.iterations = iterations;
            this.keyLengthBits = keyLengthBits;
        }

        public String getHashBase64() {
            return hashBase64;
        }

        public String getSaltBase64() {
            return saltBase64;
        }

        public int getIterations() {
            return iterations;
        }

        public int getKeyLengthBits() {
            return keyLengthBits;
        }
    }
}

