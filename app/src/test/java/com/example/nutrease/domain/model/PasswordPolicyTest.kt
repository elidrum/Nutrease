package com.example.nutrease.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasswordPolicyTest {

    @Test
    fun `valid password passes`() {
        assertNull(PasswordPolicy.validate("Password1"))
        assertNull(PasswordPolicy.validate("Abcdef12"))
    }

    @Test
    fun `too short is rejected`() {
        assertEquals(PasswordValidationError.TOO_SHORT, PasswordPolicy.validate("Ab1"))
        assertEquals(PasswordValidationError.TOO_SHORT, PasswordPolicy.validate("Abcde1")) // 6 < 8
    }

    @Test
    fun `missing uppercase is rejected`() {
        assertEquals(PasswordValidationError.NO_UPPERCASE, PasswordPolicy.validate("password1"))
    }

    @Test
    fun `missing digit is rejected`() {
        assertEquals(PasswordValidationError.NO_DIGIT, PasswordPolicy.validate("Passwordabc"))
    }

    @Test
    fun `length is checked before character classes`() {
        // troppo corta E senza cifra/maiuscola → prevale TOO_SHORT
        assertEquals(PasswordValidationError.TOO_SHORT, PasswordPolicy.validate("abc"))
    }
}