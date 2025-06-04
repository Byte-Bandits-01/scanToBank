package com.barclays;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

import java.sql.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class GetTransectionHistoryFunction {
    @FunctionName("GetTransactionHistoryFunction")
    public HttpResponseMessage run(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        String cis = request.getQueryParameters().get("CIS");
        if (cis == null || cis.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("Missing 'CIS' parameter")
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

        JSONArray transactionList = new JSONArray();

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            String sql = "SELECT transaction_id, date, currency, sender, receiver, amount, fee, type, CIS FROM bb_transaction_history WHERE CIS = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, cis);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    JSONObject obj = new JSONObject();
                    obj.put("transaction_id", rs.getString("transaction_id"));
                    obj.put("date", rs.getString("date"));
                    obj.put("currency", rs.getString("currency"));
                    obj.put("sender", rs.getString("sender"));
                    obj.put("receiver", rs.getString("receiver"));
                    obj.put("amount", rs.getString("amount"));
                    obj.put("fee", rs.getString("fee"));
                    obj.put("type", rs.getString("type"));
                    obj.put("CIS", rs.getString("CIS"));
                    transactionList.put(obj);
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
            .body(transactionList.toString())
            .build();
    }
}