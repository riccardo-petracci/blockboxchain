import org.bson.types.Binary;
import org.json.JSONArray;
import org.json.JSONObject;
import static spark.Spark.*;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import org.bson.Document;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class Main {

    private static String createResponse(String status, String message, Object data) {
        JSONObject response = new JSONObject();
        response.put("status", status);
        response.put("message", message);
        response.put("data", data);
        return response.toString();
    }

    public static void main(String[] args) {
        try {
            MongoDBConnection.checkInitialization();

            port(Config.getInt("SERVER_PORT"));

            //Save Data blob and json in unique file , handle multipart or json raw in unique end point
            post("/saveData", (req, res) -> {
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
                    System.out.println("JSON ricevuto: " + mainDoc.toJson());

                } else if (contentType != null && contentType.contains("multipart/form-data")) {

                    // Web/PC client: multipart handle
                    req.raw().setAttribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));

//                    String jsonBody = req.queryParams("json");
                    String jsonBody = req.raw().getParameter("json");

                    try {
                        mainDoc = Document.parse(jsonBody);
                    } catch (Exception e) {
                        res.status(400);
                        return createResponse("error", "Bad Request", "Invalid JSON body");
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
                            res.status(400);
                            return createResponse("error", "Bad request", "File too large (max 5MB)");
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
                    res.status(400);
                    return createResponse("error", "Unsupported Content-Type", "use application/json or multipart/form-data");
                }
                String schemaID = (String) mainDoc.get("schemaID");
                if (schemaID == null) {
                    res.status(400);
                    return createResponse("error", "Schema ID missing", "schemaID is null");
                }

                String getCompanyID = (String) MongoDBConnection.getJsonKey(mainDoc, "companyID");
                if (getCompanyID == null) {
                    res.status(400);
                    return createResponse("error", "Bad request", "CompanyID is missing");
                }
                boolean iDfound = MongoDBConnection.checkCompanyID(getCompanyID);
                if (!iDfound) {
                    res.status(400);
                    return createResponse("error", "Bad Request", "Invalid Company ID");
                }
                if (manutenzioneID == null || manutenzioneID.isEmpty()) {
                    manutenzioneID = null;
                } else {
                    iDfound = MongoDBConnection.checkID(manutenzioneID);
                    if (!iDfound) {
                        res.status(400);
                        return createResponse("error", "Bad Request", "ID not found");
                    }
                }

                //Now the mainDoc is completed and available to be checked with schema
                Document schemaDoc = MongoDBConnection.checkSchemaID(schemaID);
                if (schemaDoc == null) {
                    res.status(400);
                    return createResponse("error", "Bad Request", "Schema not found");
                }
                //mainDoc.remove("schemaID"); //se lo schemaID va nel JSON Schema togli questa riga
                String docValidated = MongoDBConnection.documentValidated(schemaDoc, mainDoc);
                if (!docValidated.contains("success")) {
                    res.status(400);
                    return createResponse("error", "Bad Request", docValidated);
                }

                mainDoc.append("upload_time", System.currentTimeMillis());
                mainDoc.append("ref_manutenzioneID", manutenzioneID);
                if (uploadedFileNames.isEmpty())
                    return createResponse("success", "Data Saved", MongoDBConnection.storeIncomingJson(mainDoc.toJson()));

                mainDoc.append("files", embeddedFiles);
                String response = MongoDBConnection.storeIncomingJson(mainDoc.toJson());

                Map<String, Object> responseData = new HashMap<>();
                responseData.put("id", response);
                responseData.put("uploadedFiles", uploadedFileNames);

                res.status(200);
                return createResponse("success", "Data Saved", responseData);
            });


            get("/getData", (req, res) -> {
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
                            res.status(400);
                            return createResponse("error", "Bad request: timeFrom cannot be greater than timeTo", null);
                        }
                    } catch (NumberFormatException e) {
                        res.status(400);
                        return createResponse("error", "Invalid time format: must be numeric", null);
                    }
                }

                if (type == null || type.isEmpty()) {
                    res.status(400);
                    return createResponse("error", "Bad request, type null or empty", null);
                }
                if (!type.equalsIgnoreCase("rilevazioni") && !type.equalsIgnoreCase("manutenzioni")) {
                    res.status(400);
                    return createResponse("error", "Bad request, wrong type. Use rilevazioni or manutenzioni", null);
                }
                if (type.equalsIgnoreCase("rilevazioni")) {
                    mustContainFiles = false;
                } else if (type.equalsIgnoreCase("manutenzioni")) {
                    mustContainFiles = true;
                }
                if(limitStr == null || limitStr.isEmpty()) {
                    res.status(400);
                    return createResponse("error", "Bad request, limit null or empty", null);
                }
                int limit = Integer.parseInt(limitStr);
                if(limit > 500){
                    res.status(400);
                    return createResponse("error", "Bad request: limit too big, use limit lower than 500", null);
                }
                if (limit <= 0) {
                    res.status(400);
                    return createResponse("error", "Bad request: use limit higher than 0", null);
                }
                boolean companyIDExists = MongoDBConnection.checkCompanyID(companyID);
                if (companyID == null || companyID.isEmpty()) {
                    res.status(400);
                    return createResponse("error", "Bad request, companyID not inserted", null);
                }
                if (!companyIDExists) {
                    res.status(404);
                    return createResponse("error", "Company not found", null);
                }
                String entries = MongoDBConnection.getFilteredRecords(companyID, mustContainFiles, timeFrom, timeTo, limit);
                return createResponse("success", "entries retrieved successfully", new JSONArray(entries));
            });


            // Validation from ID
            get("/getValidationID", (req, res) -> {
                res.type("application/json");

                // Take ID from query params
                String id = req.queryParams("id");
                if (id == null || id.isEmpty()) {
                    res.status(400);
                    return createResponse("error", "Bad request, id null or empty", null);
                }

                // Retrieve data from MongoDB with ID
                String result = MongoDBConnection.getBlockFromID(id);
                if (result.contains("blockchain") || result.contains("errore")) {
                    res.status(404);
                    return createResponse("error", "ID not found", result);
                }
                res.status(200);
                return createResponse("success", "ID found", result);
            });


            // Validatation from JSON
            get("/getValidationJson", (req, res) -> {
                res.type("application/json");

                // Receive JSON from GET
                String body = req.body();
                JSONObject json = new JSONObject(body);
                String jsonString = json.toString();

                // Retrieve data from MongoDB with JSON
                String result = MongoDBConnection.getValidationJson(jsonString);
                if (result.contains("error")) {
                    res.status(404);
                    return createResponse("error", "ID not found", result);
                }
                res.status(200);
                return createResponse("success", "ID found", result);
            });


            // GET Json from ID
            get("/getJsonFromID", (req, res) -> {
                res.type("application/json");

                // Take ID from query params
                String id = req.queryParams("id");
                if (id == null || id.isEmpty()) {
                    res.status(400);
                    return createResponse("error", "Bad request, id null or empty", null);
                }

                // Retrieve data from MongoDB with ID JSON
                String result = MongoDBConnection.getJsonFromID(id);
                if (result.contains("error")) {
                    res.status(404);
                    return createResponse("error", "ID not found", result);
                }
                res.status(200);
                return createResponse("success", "ID found", new JSONArray(result));
            });


            // GET ALL n last entries without specify data or companyID
            get("/getAllEntriesTEST", (req, res) -> {
                res.type("application/json");
                int limit = Integer.parseInt(req.queryParams("limit"));
                if(limit > 500){
                    res.status(400);
                    return createResponse("error", "Bad request: limit too big, use limit lower than 500", null);
                }
                if (limit <= 0) {
                    res.status(400);
                    return createResponse("error", "Bad request: use limit higher than 0", null);
                }
                String entries = MongoDBConnection.allEntriesTEST(limit);
                if (entries == null || entries.isEmpty()) {
                    res.status(404);
                    return createResponse("error", "no Entries", null);
                }
                res.status(200);
                return createResponse("success", "entries retrieved successfully", new JSONArray(entries));
            });


            // GET Json from parameter X
            get("/search", (req, res) -> {
                res.type("application/json");

                String field = req.queryParams("field");  // Nome del campo da cercare
                String value = req.queryParams("value");  // Valore da cercare

                if (field == null || value == null) {
                    res.status(400);
                    return createResponse("error", "bad request, field or value null or empty", null);
                }

                String result = MongoDBConnection.searchEntries(field, value);

                if (result.contains("error")) {
                    res.status(404);
                    return createResponse("error", "no results found", result);
                }
                res.status(200);
                return result;
            });

            /* **********************************
                    REMOVE BEFORE PRODUCTION
                **********************************/

            delete("/deleteLuce", (req, res) -> {
                res.type("application/json");

                long deletedEntries = 0;
                String code = req.queryParams("code");
                if (code == null || code.isEmpty()) {
                    res.status(400);
                    return createResponse("error", "Bad request, code empty or wrong", null);
                }

                if(code.equals("luce")){
                    deletedEntries = MongoDBConnection.deleteLuce();
                    if(deletedEntries == 0){
                        return createResponse("error", "No documents deleted", null);
                    }
                }
                return createResponse("success", "Documents deleted", deletedEntries);
            });

            post("/addSchema" ,(req, res) -> {
                res.type("application/json");
                Document mainDoc;
                String body = req.body();
                mainDoc = Document.parse(body);
                String response = MongoDBConnection.addSchema(mainDoc.toJson());
                if (response.contains("error")) return createResponse("error", "Schema not added", response);

                return createResponse("success", "Schema added successfully", response);
            });

            get("/getSchema" ,(req, res) -> {
                res.type("application/json");
                String schema = MongoDBConnection.getSchema();

                return createResponse("success", "Schema retrieved successfully", new JSONArray(schema));
            });

            delete("/deleteSchema", (req, res) -> {
                res.type("application/json");
                String id = req.queryParams("id");

                // Retrieve data from MongoDB with ID JSON
                boolean deleted = MongoDBConnection.deleteSchema(id);
                if (!deleted) {
                    res.status(404);
                    return createResponse("error", "schema not deleted", null);
                }
                res.status(200);
                return createResponse("success", "schema deleted", null);
            });

            System.out.println("Server avviato sulla porta " + Config.getEnvVariable("SERVER_PORT") + "...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}