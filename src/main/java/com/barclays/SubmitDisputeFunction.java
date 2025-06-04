package com.barclays;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.util.Optional;

public class SubmitDisputeFunction {
    @FunctionName("SubmitDisputeFunction")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.GET, HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        String name = request.getQueryParameters().get("name");
        String body = request.getBody().orElse("No body");

        return request.createResponseBuilder(HttpStatus.OK)
            .body("Hello " + (name != null ? name : body))
            .build();
    }
}
