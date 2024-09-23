package no.nav.sykdig.config

import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class CustomDataFetchingExceptionHandler : DataFetcherExceptionHandler {
    val logger = applog()

    override fun handleException(handlerParameters: DataFetcherExceptionHandlerParameters): CompletableFuture<DataFetcherExceptionHandlerResult> {
        // When handling the exceptions on for GQL, because we are hiding any internal exceptions from the client, we need to log them
        return when (handlerParameters.exception) {
            is ClientException -> {
                logger.warn(handlerParameters.exception.message, handlerParameters.exception)
                val graphqlError =
                    TypedGraphQLError.newBuilder()
                        .message(handlerParameters.exception.message)
                        .build()
                val result: DataFetcherExceptionHandlerResult =
                    DataFetcherExceptionHandlerResult.newResult()
                        .error(graphqlError)
                        .build()

                CompletableFuture.completedFuture(result)
            }
            is AccessDeniedException -> {
                logger.warn(handlerParameters.exception.message, handlerParameters.exception)
                handleNoAccess(handlerParameters)
            }
            is IkkeTilgangException -> {
                logger.warn(handlerParameters.exception.message, handlerParameters.exception)
                handleNoAccess(handlerParameters)
            }

            else -> {
                logger.error(handlerParameters.exception.message, handlerParameters.exception)
                val graphqlError =
                    TypedGraphQLError.newInternalErrorBuilder()
                        .message("En ukjent feil har oppstått. Vennligst prøv igjen senere.")
                        .build()
                val result: DataFetcherExceptionHandlerResult =
                    DataFetcherExceptionHandlerResult.newResult()
                        .error(graphqlError)
                        .build()

                CompletableFuture.completedFuture(result)
            }
        }
    }

    private fun handleNoAccess(handlerParameters: DataFetcherExceptionHandlerParameters): CompletableFuture<DataFetcherExceptionHandlerResult> {
        val debugInfo: MutableMap<String, Any> = HashMap()
        debugInfo["tilgang"] = "false"
        val graphqlError: GraphQLError =
            TypedGraphQLError.newPermissionDeniedBuilder()
                .message("Innlogget bruker har ikke tilgang")
                .debugInfo(debugInfo)
                .path(handlerParameters.path).build()
        val result: DataFetcherExceptionHandlerResult =
            DataFetcherExceptionHandlerResult.newResult()
                .error(graphqlError)
                .build()
        return CompletableFuture.completedFuture(result)
    }
}
