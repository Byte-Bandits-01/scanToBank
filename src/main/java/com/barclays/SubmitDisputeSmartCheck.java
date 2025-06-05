package com.barclays;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class SubmitDisputeSmartCheck {

    private static final String DB_URL = "jdbc:sqlserver://bytebandits.database.windows.net:1433;database=transactions;user=bytebanditsadmin@bytebandits;password=Gulmarg@46;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;";
    private static final String CONSENT_FORM_URL = "https://bytebanditsstorage.blob.core.windows.net/transactions/Transaction_Dispute_Consent_Form.pdf";

    @FunctionName("SubmitDisputeSmartCheck")
    public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS, route = "submit-dispute-smart") 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        try {
            String requestBody = request.getBody().orElseThrow(() -> new RuntimeException("Empty body"));
            JSONObject body = new JSONObject(requestBody);

            JSONArray transactionIds = body.getJSONArray("transactionIds");
            boolean isFraud = body.getBoolean("isFraud");

            // Generate a unique CaseID for this API call
            String caseId = UUID.randomUUID().toString();

            Connection connection = DriverManager.getConnection(DB_URL);

            for (int i = 0; i < transactionIds.length(); i++) {
                String txnId = transactionIds.getString(i);

                // Insert into bb_dispute
                String insert = "INSERT INTO bb_dispute (Transaction_ID, CreatedTS, Status, Description, CaseID) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement insertStmt = connection.prepareStatement(insert)) {
                    insertStmt.setString(1, txnId);
                    insertStmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                    insertStmt.setString(3, "RECEIVED");
                    insertStmt.setString(4, isFraud ? "Marked as fraud by user" : "Marked as valid");
                    insertStmt.setString(5, caseId); // Use the generated CaseID
                    insertStmt.executeUpdate();
                }
            }

            connection.close();

            if (!isFraud) {
                // If isFraud is false, return only the CaseID in the response
                JSONObject response = new JSONObject();
                response.put("CaseID", caseId);

                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(response.toString())
                    .build();
            }

            // If isFraud is true, include documentRequired in the response
            boolean needsAttestation = true; // Set attestation requirement based on fraud status

            JSONArray docArray = new JSONArray();
            JSONObject doc = new JSONObject();
            doc.put("docName", "Desclaimer Form");
            doc.put("url", CONSENT_FORM_URL);
            doc.put("needsAttestation", needsAttestation);

            docArray.put(doc);

            JSONObject response = new JSONObject();
            response.put("documentRequired", docArray);
            response.put("CaseID", caseId); // Include CaseID in the response

            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(response.toString())
                .build();

        } catch (Exception e) {
            context.getLogger().severe("Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage())
                .build();
        }
    }
}