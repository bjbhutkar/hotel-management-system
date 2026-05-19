package com.hotel.delivery.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256-CBC encryption for platform API credentials stored in SQLite.
 *
 * The encryption passphrase is derived from the machine-specific secret
 * (configurable via delivery.credential.secret in application.properties).
 * PBKDF2 key derivation ensures brute-force resistance.
 */
@Slf4j
@Service
public class CredentialService {

    private static final String ALGORITHM  = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALG    = "PBKDF2WithHmacSHA256";
    private static final int    ITERATIONS = 65536;
    private static final int    KEY_LEN    = 256;
    private static final byte[] FIXED_SALT = "RasoiPlatformSalt2025".getBytes();

    @Value("${delivery.credential.secret:RasoiDefaultSecretKey!2025}")
    private String masterSecret;

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv         = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
            byte[] combined   = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt credential", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] combined   = Base64.getDecoder().decode(encoded);
            byte[] iv         = new byte[16];
            byte[] ciphertext = new byte[combined.length - 16];
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, ciphertext, 0, ciphertext.length);
            SecretKey key  = deriveKey();
            Cipher cipher  = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt credential", e);
        }
    }

    public boolean isEncrypted(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            Base64.getDecoder().decode(value);
            return value.length() >= 24;
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey deriveKey() throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALG);
        KeySpec spec = new PBEKeySpec(masterSecret.toCharArray(), FIXED_SALT, ITERATIONS, KEY_LEN);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }
}
