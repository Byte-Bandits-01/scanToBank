package com.barclays;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.util.*;

public class GetDisputeListFunction {

    private static final String DB_URL = "jdbc:sqlserver://bytebandits.database.windows.net:1433;database=transactions;user=bytebanditsadmin@bytebandits;password=Gulmarg@46;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;";

    @FunctionName("GetDisputeList")
    public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "get-dispute-list") 
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            String query = "SELECT Id, Transaction_ID, FileURL, CreatedTS, Description, Status, CaseID FROM bb_dispute";
            JSONArray disputes = new JSONArray();

            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    JSONObject dispute = new JSONObject();
                    dispute.put("Id", rs.getInt("Id"));
                    dispute.put("Transaction_ID", rs.getString("Transaction_ID"));
                    dispute.put("FileURL", rs.getString("FileURL"));
                    dispute.put("CreatedTS", rs.getTimestamp("CreatedTS").toString());
                    dispute.put("Description", rs.getString("Description"));
                    dispute.put("Status", rs.getString("Status"));
                    dispute.put("CaseID", rs.getString("CaseID"));

                    disputes.put(dispute);
                }
            }

            JSONObject response = new JSONObject();
            response.put("disputes", disputes);

            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(response.toString())
                .build();

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
