package com.barclays;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
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
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Missing 'chat' parameter")
                    .build();
        }
        OkHttpClient client = new OkHttpClient();

        context.getLogger().fine("Received chat request: " + chat);

        JSONObject message = new JSONObject();
        message.put("content", chat);

        JSONArray messages = new JSONArray();
        messages.put(message);

        JSONObject requestBodyJson = new JSONObject();
        requestBodyJson.put("messages", messages);
        requestBodyJson.put("max_tokens", 256);

        Request gptRequest = new Request.Builder()
                .url(ENDPOINT)
                .addHeader("api-key", API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBodyJson.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(gptRequest).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response + ": " + response.body().string());
            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(response.body().string())
                    .build();

        } catch (IOException e) {
            context.getLogger().severe("GPT Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Database connection failed.")
                    .build();
        }

    }
}
