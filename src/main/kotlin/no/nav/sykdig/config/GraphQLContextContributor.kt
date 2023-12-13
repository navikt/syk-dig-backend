package no.nav.sykdig.config

import com.auth0.jwt.JWT
import com.netflix.graphql.dgs.context.GraphQLContextContributor
import com.netflix.graphql.dgs.internal.DgsRequestData
import graphql.GraphQLContext
import no.nav.sykdig.applog
import org.springframework.stereotype.Component

@Component
class GraphQLContextContributor : GraphQLContextContributor {
    val log = applog()

    override fun contribute(
        builder: GraphQLContext.Builder,
        extensions: Map<String, Any>?,
        requestData: DgsRequestData?,
    ) {
        if (requestData?.headers != null) {
            val authHeader = requestData.headers?.get("Authorization")
            requireNotNull(authHeader) { "Authorization header is required" }

            val token: String = authHeader.first().replace("Bearer ", "")
            val decodedJWT = JWT.decode(token)
            val username = decodedJWT.claims["preferred_username"]?.asString()
            val navIdent = decodedJWT.claims["NAVident"]?.asString()

            requireNotNull(username) { "preferred_username is missing in claims" }

            builder.put("username", username)
            builder.put("nav_ident", navIdent)
        }
    }
}
