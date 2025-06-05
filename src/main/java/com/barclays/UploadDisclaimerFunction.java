package com.barclays;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
import org.json.JSONObject;

import java.io.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public class UploadDisclaimerFunction {

    private static final String STORAGE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=bytebanditsstorage;AccountKey=r5bvBjcs8Pmq92hEtr1D0FEAsruoxTu5JbcR1sCrH1RRpN/V6+ui9fEKQPBbdHJFTBTB1PBKbcCt+AStP2Gm6g==;EndpointSuffix=core.windows.net";
    private static final String CONTAINER_NAME = "casefiles";
    private static final String JDBC_URL = "jdbc:sqlserver://bytebandits.database.windows.net:1433;database=transactions;user=bytebanditsadmin@bytebandits;password=Gulmarg@46;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;";

    @FunctionName("upload-disclaimer")
    public HttpResponseMessage run(
        @HttpTrigger(
            name = "req",
            methods = {HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS,
            dataType = "binary",
            route = "upload-disclaimer")
        HttpRequestMessage<Optional<byte[]>> request,
        final ExecutionContext context) {

        try {
            String caseId = request.getQueryParameters().get("CaseID");
            if (caseId == null || caseId.isEmpty()) {
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "Missing 'CaseID' parameter");
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .header("Content-Type", "application/json")
                    .body(errorResponse.toString())
                    .build();
            }

            // Validate CaseID exists in bb_dispute
            try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
                String validateQuery = "SELECT COUNT(*) AS count FROM bb_dispute WHERE CaseID = ?";
                try (PreparedStatement validateStmt = connection.prepareStatement(validateQuery)) {
                    validateStmt.setString(1, caseId);
                    ResultSet rs = validateStmt.executeQuery();
                    if (rs.next() && rs.getInt("count") == 0) {
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("error", "Invalid 'CaseID': No matching record found in bb_dispute");
                        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                            .header("Content-Type", "application/json")
                            .body(errorResponse.toString())
                            .build();
                    }
                }

                // Upload file to Azure Blob Storage
                BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(STORAGE_CONNECTION_STRING)
                    .buildClient();
                BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(CONTAINER_NAME);
                if (!containerClient.exists()) {
                    containerClient.create();
                }

                String fileName = "disclaimer_" + caseId + "_" + Instant.now().toEpochMilli() + ".pdf";
                BlobClient blobClient = containerClient.getBlobClient(fileName);
                blobClient.upload(new ByteArrayInputStream(request.getBody().orElse(new byte[0])), request.getBody().orElse(new byte[0]).length, true);

                String fileUrl = blobClient.getBlobUrl(); // Get the file URL

                // Update FileURL in bb_dispute for all rows with the given CaseID
                String updateQuery = "UPDATE bb_dispute SET FileURL = ? WHERE CaseID = ?";
                try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                    updateStmt.setString(1, fileUrl);
                    updateStmt.setString(2, caseId);
                    updateStmt.executeUpdate();
                }

                // Prepare JSON response
                JSONObject successResponse = new JSONObject();
                successResponse.put("message", "File uploaded successfully");
                successResponse.put("fileUrl", fileUrl);

                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(successResponse.toString())
                    .build();
            }

        } catch (Exception e) {
            context.getLogger().severe("Error: " + e.getMessage());
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Internal server error");
            errorResponse.put("details", e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(errorResponse.toString())
                .build();
        }
    }
}
