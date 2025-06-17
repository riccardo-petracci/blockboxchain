import org.json.JSONArray;
import org.json.JSONObject;
import static spark.Spark.*;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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

            // Save data with POST
            post("/saveData", (req, res) -> {
                res.type("application/json");

                // Receive JSON from request
                String body = req.body();
                JSONObject json = new JSONObject(body);
                String jsonString = json.toString();
                System.out.println("JSON ricevuto: " + json.toString(4));

                // Save JSON in MongoDB
                String success = MongoDBConnection.storeIncomingJson(jsonString , MongoDBConnection.STANDARD);
                if (success.contains("error")) {
                    res.status(400);
                    return createResponse("error", "Bad Request", success);
                }
                res.status(200);
                return createResponse("success", "Data saved in DB and blockchain", success);
            });

            //Save Blob with POST
            post("/saveDataBlob", (req, res) -> {
                req.raw().setAttribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));

                String companyName = req.queryParams("companyName");
                if (companyName == null || companyName.isEmpty()) {
                    res.status(400);
                    return createResponse("error", "Bad Request", "missing params: companyName");
                }

                Collection<Part> parts = req.raw().getParts();
                List<String> success = new ArrayList<>();

                for (Part filePart : parts) {
                    if (filePart.getName().equals("file")) {
                        String fileName = filePart.getSubmittedFileName();
                        long fileSize = filePart.getSize();

                        // ========== SECURITY CHECKS ==========
                        // 1. Check size (e.g., max 5MB)
                        if (fileSize > 5 * 1024 * 1024) {
                            res.status(413);
                            return createResponse("error", "File too large (max 5MB)", null);
                        }

                        // 3. Rename file safely
                        fileName = UUID.randomUUID() + "_" + fileName;
                        File uploadsDir = new File("uploads");
                        if (!uploadsDir.exists()) uploadsDir.mkdir();
                        File savedFile = new File(uploadsDir, fileName);

                        // Save file to disk
                        InputStream input = filePart.getInputStream();
                        Files.copy(input, savedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                        try {
                            String result = MongoDBConnection.storeBlob(
                                    companyName,
                                    fileName,
                                    fileSize,
                                    filePart.getContentType(),
                                    System.currentTimeMillis(),
                                    Files.readAllBytes(savedFile.toPath()),
                                    MongoDBConnection.BLOB
                            );
                            JSONArray fullResult = new JSONArray();
                            fullResult.put(fileName);
                            fullResult.put(result);

                            success.add(createResponse("success", "File Saved", fullResult));

                        } catch (Exception e) {
                            success.add(createResponse("error", "Failed to saved", fileName));
                        } finally {
                            // Always delete the file, even on failure
                            try {
                                Files.deleteIfExists(savedFile.toPath());
                                System.out.println("Deleted temporary file: " + savedFile.getPath());
                            } catch (IOException e) {
                                System.err.println("Failed to delete temp file: " + e.getMessage());
                            }
                        }
                    }
                }

                return String.join("\n", success);
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
                String result = MongoDBConnection.getJsonFromID(id, MongoDBConnection.STANDARD);
                if (result.contains("error")) {
                    res.status(404);
                    return createResponse("error", "ID not found", result);
                }
                res.status(200);
                return createResponse("success", "ID found", new JSONArray(result));
            });

            // GET all entries
            get("/getAllEntries", (req, res) -> {
                res.type("application/json");
                String entries = MongoDBConnection.allEntries();
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

            get("/deleteLuce", (req, res) -> {
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


            System.out.println("Server avviato sulla porta " + Config.getEnvVariable("SERVER_PORT") + "...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}