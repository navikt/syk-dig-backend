package no.nav.sykdig.utils

import no.nav.syfo.sm.Diagnosekoder
import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.generated.types.DiagnoseInput

fun validateDiagnose(diagnose: DiagnoseInput) {
    validateDiagnoseSystem(diagnose.system)
    validateDiagnoseKode(diagnose.system, diagnose.kode)
}

private fun validateDiagnoseSystem(system: String) {
    if ("ICD10" != system && "ICPC2" != system) {
        throw ClientException("Diagnosekode system som er benyttet: $system er ukjent")
    }
}

private fun validateDiagnoseKode(system: String, kode: String) {
    if (system == "ICD10" && Diagnosekoder.icd10[kode] == null) {
        throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
    } else if (system == "ICPC2" && Diagnosekoder.icpc2[kode] == null) {
        throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
    }
}
