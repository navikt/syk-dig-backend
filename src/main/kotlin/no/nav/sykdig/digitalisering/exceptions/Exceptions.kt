package no.nav.sykdig.digitalisering.exceptions

import graphql.GraphQLException
import no.nav.sykdig.digitalisering.sykmelding.ValidationResult

class IkkeTilgangException(override val message: String) : GraphQLException(message)

class ClientException(override val message: String) : GraphQLException(message)

class MappingException(override val message: String) : Exception(message)

class NoOppgaveException(override val message: String) : RuntimeException(message)

class SykmelderNotFoundException(message: String) : RuntimeException(message)

class MissingJournalpostException(message: String) : RuntimeException(message)

class UnauthorizedException(message: String) : Exception(message)

class ValidationException(override val message: String): Exception(message)
