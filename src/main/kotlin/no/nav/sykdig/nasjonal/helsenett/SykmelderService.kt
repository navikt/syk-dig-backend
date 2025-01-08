package no.nav.sykdig.nasjonal.helsenett

import no.nav.sykdig.felles.LoggingMeta
import no.nav.sykdig.felles.applog
import no.nav.sykdig.felles.exceptions.SykmelderNotFoundException
import no.nav.sykdig.nasjonal.helsenett.client.HelsenettClient
import no.nav.sykdig.nasjonal.helsenett.client.SmtssClient
import no.nav.sykdig.nasjonal.model.Godkjenning
import no.nav.sykdig.nasjonal.model.Kode
import no.nav.sykdig.nasjonal.model.Sykmelder
import no.nav.sykdig.pdl.PersonService
import no.nav.sykdig.felles.securelog
import org.springframework.stereotype.Service

@Service
class SykmelderService(
    private val helsenettClient: HelsenettClient,
    private val personService: PersonService,
    private val smtssClient: SmtssClient,
) {
    val log = applog()
    val securelog = securelog()

    fun getSykmelder(
        hprNummer: String,
        callId: String,
    ): Sykmelder {
        val hprPadded = padHpr(hprNummer)
        val behandler = helsenettClient.getBehandler(hprPadded, callId)
        securelog.info("hentet behandler: ${behandler.fnr} hprNummer: $hprNummer, callId=$callId")
        if (behandler.fnr == null) {
            log.warn("Kunne ikke hente fnr for hpr {}", hprPadded)
            throw SykmelderNotFoundException("Kunne ikke hente fnr for hpr $hprPadded")
        }
        // Helsedir har ikke migriert alle med Helsepersonellkategori(OID=9060) Verdien FA over til
        // FA1 eller FA2,
        // da det var mulighet at noe måtte ligge igjen for historiske årsaker
        val godkjenninger = changeHelsepersonellkategoriVerdiFromFAToFA1(behandler.godkjenninger)

        val pdlPerson = personService.getPerson(behandler.fnr, callId)

        return Sykmelder(
            hprNummer = hprPadded,
            fnr = behandler.fnr,
            aktorId = pdlPerson.aktorId,
            fornavn = pdlPerson.navn.fornavn,
            mellomnavn = pdlPerson.navn.mellomnavn,
            etternavn = pdlPerson.navn.etternavn,
            godkjenninger = godkjenninger,
        )
    }

    fun getSykmelderForAvvistOppgave(
        hpr: String?,
        callId: String,
        oppgaveId: Int,
    ): Sykmelder {
        try {
            log.info("Henter sykmelder fra HPR og PDL for oppgaveid $oppgaveId")
            if (hpr.isNullOrBlank()) {
                return getDefaultSykmelder()
            }
            return getSykmelder(hpr, callId)
        } catch (ex: Exception) {
            log.error(ex.message, ex)
            return getDefaultSykmelder()
        }
    }

    private fun getDefaultSykmelder(): Sykmelder =
        Sykmelder(
            fornavn = "Helseforetak",
            hprNummer = null,
            fnr = null,
            aktorId = null,
            mellomnavn = null,
            etternavn = null,
            godkjenninger = null,
        )

    suspend fun getTssIdInfotrygd(samhandlerFnr: String, samhandlerOrgName: String, loggingMeta: LoggingMeta, sykmeldingId: String): String {
        return smtssClient.findBestTssInfotrygd(samhandlerFnr, samhandlerOrgName, loggingMeta, sykmeldingId)
    }

    fun changeHelsepersonellkategoriVerdiFromFAToFA1(
        godkjenninger: List<Godkjenning>,
    ): List<Godkjenning> {
        return if (godkjenninger.isNotEmpty()) {
            return godkjenninger.map {
                if (it.helsepersonellkategori?.verdi == "FA") {
                    Godkjenning(
                        helsepersonellkategori =
                            Kode(
                                aktiv = it.helsepersonellkategori.aktiv,
                                oid = it.helsepersonellkategori.oid,
                                verdi = "FA1",
                            ),
                        autorisasjon = it.autorisasjon,
                    )
                } else {
                    Godkjenning(
                        helsepersonellkategori = it.helsepersonellkategori,
                        autorisasjon = it.autorisasjon,
                    )
                }
            }
        } else {
            godkjenninger
        }
    }

    private fun padHpr(hprnummer: String): String {
        if (hprnummer.length < 9) {
            securelog.info("padder hpr: $hprnummer")
            return hprnummer.padStart(9, '0')
        }
        return hprnummer
    }
}
