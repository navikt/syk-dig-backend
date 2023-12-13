package no.nav.sykdig.utils

import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.generated.types.DiagnoseInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ValidateDiagnoseTests {
    @Test
    fun validateDiagnoseOKICPC2() {
        val validDiagnose = DiagnoseInput(kode = "Z09", system = "ICPC2")

        val result =
            assertDoesNotThrow("Should not throw an exception") {
                validateDiagnose(validDiagnose)
            }

        assertEquals(Unit, result)
    }

    @Test
    fun validateDiagnoseOKICD10() {
        val validDiagnose = DiagnoseInput(kode = "T909", system = "ICD10")

        val result =
            assertDoesNotThrow("Should not throw an exception") {
                validateDiagnose(validDiagnose)
            }

        assertEquals(Unit, result)
    }

    @Test
    fun validateDiagnoseInvalidSystem() {
        val invalidDiagnose = DiagnoseInput(kode = "Z09", system = "ICPC-2")

        val exception =
            assertThrows<ClientException> {
                validateDiagnose(invalidDiagnose)
            }
        assertEquals(
            "Diagnosekode system som er benyttet: ${invalidDiagnose.system} er ukjent",
            exception.message,
        )
    }

    @Test
    fun validateDiagnoseInvalidKode() {
        val invalidDiagnose = DiagnoseInput(kode = "Z099", system = "ICPC2")

        val exception =
            assertThrows<ClientException> {
                validateDiagnose(invalidDiagnose)
            }
        assertEquals(
            "Diagnosekoden som er benyttet: ${invalidDiagnose.kode} er ukjent",
            exception.message,
        )
    }
}
