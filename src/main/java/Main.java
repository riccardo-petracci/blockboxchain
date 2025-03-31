import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;

import static spark.Spark.*;

public class Main {

    public static String createResponse(String status, String message, Object data) {
        JSONObject response = new JSONObject();
        response.put("status", status);
        response.put("message", message);
        response.put("data", data);
        return response.toString();
    }

    public static void main(String[] args) {

        try {
//            SwingUtilities.invokeLater(SwingMain::createAndShowGUI);

            //HANDLER SERVER
            new Thread(() -> {
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
                    String success = MongoDBConnection.storeIncomingJson(jsonString);
                    if(success.contains("error")) {
                        res.status(400);
                        return createResponse("error", "Bad Request", success);
                    }
                    res.status(200);
                    return createResponse("success", "OK", success);
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
                        if (result.contains("blockchain")|| result.contains("errore")) {
                            res.status(404);
                            return createResponse("error", "ID not found", result);
                        }
                        res.status(200);
                        return createResponse("success","ID found", result);
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
                    return createResponse("success","ID found", result);
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
                    return createResponse("success","ID found", new JSONArray(result));
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

                System.out.println("Server avviato sulla porta " + Config.getEnvVariable("SERVER_PORT") +"...");
            }).start(); // Avvia il thread separato

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}