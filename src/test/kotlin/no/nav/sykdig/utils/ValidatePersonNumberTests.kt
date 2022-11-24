package no.nav.sykdig.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValidatePersonNumberTests {

    @Test
    fun validatePersonNumberValid() {

        val validFnr = "20086600138"

        val result = validatePersonDNumberMod11(validFnr)

        assertEquals(true, result)
    }

    @Test
    fun validatePersonNumberInvalid10Digist() {

        val invalidFnr = "2008660013"

        val result = validatePersonDNumberMod11(invalidFnr)

        assertEquals(false, result)
    }

    @Test
    fun validatePersonNumberInvalid() {

        val invalidFnr = "20086600137"

        val result = validatePersonDNumberMod11(invalidFnr)

        assertEquals(false, result)
    }
}
