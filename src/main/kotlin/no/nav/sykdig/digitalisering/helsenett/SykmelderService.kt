package no.nav.sykdig.digitalisering.helsenett

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.SykmelderNotFoundException
import no.nav.sykdig.digitalisering.helsenett.client.HelsenettClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Godkjenning
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Kode
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
import no.nav.sykdig.digitalisering.pdl.PersonService
import org.springframework.stereotype.Service

@Service
class SykmelderService(
    private val helsenettClient: HelsenettClient,
    private val personService: PersonService,
) {
    val log = applog()

    suspend fun getSykmelder(
        hprNummer: String,
        callId: String,
    ): Sykmelder {
        if (hprNummer.isEmpty()) {
            log.warn("HPR-nummer mangler, kan ikke fortsette")
            throw IllegalStateException("HPR-nummer mangler")
        }
        val hprPadded = padHpr(hprNummer)
        val behandler = helsenettClient.getBehandler(hprPadded, callId)
        if (behandler.fnr == null) {
            log.warn("Kunne ikke hente fnr for hpr {}", hprPadded)
            throw SykmelderNotFoundException("Kunne ikke hente fnr for hpr $hprPadded")
        }
        // Helsedir har ikke migriert alle med Helsepersonellkategori(OID=9060) Verdien FA over til
        // FA1 eller FA2,
        // da det var mulighet at noe måtte ligge igjen for historiske årsaker
        val godkjenninger = changeHelsepersonellkategoriVerdiFromFAToFA1(behandler.godkjenninger)

        val pdlPerson = personService.getPerson(behandler.fnr, callId)
        // nullchecker ikke pdlPerosn fordi dette er vel gjort tidligere i løpet
        // men må testes

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
            return hprnummer.padStart(9, '0')
        }
        return hprnummer
    }
}
