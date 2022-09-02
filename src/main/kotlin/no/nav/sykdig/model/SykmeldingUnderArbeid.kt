package no.nav.sykdig.model

import no.nav.syfo.model.UtenlandskSykmelding
import java.time.LocalDateTime

data class SykmeldingUnderArbeid(
    val sykmelding: Sykmelding?,
    val personNrPasient: String,
    val personNrLege: String?,
    val legeHprNr: String?,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorOrgName: String?,
    val mottattDato: LocalDateTime?,
    val utenlandskSykmelding: UtenlandskSykmelding?
)
