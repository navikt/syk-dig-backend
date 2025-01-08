package no.nav.sykdig.utenlandsk.models

import java.time.LocalDateTime

data class SykmeldingUnderArbeid(
    val sykmelding: SDSykmelding,
    var fnrPasient: String,
    val fnrLege: String?,
    val legeHprNr: String?,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorOrgName: String?,
    val mottattDato: LocalDateTime?,
    var utenlandskSykmelding: UtenlandskSykmelding?,
)
