package com.pranit.connect.crypto;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageCryptoService {

    public static final String ENCRYPTED_PREFIX = "enc:v1:";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private static volatile MessageCryptoService instance;

    private final ChatEncryptionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    public static MessageCryptoService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MessageCryptoService is not initialized.");
        }
        return instance;
    }

    @PostConstruct
    public void init() {
        if (this.properties == null
                || this.properties.secretKey() == null
                || this.properties.secretKey().isBlank()) {
            throw new IllegalStateException("Strict configuration failure.");
        }
        this.secretKey = deriveKey(this.properties.secretKey().trim());
        instance = this;
        log.info("MessageCryptoService initialized successfully.");
    }

    private SecretKey deriveKey(final String secret) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] keyBytes = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES-256 key", e);
        }
    }

    public String encrypt(final String plainText) {
        if (plainText == null) return null;
        if (plainText.startsWith(ENCRYPTED_PREFIX)) return plainText;
        try {
            final byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            this.secureRandom.nextBytes(iv);
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            final GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, parameterSpec);
            final byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            final byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Failed to encrypt message content", e);
            throw new IllegalStateException("Message encryption failed", e);
        }
    }

    public String decrypt(final String dbContent) {
        if (dbContent == null) return null;
        if (!dbContent.startsWith(ENCRYPTED_PREFIX)) return dbContent;
        try {
            final String base64Payload = dbContent.substring(ENCRYPTED_PREFIX.length());
            final byte[] combined = Base64.getDecoder().decode(base64Payload);
            if (combined.length < GCM_IV_LENGTH_BYTES) {
                log.warn("Corrupted encrypted payload: length too short");
                return dbContent;
            }
            final byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            final int cipherTextLength = combined.length - GCM_IV_LENGTH_BYTES;
            final byte[] cipherText = new byte[cipherTextLength];
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherTextLength);
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            final GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, this.secretKey, parameterSpec);
            final byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decrypt message content: {}", e.getMessage());
            return dbContent;
        }
    }
}
