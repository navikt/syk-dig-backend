package no.nav.sykdig

import com.netflix.graphql.dgs.context.GraphQLContextContributor
import com.netflix.graphql.dgs.internal.DgsRequestData
import graphql.GraphQLContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("test")
class TestGraphQLContextContributor : GraphQLContextContributor {
    override fun contribute(
        builder: GraphQLContext.Builder,
        extensions: Map<String, Any>?,
        requestData: DgsRequestData?,
    ) {
        builder.put("username", "fake-test-ident")
    }
}
