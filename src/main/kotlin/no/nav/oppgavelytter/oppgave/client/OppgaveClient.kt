package no.nav.oppgavelytter.oppgave.client

import no.nav.oppgavelytter.accesstoken.AccessTokenClient
import no.nav.sykdig.applog
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

class OppgaveClient(
    private val url: String,
    private val accessTokenClient: AccessTokenClient,
    private val webClient: WebClient,
    private val scope: String,
) {
    val logger = applog()

    fun hentOppgave(
        oppgaveId: Long,
        sporingsId: String,
    ): OppgaveResponse {
        return try {
            val token = accessTokenClient.getAccessToken(scope)
            webClient.get()
                .uri("$url/$oppgaveId")
                .header("Authorization", "Bearer $token")
                .header("X-Correlation-ID", sporingsId)
                .retrieve()
                .bodyToMono<OppgaveResponse>()
                .block()!!
        } catch (e: Exception) {
            logger.error(
                "Noe gikk galt ved henting av oppgave med id $oppgaveId, sporingsId $sporingsId",
                e,
            )
            throw e
        }
    }

    fun oppdaterOppgave(
        oppdaterOppgaveRequest: OppdaterOppgaveRequest,
        sporingsId: String,
    ) {
        val token = accessTokenClient.getAccessToken(scope)

        val response =
            webClient.patch()
                .uri("$url/${oppdaterOppgaveRequest.id}")
                .header("Authorization", "Bearer $token")
                .header("X-Correlation-ID", sporingsId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(oppdaterOppgaveRequest)
                .retrieve()
                .toBodilessEntity()
                .block()

        if (response != null) {
            if (response.statusCode != HttpStatus.OK) {
                logger.error(
                    "Noe gikk galt ved oppdatering av oppgave for sporingsId $sporingsId: ${response.statusCode}",
                )
                throw RuntimeException(
                    "Noe gikk galt ved oppdatering av oppgave, responskode ${response.statusCode}",
                )
            }
        }
    }
}

data class OppgaveResponse(
    val journalpostId: String?,
    val behandlesAvApplikasjon: String?,
    val tema: String,
    val behandlingstema: String?,
    val oppgavetype: String,
    val behandlingstype: String?,
    val versjon: Int,
    val metadata: Map<String, String?>?,
    val ferdigstiltTidspunkt: String?,
    val tildeltEnhetsnr: String,
)

data class OppdaterOppgaveRequest(
    val id: Int,
    val versjon: Int,
    val behandlesAvApplikasjon: String,
)
