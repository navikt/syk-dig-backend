package no.nav.sykdig.config

import com.netflix.graphql.types.errors.TypedGraphQLError
import graphql.GraphQLError
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class CustomDataFetchingExceptionHandler : DataFetcherExceptionHandler {
    override fun handleException(handlerParameters: DataFetcherExceptionHandlerParameters): CompletableFuture<DataFetcherExceptionHandlerResult> {
        return if (handlerParameters.exception is IkkeTilgangException) {
            val debugInfo: MutableMap<String, Any> = HashMap()
            debugInfo["tilgang"] = "false"
            val graphqlError: GraphQLError = TypedGraphQLError.newPermissionDeniedBuilder()
                .message("Innlogget bruker har ikke tilgang")
                .debugInfo(debugInfo)
                .path(handlerParameters.path).build()
            val result: DataFetcherExceptionHandlerResult = DataFetcherExceptionHandlerResult.newResult()
                .error(graphqlError)
                .build()
            CompletableFuture.completedFuture<DataFetcherExceptionHandlerResult>(result)
        } else {
            super.handleException(handlerParameters)
        }
    }
}
