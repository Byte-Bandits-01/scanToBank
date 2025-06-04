package com.barclays;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.azure.storage.blob.*;
import com.azure.storage.blob.models.*;
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
            authLevel = AuthorizationLevel.FUNCTION,
            dataType = "binary",
            route = "upload-disclaimer")
        HttpRequestMessage<Optional<byte[]>> request,
        final ExecutionContext context) {

        try {
            String caseId = request.getQueryParameters().get("CaseID");
            if (caseId == null || caseId.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Missing 'CaseID' parameter")
                    .build();
            }

            // Validate CaseID exists in bb_dispute
            try (Connection connection = DriverManager.getConnection(JDBC_URL)) {
                String validateQuery = "SELECT COUNT(*) AS count FROM bb_dispute WHERE CaseID = ?";
                try (PreparedStatement validateStmt = connection.prepareStatement(validateQuery)) {
                    validateStmt.setString(1, caseId);
                    ResultSet rs = validateStmt.executeQuery();
                    if (rs.next() && rs.getInt("count") == 0) {
                        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                            .body("Invalid 'CaseID': No matching record found in bb_dispute")
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

                // Insert record into bb_casefile
                String insertQuery = "INSERT INTO bb_casefile (ID, CaseID, FileURL) VALUES (?, ?, ?)";
                try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                    int id = generateUniqueId(connection); // Generate a unique ID
                    insertStmt.setInt(1, id); // Set the ID
                    insertStmt.setString(2, caseId);
                    insertStmt.setString(3, fileUrl);
                    insertStmt.executeUpdate();
                }

                // Return the file URL in the response
                return request.createResponseBuilder(HttpStatus.OK)
                    .body("File uploaded successfully. File URL: " + fileUrl)
                    .build();
            }

        } catch (Exception e) {
            context.getLogger().severe("Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Helper method to generate a unique ID for the bb_casefile table.
     */
    private int generateUniqueId(Connection connection) throws SQLException {
        String query = "SELECT MAX(ID) AS maxId FROM bb_casefile";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("maxId") + 1; // Increment the maximum ID
            }
        }
        return 1; // Start with 1 if the table is empty
    }
}
