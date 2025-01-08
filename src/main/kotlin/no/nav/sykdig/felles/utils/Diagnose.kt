package no.nav.sykdig.felles.utils

import no.nav.helse.diagnosekoder.Diagnosekoder
import no.nav.sykdig.felles.exceptions.ClientException
import no.nav.sykdig.generated.types.DiagnoseInput

fun validateDiagnose(diagnose: DiagnoseInput) {
    validateDiagnoseSystem(diagnose.system)
    validateDiagnoseKode(diagnose.system, diagnose.kode)
}

fun getDiagnoseText(
    system: String,
    kode: String,
): String {
    return when (system) {
        "ICD10" -> Diagnosekoder.icd10[kode]?.text ?: throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
        "ICPC2" -> Diagnosekoder.icpc2[kode]?.text ?: throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
        else -> throw ClientException("Diagnosekode system som er benyttet: $system er ukjent")
    }
}

private fun validateDiagnoseSystem(system: String) {
    if ("ICD10" != system && "ICPC2" != system) {
        throw ClientException("Diagnosekode system som er benyttet: $system er ukjent")
    }
}

private fun validateDiagnoseKode(
    system: String,
    kode: String,
) {
    if (system == "ICD10" && Diagnosekoder.icd10[kode] == null) {
        throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
    } else if (system == "ICPC2" && Diagnosekoder.icpc2[kode] == null) {
        throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
    }
}
