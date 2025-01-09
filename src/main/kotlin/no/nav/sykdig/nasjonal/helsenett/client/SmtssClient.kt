package no.nav.sykdig.nasjonal.helsenett.client

import kotlinx.coroutines.reactor.awaitSingleOrNull
import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.exceptions.SykmelderNotFoundException
import no.nav.sykdig.shared.securelog
import org.springframework.beans.factory.annotation.Value

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class SmtssClient(
    @Value("\${smtss.url}") private val smtssUrl: String,
    private val smtssM2mWebClient: WebClient,
) {
    val log = applog()
    val securelog = securelog()

    suspend fun findBestTssInfotrygd(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): String {
        log.info("Fetching TSS ID from SMTSS for samhandlerOrgName: {} with loggingMeta {}", samhandlerOrgName, loggingMeta)

        return try {
            val tssIdent = smtssM2mWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("$smtssUrl/api/v1/samhandler/infotrygd")
                        .build()
                }
                .headers { headers ->
                    headers["samhandlerFnr"] = samhandlerFnr
                    headers["samhandlerOrgName"] = samhandlerOrgName
                    headers["requestId"] = sykmeldingId
                }
                .retrieve()
                .onStatus({ status -> !status.is2xxSuccessful }) { response ->
                    log.info(
                        "SMTSS responded with an error code {} for {}",
                        response.statusCode(),
                        StructuredArguments.fields(loggingMeta),
                    )
                    response.bodyToMono(String::class.java).flatMap { errorBody ->
                        Mono.error(
                            SykmelderNotFoundException(
                                "Error from SMTSS: $errorBody for samhandlerOrgName $samhandlerOrgName"
                            )
                        )
                    }
                }
                .bodyToMono(TSSident::class.java)
                .awaitSingleOrNull()

            tssIdent?.tssid
                ?: throw SykmelderNotFoundException("Samhandlerpraksis ikke funnet for samhandlerOrgname $samhandlerOrgName")
        } catch (e: WebClientResponseException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                throw SykmelderNotFoundException("Samhandlerpraksis ikke funnet for samhandlerOrgname $samhandlerOrgName")
            }
            throw e
        }
    }
}

data class TSSident(
    val tssid: String,
)
