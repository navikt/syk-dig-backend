package no.nav.sykdig.shared.config.kafka

import no.nav.sykdig.utenlandsk.models.CreateSykmeldingKafkaMessage
import no.nav.sykdig.shared.ReceivedSykmelding
import no.nav.sykdig.shared.utils.JacksonKafkaSerializer
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.RETRY_BACKOFF_MS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

@Configuration
class AivenKafkaConfig(
    @Value("\${KAFKA_BROKERS}") private val kafkaBrokers: String,
    @Value("\${KAFKA_SECURITY_PROTOCOL:SSL}") private val kafkaSecurityProtocol: String,
    @Value("\${KAFKA_TRUSTSTORE_PATH}") private val kafkaTruststorePath: String,
    @Value("\${KAFKA_CREDSTORE_PASSWORD}") private val kafkaCredstorePassword: String,
    @Value("\${KAFKA_KEYSTORE_PATH}") private val kafkaKeystorePath: String,
    @Value("\${aiven-kafka.auto-offset-reset}") private val kafkaAutoOffsetReset: String,
) {
    private val javaKeystore = "JKS"
    private val pkcs12 = "PKCS12"

    @Bean
    fun sykmeldingOKProducer(): KafkaProducer<String, ReceivedSykmelding> {
        val configs =
            mapOf(
                KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java,
                ACKS_CONFIG to "all",
                RETRIES_CONFIG to 10,
                RETRY_BACKOFF_MS_CONFIG to 100,
            ) + commonConfig()
        return KafkaProducer<String, ReceivedSykmelding>(configs)
    }

    fun commonConfig() =
        mapOf(
            BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
        ) + securityConfig()

    private fun securityConfig() =
        mapOf(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
            // Disable server host name verification
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to javaKeystore,
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to pkcs12,
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
        )

    @Bean
    fun aivenKafkaListenerContainerFactory(
        aivenKafkaErrorHandler: AivenKafkaErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val config =
            mapOf(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
            ) + commonConfig()
        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(config, StringDeserializer(), StringDeserializer())

        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        factory.setCommonErrorHandler(aivenKafkaErrorHandler)
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        return factory
    }

    @Bean
    fun createSykmeldingKafkaProducer(): KafkaProducer<String, CreateSykmeldingKafkaMessage> {
        val configs =
            mapOf(
                KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java,
                ACKS_CONFIG to "all",
                RETRIES_CONFIG to 10,
                RETRY_BACKOFF_MS_CONFIG to 100,
            ) + commonConfig()
        return KafkaProducer<String, CreateSykmeldingKafkaMessage>(configs)
    }

    @Bean("sykmeldingTopic")
    fun getCreateSykmeldingTopic(): String = "teamsykmelding.opprett-sykmelding"
}

const val SYK_DIG_OPPGAVE_TOPIC = "teamsykmelding.syk-dig-oppgave"
const val OK_SYKMELDING_TOPIC = "teamsykmelding.ok-sykmelding"
