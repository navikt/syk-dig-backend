package no.nav.syfo.oppgave.client

import no.nav.syfo.accesstoken.AccessTokenClient

class OppgaveClient(
    private val url: String,
    private val accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient,
    private val scope: String,
) {
    suspend fun hentOppgave(
        oppgaveId: Long,
        sporingsId: String,
    ): OppgaveResponse {
        try {
            return httpClient
                .get("$url/$oppgaveId") {
                    val token = accessTokenClient.getAccessToken(scope)
                    header("Authorization", "Bearer $token")
                    header("X-Correlation-ID", sporingsId)
                }
                .body<OppgaveResponse>()
        } catch (e: Exception) {
            logger.error(
                "Noe gikk galt ved henting av oppgave med id $oppgaveId, sporingsId $sporingsId",
                e,
            )
            throw e
        }
    }

    suspend fun oppdaterOppgave(
        oppdaterOppgaveRequest: OppdaterOppgaveRequest,
        sporingsId: String,
    ) {
        val httpResponse: HttpResponse =
            httpClient.patch("$url/${oppdaterOppgaveRequest.id}") {
                contentType(ContentType.Application.Json)
                val token = accessTokenClient.getAccessToken(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", sporingsId)
                setBody(oppdaterOppgaveRequest)
            }
        if (httpResponse.status != HttpStatusCode.OK) {
            logger.error(
                "Noe gikk galt ved oppdatering av oppgave for sporingsId $sporingsId: ${httpResponse.status}, ${httpResponse.body<String>()}",
            )
            throw RuntimeException(
                "Noe gikk galt ved oppdatering av oppgave, responskode ${httpResponse.status}",
            )
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
