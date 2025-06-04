// package com.barclays;

// import com.microsoft.azure.functions.*;
// import com.microsoft.azure.functions.annotation.*;

// import java.io.*;
// import java.sql.*;
// import java.util.*;
// import org.apache.commons.fileupload.*;
// import org.apache.commons.fileupload.disk.*;
// import org.apache.commons.fileupload.servlet.*;

// import com.azure.storage.blob.*;
// import com.azure.storage.blob.models.*;

// public class SubmitDisputeWithFileFunction {
//     @FunctionName("SubmitDisputeWithFile")
//     public HttpResponseMessage run(
//         @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION, 
//                      dataType = "binary") HttpRequestMessage<Optional<byte[]>> request,
//         final ExecutionContext context) {

//         try {
//             // Parse multipart form data
//             DiskFileItemFactory factory = new DiskFileItemFactory();
//             ServletFileUpload upload = new ServletFileUpload(factory);
//             List<FileItem> items = upload.parseRequest(new ByteArrayInputStream(request.getBody().orElse(new byte[0])));

//             String custId = null, txnId = null, reason = null;
//             FileItem fileItem = null;

//             for (FileItem item : items) {
//                 if (item.isFormField()) {
//                     switch (item.getFieldName()) {
//                         case "cust_id": custId = item.getString(); break;
//                         case "txn_id": txnId = item.getString(); break;
//                         case "reason": reason = item.getString(); break;
//                     }
//                 } else {
//                     fileItem = item;
//                 }
//             }

//             // Upload to Azure Blob
//             String connectStr = System.getenv("AzureWebJobsStorage");
//             BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectStr).buildClient();
//             BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient("disputes");
//             if (!containerClient.exists()) containerClient.create();

//             BlobClient blobClient = containerClient.getBlobClient(fileItem.getName());
//             blobClient.upload(fileItem.getInputStream(), fileItem.getSize(), true);
//             String blobUrl = blobClient.getBlobUrl();

//             // Insert into SQL DB
//             String dbUrl = System.getenv("SQL_CONN_STRING");
//             try (Connection conn = DriverManager.getConnection(dbUrl)) {
//                 String sql = "INSERT INTO disputes (cust_id, txn_id, reason, file_url, timestamp) VALUES (?, ?, ?, ?, GETDATE())";
//                 PreparedStatement stmt = conn.prepareStatement(sql);
//                 stmt.setString(1, custId);
//                 stmt.setString(2, txnId);
//                 stmt.setString(3, reason);
//                 stmt.setString(4, blobUrl);
//                 stmt.executeUpdate();
//             }

//             return request.createResponseBuilder(HttpStatus.OK).body("Dispute submitted successfully.").build();

//         } catch (Exception e) {
//             context.getLogger().severe("Error: " + e.getMessage());
//             return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
//                 .body("Error submitting dispute: " + e.getMessage()).build();
//         }
//     }
// }
