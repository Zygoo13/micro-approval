package com.microapproval.api.service;

import com.microapproval.api.config.AiCredentialsProperties;
import com.microapproval.api.exception.AiCredentialEncryptionUnavailableException;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CredentialCipher {
    private static final int IV_LENGTH = 12;
    private final AiCredentialsProperties properties;
    private final SecureRandom random = new SecureRandom();
    public CredentialCipher(AiCredentialsProperties properties) { this.properties = properties; }
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new javax.crypto.spec.GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length); System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(combined);
        } catch (AiCredentialEncryptionUnavailableException exception) { throw exception; }
        catch (Exception exception) { throw new AiCredentialEncryptionUnavailableException("Không thể bảo vệ khóa AI"); }
    }
    public String decrypt(String ciphertext) {
        try {
            if (ciphertext == null || !ciphertext.startsWith("v1:")) throw new IllegalStateException("Định dạng khóa AI không hợp lệ");
            byte[] combined = Base64.getDecoder().decode(ciphertext.substring(3));
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new javax.crypto.spec.GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (AiCredentialEncryptionUnavailableException exception) { throw exception; }
        catch (Exception exception) { throw new AiCredentialEncryptionUnavailableException("Không thể đọc khóa AI đã bảo vệ"); }
    }
    private SecretKey key() {
        String value = properties.getEncryptionKey();
        if (value == null || value.isBlank()) throw new AiCredentialEncryptionUnavailableException("Server chưa có AI_CREDENTIAL_ENCRYPTION_KEY để bảo vệ API key");
        byte[] decoded = Base64.getDecoder().decode(value);
        if (decoded.length != 32) throw new AiCredentialEncryptionUnavailableException("AI_CREDENTIAL_ENCRYPTION_KEY phải là khóa AES-256 Base64 hợp lệ");
        return new SecretKeySpec(decoded, "AES");
    }
}
