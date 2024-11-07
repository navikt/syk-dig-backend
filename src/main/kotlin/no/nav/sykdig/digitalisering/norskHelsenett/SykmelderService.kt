package no.nav.sykdig.digitalisering.norskHelsenett

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.SykmelderNotFoundException
import no.nav.sykdig.digitalisering.norskHelsenett.client.NorskHelsenettClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Godkjenning
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Kode
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.securelog
import org.springframework.stereotype.Service

@Service
class SykmelderService(
    private val norskHelsenettClient: NorskHelsenettClient,
    private val personService: PersonService,

) {

    val log = applog()
    val secureLog = securelog()

    suspend fun hentSykmelder(hprNummer: String, callId: String): Sykmelder? {
        val behandler = norskHelsenettClient.getBehandler(hprNummer, callId)
        if (behandler.fnr.isNullOrEmpty()){
            log.warn("Kunne ikke hente fnr for hpr {}", hprNummer)
            throw SykmelderNotFoundException("Kunne ikke hente fnr for hpr $hprNummer")
        }
        // Helsedir har ikke migriert alle med Helsepersonellkategori(OID=9060) Verdien FA over til
        // FA1 eller FA2,
        // da det var mulighet at noe måtte ligge igjen for historiske årsaker
        val godkjenninger = changeHelsepersonellkategoriVerdiFromFAToFA1(behandler.godkjenninger)

        val pdlPerson = personService.getPerson(behandler.fnr, callId)

        return Sykmelder(
            hprNummer = hprNummer,
            fnr = behandler.fnr,
            aktorId = pdlPerson.aktorId,
            fornavn = pdlPerson.navn.fornavn,
            mellomnavn = pdlPerson.navn.mellomnavn,
            etternavn = pdlPerson.navn.etternavn,
            godkjenninger = godkjenninger
        )
    }

    fun changeHelsepersonellkategoriVerdiFromFAToFA1(
        godkjenninger: List<Godkjenning>
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
                        autorisasjon = it.autorisasjon
                    )
                }
            }
        } else {
            godkjenninger
        }
    }



}
