package com.medmaint.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hachage des mots de passe : format, vérification, rejet des entrées invalides. */
class PasswordHasherTest {

    @Test
    void hashUsesPbkdf2Format() {
        String encoded = PasswordHasher.hash("demo2026");
        assertTrue(encoded.startsWith("pbkdf2$"), "format attendu pbkdf2$…");
        assertTrue(encoded.split("\\$").length == 4);
    }

    @Test
    void verifyAcceptsCorrectPassword() {
        String encoded = PasswordHasher.hash("demo2026");
        assertTrue(PasswordHasher.verify("demo2026", encoded));
        assertFalse(PasswordHasher.verify("demo2027", encoded));
        assertFalse(PasswordHasher.verify("", encoded));
        assertFalse(PasswordHasher.verify(null, encoded));
    }

    @Test
    void verifyRejectsMalformedStoredValues() {
        assertFalse(PasswordHasher.verify("demo2026", null));
        assertFalse(PasswordHasher.verify("demo2026", "plaintext"));
        assertFalse(PasswordHasher.verify("demo2026", "pbkdf2$bad"));
        assertFalse(PasswordHasher.verify("demo2026", "pbkdf2$abc$xyz$uvw"));
    }

    @Test
    void saltsDifferBetweenHashes() {
        String a = PasswordHasher.hash("demo2026");
        String b = PasswordHasher.hash("demo2026");
        assertNotEquals(a, b, "deux hachages doivent utiliser des sels distincts");
        assertTrue(PasswordHasher.verify("demo2026", a));
        assertTrue(PasswordHasher.verify("demo2026", b));
    }
}
