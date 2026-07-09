package no.nav.sykdig.shared.utils

import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.shared.Diagnose
import no.nav.sykdig.shared.exceptions.ClientException
import no.nav.tsm.diagnoser.ICD10
import no.nav.tsm.diagnoser.ICPC2

fun validateDiagnose(diagnose: DiagnoseInput) {
    validateDiagnoseSystem(diagnose.system)
    validateDiagnoseKode(diagnose.system, diagnose.kode)
}

fun DiagnoseInput.mapToDiagnose(): Diagnose {
    return Diagnose(
        system = this.system,
        // New diagnose lib supports having the real ICD10 codes that has a . in it, lets remove it
        kode = if (this.system == "ICD10") this.kode.replace(".", "") else this.kode,
        tekst = getDiagnoseText(this.system, this.kode),
    )
}

fun getDiagnoseText(system: String, kode: String): String {
    return when (system) {
        "ICD10" ->
            ICD10[kode]?.text
                ?: throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
        "ICPC2" ->
            ICPC2[kode]?.text
                ?: throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
        else -> throw ClientException("Diagnosekode system som er benyttet: $system er ukjent")
    }
}

private fun validateDiagnoseSystem(system: String) {
    if ("ICD10" != system && "ICPC2" != system) {
        throw ClientException("Diagnosekode system som er benyttet: $system er ukjent")
    }
}

private fun validateDiagnoseKode(system: String, kode: String) {
    if (system == "ICD10" && ICD10[kode] == null) {
        throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
    } else if (system == "ICPC2" && ICPC2[kode] == null) {
        throw ClientException("Diagnosekoden som er benyttet: $kode er ukjent")
    }
}
