package no.nav.oppgavelytter

data class EnvironmentVariables(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syk-dig-oppgavelytter"),
    val oppgaveTopic: String = getEnvVar("OPPGAVE_TOPIC"),
    val aadAccessTokenUrl: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val oppgaveUrl: String = getEnvVar("OPPGAVE_URL"),
    val oppgaveScope: String = getEnvVar("OPPGAVE_SCOPE"),
    val safUrl: String = getEnvVar("SAF_URL"),
    val safScope: String = getEnvVar("SAF_SCOPE"),
    val sykDigTopic: String = "teamsykmelding.syk-dig-oppgave",
    val cloudSqlInstance: String = getEnvVar("CLOUD_SQL_INSTANCE"),
    val dbHost: String = getEnvVar("DB_SYK_DIG_OPPGAVELYTTER_HOST"),
    val dbPort: String = getEnvVar("DB_SYK_DIG_OPPGAVELYTTER_PORT"),
    val dbName: String = getEnvVar("DB_SYK_DIG_OPPGAVELYTTER_DATABASE"),
    val databaseUsername: String = getEnvVar("DB_SYK_DIG_OPPGAVELYTTER_USERNAME"),
    val databasePassword: String = getEnvVar("DB_SYK_DIG_OPPGAVELYTTER_PASSWORD"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
)

fun getEnvVar(
    varName: String,
    defaultValue: String? = null,
) = System.getenv(varName)
    ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
