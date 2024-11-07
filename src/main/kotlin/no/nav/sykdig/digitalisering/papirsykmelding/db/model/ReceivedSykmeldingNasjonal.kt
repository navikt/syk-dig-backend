package no.nav.sykdig.digitalisering.papirsykmelding.db.model

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelding
import no.nav.sykdig.digitalisering.sykmelding.Merknad
import no.nav.sykdig.digitalisering.sykmelding.UtenlandskSykmelding
import java.time.LocalDateTime

data class ReceivedSykmeldingNasjonal(
    val sykmelding: Sykmelding,
    val personNrPasient: String,
    val tlfPasient: String?,
    val personNrLege: String,
    val legeHelsepersonellkategori: String?,
    val legeHprNr: String?,
    val navLogId: String,
    val msgId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val legekontorOrgName: String,
    val mottattDato: LocalDateTime,
    val rulesetVersion: String?,
    val merknader: List<Merknad>?,
    val partnerreferanse: String?,
    val vedlegg: List<String>?,
    val utenlandskSykmelding: UtenlandskSykmelding?,
    /**
     * Full fellesformat as a XML payload, this is only used for infotrygd compat and should be
     * removed in thefuture
     */
    val fellesformat: String,
    /** TSS-ident, this is only used for infotrygd compat and should be removed in thefuture */
    val tssid: String?
)
