package com.barclays;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class GetDisputesFunction {
    @FunctionName("GetDisputesFunction")
    public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.FUNCTION)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        String custId = request.getQueryParameters().get("cust_id");
        if (custId == null || custId.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Missing 'cust_id' parameter")
                .build();
        }

        String jdbcUrl = "jdbc:sqlserver://bytebandits.database.windows.net:1433;"
                + "database=transactions;"
                + "user=bytebanditsadmin@bytebandits;"
                + "password=Gulmarg@46;"
                + "encrypt=true;"
                + "trustServerCertificate=false;"
                + "hostNameInCertificate=*.database.windows.net;"
                + "loginTimeout=30;";

        JSONArray disputeList = new JSONArray();

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            String sql = "SELECT dispute_id, transaction_id, reason, status, created_at FROM disputes WHERE cust_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, custId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    JSONObject obj = new JSONObject();
                    obj.put("dispute_id", rs.getString("dispute_id"));
                    obj.put("transaction_id", rs.getString("transaction_id"));
                    obj.put("reason", rs.getString("reason"));
                    obj.put("status", rs.getString("status"));
                    obj.put("created_at", rs.getString("created_at"));
                    disputeList.put(obj);
                }
            }

        } catch (SQLException e) {
            context.getLogger().severe("SQL Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Database connection failed.")
                .build();
        }

        return request.createResponseBuilder(HttpStatus.OK)
            .header("Content-Type", "application/json")
            .body(disputeList.toString())
            .build();
    }
}
