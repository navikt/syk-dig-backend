package no.nav.sykdig.utils

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class ValidatePersonNumberTests {

    @Test
    fun validatePersonNumberValid() {

        val validFnr = "20086600138"

        val result = validatePersonDNumberMod11(validFnr)

        result shouldBeEqualTo true
    }

    @Test
    fun validatePersonNumberInvalid10Digist() {

        val invalidFnr = "2008660013"

        val result = validatePersonDNumberMod11(invalidFnr)

        result shouldBeEqualTo false
    }

    @Test
    fun validatePersonNumberInvalid() {

        val invalidFnr = "20086600137"

        val result = validatePersonDNumberMod11(invalidFnr)

        result shouldBeEqualTo false
    }
}
