package com.microapproval.api.service;

import com.microapproval.api.config.AiCredentialsProperties;
import com.microapproval.api.exception.AiCredentialEncryptionUnavailableException;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class CredentialCipherTest {
    @Test void encryptsWithRandomIvAndDecryptsWithoutKeepingPlaintext() {
        AiCredentialsProperties properties = new AiCredentialsProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        CredentialCipher cipher = new CredentialCipher(properties);
        String first = cipher.encrypt("sk-test-secret-value");
        String second = cipher.encrypt("sk-test-secret-value");
        assertNotEquals(first, second);
        assertFalse(first.contains("sk-test-secret-value"));
        assertEquals("sk-test-secret-value", cipher.decrypt(first));
    }
    @Test void failsClosedWhenMasterKeyIsMissing() {
        assertThrows(AiCredentialEncryptionUnavailableException.class, () -> new CredentialCipher(new AiCredentialsProperties()).encrypt("secret"));
    }
}
