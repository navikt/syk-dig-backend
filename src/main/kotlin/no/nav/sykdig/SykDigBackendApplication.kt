package no.nav.sykdig

import no.nav.oppgavelytter.EnvironmentVariables
import no.nav.oppgavelytter.accesstoken.AccessTokenClient
import no.nav.oppgavelytter.kafka.aiven.KafkaUtils
import no.nav.oppgavelytter.kafka.toConsumerConfig
import no.nav.oppgavelytter.kafka.toProducerConfig
import no.nav.oppgavelytter.kafka.util.JacksonKafkaDeserializer
import no.nav.oppgavelytter.kafka.util.JacksonKafkaSerializer
import no.nav.oppgavelytter.oppgave.OppgaveService
import no.nav.oppgavelytter.oppgave.client.OppgaveClient
import no.nav.oppgavelytter.oppgave.kafka.OppgaveConsumer
import no.nav.oppgavelytter.oppgave.kafka.OppgaveKafkaAivenRecord
import no.nav.oppgavelytter.oppgave.saf.SafJournalpostService
import no.nav.oppgavelytter.oppgave.saf.client.SafGraphQlClient
import no.nav.oppgavelytter.oppgave.sykdig.DigitaliseringsoppgaveKafka
import no.nav.oppgavelytter.oppgave.sykdig.SykDigProducer
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.client.reactive.HttpComponentsClientHttpConnector
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.reactive.function.client.WebClient

@SpringBootApplication
@EnableScheduling
class SykDigBackendApplication

suspend fun main(args: Array<String>) {
    runApplication<SykDigBackendApplication>(*args)

    val environmentVariables = EnvironmentVariables()
    val applicationState = ApplicationState()

    val webClient = createWebClient()
    val accessTokenClient =
        AccessTokenClient(
            aadAccessTokenUrl = environmentVariables.aadAccessTokenUrl,
            clientId = environmentVariables.clientId,
            clientSecret = environmentVariables.clientSecret,
            webClient = webClient,
        )

    val safGraphQlClient =
        SafGraphQlClient(
            webClient = webClient,
            basePath = "${environmentVariables.safUrl}/graphql",
            graphQlQuery =
                SafGraphQlClient::class
                    .java
                    .getResource("/graphql/findJournalpost.graphql")!!
                    .readText()
                    .replace(Regex("[\n\t]"), ""),
        )

    val safJournalpostService =
        SafJournalpostService(
            safGraphQlClient = safGraphQlClient,
            accessTokenClient = accessTokenClient,
            scope = environmentVariables.safScope,
        )
    val oppgaveClient =
        OppgaveClient(
            url = environmentVariables.oppgaveUrl,
            accessTokenClient = accessTokenClient,
            webClient = webClient,
            scope = environmentVariables.oppgaveScope,
        )
    val sykDigProducer =
        SykDigProducer(
            KafkaProducer<String, DigitaliseringsoppgaveKafka>(
                KafkaUtils.getAivenKafkaConfig("syk-dig-producer")
                    .toProducerConfig(
                        environmentVariables.applicationName,
                        valueSerializer = JacksonKafkaSerializer::class,
                    ),
            ),
            environmentVariables.sykDigTopic,
        )

    val oppgaveConsumer =
        OppgaveConsumer(
            oppgaveTopic = environmentVariables.oppgaveTopic,
            kafkaConsumer = getKafkaConsumer(),
            oppgaveService =
                OppgaveService(
                    oppgaveClient,
                    safJournalpostService,
                    sykDigProducer,
                    environmentVariables.cluster,
                ),
            applicationState = applicationState,
        )

    oppgaveConsumer.startConsumer()
}

fun createWebClient(): WebClient {
    val apacheHttpClient =
        HttpClients.custom()
            .build()

    val clientConnector = HttpComponentsClientHttpConnector(apacheHttpClient)

    return WebClient.builder()
        .clientConnector(clientConnector)
        .baseUrl("https://www.nav.no")
        .defaultHeader("Content-Type", "application/json")
        .build()
}

fun HttpComponentsClientHttpConnector(apacheHttpClient: CloseableHttpClient?): HttpComponentsClientHttpConnector {
    return HttpComponentsClientHttpConnector(apacheHttpClient)
}

private fun getKafkaConsumer(): KafkaConsumer<String, OppgaveKafkaAivenRecord> {
    val kafkaConsumer =
        KafkaConsumer(
            KafkaUtils.getAivenKafkaConfig("oppgave-consumer")
                .also {
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 50
                }
                .toConsumerConfig(
                    "syk-dig-oppgavelytter-consumer",
                    JacksonKafkaDeserializer::class,
                ),
            StringDeserializer(),
            JacksonKafkaDeserializer(OppgaveKafkaAivenRecord::class),
        )
    return kafkaConsumer
}

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = true,
)
