package com.barclays;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Optional;

public class GenerativeAIChatFunction {

    private static final String ENDPOINT = "https://bh-in-openai-bytebandits.openai.azure.com/openai/deployments/gpt-4o/chat/completions?api-version=2025-01-01-preview";
    private static final String API_KEY = "2836495ad41f417c82a60daadd0ba9e6";

    @FunctionName("GenerativeAIChatFunction")
    public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        String chat = request.getQueryParameters().get("chat");
        if (chat == null || chat.isEmpty()) {
            context.getLogger().warning("Missing 'chat' parameter in the request.");
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Missing 'chat' parameter")
                .build();
        }

        context.getLogger().info("Received chat request: " + chat);

        // Prepare the request body for the GPT API
        JSONObject message = new JSONObject();
        message.put("role", "user"); // Specify the role
        message.put("content", chat);

        JSONArray messages = new JSONArray();
        messages.put(message);

        JSONObject requestBodyJson = new JSONObject();
        requestBodyJson.put("messages", messages);
        requestBodyJson.put("max_tokens", 256);

        OkHttpClient client = new OkHttpClient();
        Request gptRequest = new Request.Builder()
            .url(ENDPOINT)
            .addHeader("api-key", API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBodyJson.toString(), MediaType.parse("application/json")))
            .build();

        try (Response response = client.newCall(gptRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorMessage = "GPT API call failed with status code: " + response.code();
                context.getLogger().severe(errorMessage);
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorMessage)
                    .build();
            }

            // Log and return the GPT API response
            String responseBody = response.body().string();
            context.getLogger().info("GPT API response: " + responseBody);
            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(responseBody)
                .build();

        } catch (IOException e) {
            String errorMessage = "Error while calling GPT API: " + e.getMessage();
            context.getLogger().severe(errorMessage);
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorMessage)
                .build();
        }
    }
}
