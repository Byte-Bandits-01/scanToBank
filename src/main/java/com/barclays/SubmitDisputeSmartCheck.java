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

            List<String> falseClaimTxns = new ArrayList<>();
            JSONArray caseIds = new JSONArray(); // Collect CaseIDs for response
            Connection connection = DriverManager.getConnection(DB_URL);

            for (int i = 0; i < transactionIds.length(); i++) {
                String txnId = transactionIds.getString(i);

                // Validate transaction amount
                String query = "SELECT amount FROM bb_transaction_history WHERE Transaction_ID = ?";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, txnId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        String amountStr = rs.getString("amount"); // Retrieve as string
                        double amount = Double.parseDouble(amountStr.replaceAll("[^0-9.]", "")); // Convert to double after removing non-numeric characters
                        if (amount > 50) {
                            falseClaimTxns.add(txnId);
                        }
                    }
                }

                // Generate CaseID and Insert into bb_dispute
                String caseId = truncateString(UUID.randomUUID().toString(), 20); // Generate CaseID
                String insert = "INSERT INTO bb_dispute (ID, Transaction_ID, CaseID, CreatedTS, Status, Description) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement insertStmt = connection.prepareStatement(insert)) {
                    int id = generateUniqueId(connection); // Generate a unique ID
                    insertStmt.setInt(1, id); // Set the ID
                    insertStmt.setString(2, txnId);
                    insertStmt.setString(3, caseId); // Set the CaseID
                    insertStmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                    insertStmt.setString(5, "RECEIVED");
                    insertStmt.setString(6, isFraud ? "Marked as fraud by user" : "Marked as valid");
                    insertStmt.executeUpdate();
                }

                caseIds.put(caseId); // Add CaseID to response
            }

            connection.close();

            boolean needsAttestation = !falseClaimTxns.isEmpty();

            JSONArray docArray = new JSONArray();
            JSONObject doc = new JSONObject();
            doc.put("docName", "consentForm");
            doc.put("url", needsAttestation ? CONSENT_FORM_URL : "");

            JSONObject flag = new JSONObject();
            flag.put("needsAttestation", needsAttestation);

            docArray.put(doc);
            docArray.put(flag);

            JSONObject response = new JSONObject();
            response.put("documentRequired", docArray);
            response.put("caseIds", caseIds); // Include CaseIDs in response

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

    /**
     * Helper method to truncate a string to a specified length.
     * @param value The string to truncate.
     * @param maxLength The maximum length of the string.
     * @return The truncated string.
     */
    private String truncateString(String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            return value.substring(0, maxLength);
        }
        return value;
    }

    /**
     * Generate a unique ID for the dispute record.
     * @param connection The database connection.
     * @return A unique ID.
     * @throws SQLException If a database access error occurs.
     */
    private int generateUniqueId(Connection connection) throws SQLException {
        String query = "SELECT COALESCE(MAX(ID), 0) + 1 AS next_id FROM bb_dispute";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("next_id") : 1;
        }
    }
}