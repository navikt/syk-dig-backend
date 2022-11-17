package no.nav.sykdig.digitalisering.exceptions

import graphql.GraphQLException

class IkkeTilgangException(override val message: String) : GraphQLException(message)

class ClientException(override val message: String) : GraphQLException(message)

class MappingException(override val message: String) : Exception(message)
