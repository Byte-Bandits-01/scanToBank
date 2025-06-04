package com.barclays;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

public class AzureOpenAIRuleEngine {
    private static final String ENDPOINT = "https://bh-in-openai-bytebandits.openai.azure.com/openai/deployments/gpt-4o/chat/completions?api-version=2025-01-01-preview";
    private static final String API_KEY = "2836495ad41f417c82a60daadd0ba9e6";

    public static String evaluateRule(String rule, String data) throws IOException {
        OkHttpClient client = new OkHttpClient();

        String prompt = "Rule: " + rule + "\nData: " + data + "\nShould this transaction be flagged? Answer YES or NO and explain why.";

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);

        JSONArray messages = new JSONArray();
        messages.put(message);

        JSONObject requestBodyJson = new JSONObject();
        requestBodyJson.put("messages", messages);
        requestBodyJson.put("max_tokens", 256);

        Request request = new Request.Builder()
                .url(ENDPOINT)
                .addHeader("api-key", API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBodyJson.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response + ": " + response.body().string());
            return response.body().string();
        }
    }

    public static void main(String[] args) throws IOException {
        String rule = "Flag if amount > 1000 and country is not US";
        String data = "{ \"amount\": 1500, \"country\": \"IN\" }";
        String result = evaluateRule(rule, data);
        System.out.println(result);
    }
}