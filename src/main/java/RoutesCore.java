import org.bson.Document;
import org.bson.types.Binary;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoutesCore {

    private static final Logger logger = LoggerFactory.getLogger(RoutesCore.class);

    private final static String ERR_STATUS = "Error";
    private final static String SUCC_STATUS = "Success";
    private final static String MSG_BAD = "Bad request";
    private final static String MSG_SAVE = "Data saved successfully";
    private final static String MSG_GET_SUCC = "Data get successfully";
    private final static int STATUSCODE_SUCC = 200;
    private final static int STATUSCODE_BAD = 400;
    private final static int STATUSCODE_ERR = 404;


    private static String createResponse(Response _res, int _resStatus ,String _status, String _message, Object _data) {
        _res.status(_resStatus);
        JSONObject response = new JSONObject();
        response.put("status", _status);
        response.put("message", _message);
        response.put("data", _data);
        return response.toString();
    }

//    savaData RAW JSON or MULTIPART
    public static String saveData(Request req, Response res) throws ServletException, IOException {
        String contentType = req.contentType();
        Document mainDoc;
        String manutenzioneID = "";
        List<String> uploadedFileNames = new ArrayList<>();
        List<Document> embeddedFiles = new ArrayList<>();

        if (contentType != null && contentType.contains("application/json")) {
            // Embedded device (Arduino/ESP32) raw JSON
            res.type("application/json");

            // Receive JSON from request
            String body = req.body();
            mainDoc = Document.parse(body);
            logger.debug("JSON ricevuto: {}" , mainDoc.toJson());

        } else if (contentType != null && contentType.contains("multipart/form-data")) {

            // Web/PC client: multipart handle
            req.raw().setAttribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));

            String jsonBody = req.raw().getParameter("json");

            try {
                mainDoc = Document.parse(jsonBody);
            } catch (Exception e) {
                return createResponse(res, STATUSCODE_BAD,"error", "Bad Request", "Invalid JSON body");
            }

            manutenzioneID = mainDoc.getString("manutenzioneID");
            List<Part> fileParts = req.raw().getParts().stream()
                    .filter(part -> "file".equals(part.getName()))
                    .collect(Collectors.toList());

            File uploadsDir = new File("uploads");
            if (!uploadsDir.exists()) uploadsDir.mkdir();

            for (Part filePart : fileParts) {
                String fileName = UUID.randomUUID() + "_" + filePart.getSubmittedFileName();
                long fileSize = filePart.getSize();

                if (fileSize > 5 * 1024 * 1024) {
                    return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "File too large (max 5MB)");
                }

                File savedFile = new File(uploadsDir, fileName);

                InputStream input = filePart.getInputStream();
                Files.copy(input, savedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                byte[] contentBytes = Files.readAllBytes(savedFile.toPath());

                Document embedded = new Document("name", fileName)
                        .append("size", fileSize)
                        .append("contentType", filePart.getContentType())
                        .append("data", new Binary(contentBytes));

                embeddedFiles.add(embedded);
                uploadedFileNames.add(fileName);
                savedFile.delete(); //clean tmp file
            }
        } else {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "Use application/json or multipart/form-data");
        }
        String schemaID = (String) mainDoc.get("schemaID");
        if (schemaID == null) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "schemaID null");
        }

        String getCompanyID = (String) MongoDBConnection.getJsonKey(mainDoc, "companyID");
        if (getCompanyID == null) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "companyID missing");
        }
        boolean iDfound = MongoDBConnection.checkCompanyID(getCompanyID);
        if (!iDfound) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "Invalid companyID");
        }
        if (manutenzioneID == null || manutenzioneID.isEmpty()) {
            manutenzioneID = null;
        } else {
            iDfound = MongoDBConnection.checkID(manutenzioneID);
            if (!iDfound) {
                return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "No entry found with manutenzioneID used");
            }
            String reqToken = (String) mainDoc.get("token");
            boolean authorization = false;

            if (reqToken == null || reqToken.isEmpty())
            {
                return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "Missing token");
            }

            authorization = MongoDBConnection.maintenanceAttachAuth(manutenzioneID, getCompanyID, reqToken);

            if (!authorization)
            {
                return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "Impossible attach maintenance, wrong token.");
            }
        }

        //Now the mainDoc is completed and available to be checked with schema
        Document schemaDoc = MongoDBConnection.checkSchemaID(schemaID);
        if (schemaDoc == null)
        {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "Schema not found");
        }
        mainDoc.remove("token");
        String docValidated = MongoDBConnection.documentValidated(schemaDoc, mainDoc);
        if (!docValidated.contains("success"))
        {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, "Bad Request", docValidated);
        }

        mainDoc.append("upload_time", System.currentTimeMillis());
        if (uploadedFileNames.isEmpty())
        {
            return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, MSG_SAVE, MongoDBConnection.storeIncomingJson(mainDoc.toJson()));
        }
        mainDoc.append("files", embeddedFiles);
        String response = MongoDBConnection.storeIncomingJson(mainDoc.toJson());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("id", response);
        responseData.put("uploadedFiles", uploadedFileNames);

        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, MSG_SAVE, responseData);
    }

//    getData from DB with filters: type, companyID, timeFrom, timeTo, limit
    public static String getData(Request req, Response res){
        res.type("application/json");
        String type = req.queryParams("type");
        boolean mustContainFiles = false;
        String limitStr = req.queryParams("limit");
        String companyID = req.queryParams("companyID");     //companyID field in db
        String timeFromStr = req.queryParams("timeFrom");    //upload_time field in db
        String timeToStr = req.queryParams("timeTo");        //upload_time field in db
        long timeFrom = 0;
        long timeTo = 0;

        if (timeFromStr != null && !timeFromStr.isEmpty() &&
                timeToStr != null && !timeToStr.isEmpty()) {
            try {
                timeFrom = Long.parseLong(timeFromStr);
                timeTo = Long.parseLong(timeToStr);

                if (timeFrom > timeTo) {
                    return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "timeFrom must < timeTo");
                }
            } catch (NumberFormatException e) {
                return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "Time format must be numeric");
            }
        }

        if (type == null || type.isEmpty()) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "type null or empty");
        }
        if (!type.equalsIgnoreCase("rilevazioni") && !type.equalsIgnoreCase("manutenzioni")) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS , MSG_BAD, "Use rilevazioni or manutenzioni");
        }
        if (type.equalsIgnoreCase("rilevazioni")) {
            mustContainFiles = false;
        } else if (type.equalsIgnoreCase("manutenzioni")) {
            mustContainFiles = true;
        }
        if(limitStr == null || limitStr.isEmpty()) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "limit null or empty");
        }
        int limit = Integer.parseInt(limitStr);
        if(limit > 500 || limit <= 0){
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "limit must be between 1 and 500");
        }
        if(companyID == null || companyID.isEmpty()){
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "companyID null or empty");
        }
        boolean companyIDExists = MongoDBConnection.checkCompanyID(companyID);
        if (companyID == null || companyID.isEmpty()) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "companyID missing");
        }
        if (!companyIDExists) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "companyID does not exists");
        }
        String entries = MongoDBConnection.getFilteredRecords(companyID, mustContainFiles, timeFrom, timeTo, limit);
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, MSG_GET_SUCC, new JSONArray(entries));
    }

//    return blockchain block number of ID sent - if present
    public static String getValidationID(Request req, Response res) {
        res.type("application/json");
        // Take ID from query params
        String id = req.queryParams("id");
        if (id == null || id.isEmpty()) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "ID null or empty");
        }

        // Retrieve data from MongoDB with ID
        String result = MongoDBConnection.getBlockFromID(id);
        if (result.contains("error")) {
            return createResponse(res, STATUSCODE_ERR, ERR_STATUS, ERR_STATUS, "No ID found");
        }
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, MSG_GET_SUCC, result);
    }

//    return blockchain block number of JSON sent - if present
    public static String getValidationJson(Request req, Response res){
        res.type("application/json");
        String body = req.body();
        JSONObject json = new JSONObject(body);
        String jsonString = json.toString();

        String result = MongoDBConnection.getValidationJson(jsonString);
        if (result.contains("error")) {
            return createResponse(res, STATUSCODE_ERR, ERR_STATUS, ERR_STATUS, result);
        }
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, MSG_GET_SUCC, result);
    }

//    return JSON stored in DB with ID sent - if present
    public static String getJsonFromID(Request req, Response res){
        res.type("application/json");
        String id = req.queryParams("id");
        if (id == null || id.isEmpty()) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "ID null or empty");
        }

        String result = MongoDBConnection.getJsonFromID(id);
        if (result.contains("error")) {
            return createResponse(res, STATUSCODE_ERR, ERR_STATUS, ERR_STATUS, result);
        }
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, MSG_GET_SUCC, new JSONArray(result));
    }

//    return JSON stored in DB from search of field and value sent - if present
    public static String search(Request req, Response res){
        res.type("application/json");
        String field = req.queryParams("field");
        String value = req.queryParams("value");

        if (field == null || value == null) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "field/value empty or null");
        }

        String result = MongoDBConnection.searchEntries(field, value);

        if (result.contains("error")) {
            return createResponse(res, STATUSCODE_ERR, ERR_STATUS, ERR_STATUS, result);
        }
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, MSG_GET_SUCC, new JSONArray(result));
    }

               /* **********************************
                    REMOVE BEFORE PRODUCTION
                **********************************/

    public static String getDataTEST(Request req, Response res){
        res.type("application/json");
        String limitCheck = req.queryParams("limit");
        if(limitCheck == null || limitCheck.isEmpty()) {
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "limit null or empty");
        }
        int limit = Integer.parseInt(req.queryParams("limit"));
        if(limit > 500 || limit <= 0){
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "limit must be between 1 and 500");
        }
        String entries = MongoDBConnection.allEntriesTEST(limit);
        if (entries == null || entries.isEmpty()) {
            return createResponse(res, STATUSCODE_ERR, ERR_STATUS, ERR_STATUS, "No Data");
        }
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, MSG_GET_SUCC, new JSONArray(entries));
    }

    public static String deleteLuce(Request req, Response res){
        res.type("application/json");

        long deletedEntries = 0;
        String code = req.queryParams("code");
        if (code == null || code.isEmpty()) {
            res.status(400);
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "code null or empty");
        }

        if(code.equals("luce")){
            deletedEntries = MongoDBConnection.deleteLuce();
        }else{
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, "Invalid code");
        }
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, "Data deleted correctly", deletedEntries);
    }

    public static String addSchema(Request req, Response res){
        res.type("application/json");
        Document mainDoc;
        String body = req.body();
        mainDoc = Document.parse(body);
        String response = MongoDBConnection.addSchema(mainDoc.toJson());
        if (response.contains("error"))
            return createResponse(res, STATUSCODE_BAD, ERR_STATUS, MSG_BAD, response);

        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, "Schema added successfully", response);
    }

    public static String getSchema(Request req, Response res){
        res.type("application/json");
        String schema = MongoDBConnection.getSchema();
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS , "Schema retrieved successfully", new JSONArray(schema));
    }

    public static String deleteSchema(Request req, Response res){
        res.type("application/json");
        String id = req.queryParams("id");

        boolean deleted = MongoDBConnection.deleteSchema(id);
        if (!deleted) {
            return createResponse(res, STATUSCODE_ERR, ERR_STATUS, ERR_STATUS, "Schema not found");
        }
        return createResponse(res, STATUSCODE_SUCC, SUCC_STATUS, SUCC_STATUS, "Schema deleted");
    }

}
