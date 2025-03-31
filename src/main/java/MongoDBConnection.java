import com.mongodb.client.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import com.mongodb.MongoWriteException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.bson.Document;
import org.bson.json.JsonWriterSettings;
import org.json.JSONArray;
import org.json.JSONObject;

public class MongoDBConnection {

    private final static String URI = Config.getEnvVariable("MONGO_URI");
    private final static String DATABASE = Config.getEnvVariable("MONGO_DB_NAME");
    private final static String COLLECTION_CREDENTIAL = Config.getEnvVariable("COLLECTION_CREDENTIAL");
    private final static String COLLECTION_ENTRIES = Config.getEnvVariable("COLLECTION_ENTRIES");
    private final static String COLLECTION_BLOCKS = Config.getEnvVariable("COLLECTION_BLOCKS");

    // Metodo per ottenere la collezione MongoDB
    private static MongoCollection<Document> getCollection(String _collection) {
        MongoClient mongoClient = MongoClients.create(URI);
        MongoDatabase database = mongoClient.getDatabase(DATABASE);
        return database.getCollection(_collection);
    }


    public static String allEntries(){
        // Obtain collection from DB
        MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);

        // Create JSON array for all entries
        JSONArray jsonArray = new JSONArray();

        // Query to obtain documents
        try (MongoCursor<Document> cursor = collection.find().iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                // Convert doc BSON in JSONObject and add to array
                JSONObject jsonObject = new JSONObject(doc.toJson());
                jsonArray.put(jsonObject);
            }
        }
        return jsonArray.toString();
    }


	public static boolean storeBlock(String _idref, String _block)
	{
		boolean result = true;

	        try {
                MongoCollection<Document> collection = getCollection(COLLECTION_BLOCKS);

	            // Define the document as a JSON string
	            String _json = "{ \"StandardEntry_id\": \"" + _idref + "\", \"block\": " + _block + " }";
	            // Convert JSON String to BSON Document
	            Document doc = Document.parse(_json);

	            // Insert into MongoDB
	            collection.insertOne(doc);

	        } catch (JsonParseException e) {
	            // Handle invalid JSON input
	            result = false;
	        	System.err.println("Error: Invalid JSON input. Please check the format.");
	            e.printStackTrace();

	        } catch (Exception e) {
	            // Catch any other exceptions
	            result = false;
	        	System.err.println("Error: An unexpected error occurred.");
	            e.printStackTrace();
	        }

	        return result;
	}


	public static String storeStandardEntry(String _json)
	{
        String result = "";

        try {
            MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);

            // Convert JSON String to BSON Document
            Document doc = Document.parse(_json);

            // Insert into MongoDB
            collection.insertOne(doc);
//            ObjectId generatedId = doc.getObjectId("_id");
//            result = generatedId.toString();

            // Convert doc with updated _id in JSON String
            result = doc.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build());

        } catch (MongoWriteException e) {
            result = "error: missing properties or JSON schema not satisfied";
            System.err.println(e.getMessage());
            e.printStackTrace();
        } catch (JsonParseException e) {
            // Handle invalid JSON input
            result = "error: JSON not valid" + e.getMessage();
        	System.err.println(result);
            e.printStackTrace();

        } catch (Exception e) {
            // Catch any other exceptions
            result = "error unexpected" + e.getMessage();
        	System.err.println(result);
            e.printStackTrace();
        }
        return result;
	}


    public static String getJsonID(String _json){
        String result = "";
        Document doc = Document.parse(_json);

        ObjectId generatedId = doc.getObjectId("_id");
        result = generatedId.toString();

        return result;
    }


	public static HashMap<String, String> getBCcredentials()
	{
        MongoCollection<Document> collection = getCollection(COLLECTION_CREDENTIAL);
        HashMap<String, String> settingsValues = new HashMap<String, String>();
        FindIterable<Document> settings = collection.find();
     
        for (Document doc : settings) 
        {
        	settingsValues.put("address", doc.get("address").toString());
        	settingsValues.put("credentials", doc.get("credentials").toString());
        	settingsValues.put("contract", doc.get("contract").toString());
        	settingsValues.put("gaslimit", doc.get("gaslimit").toString());
        	settingsValues.put("gasprice", doc.get("gasprice").toString());
        }

        return settingsValues;
	}


    private static String prettyPrintJson(String json) 
    {
        try 
        {
            // Create an ObjectMapper instance
            ObjectMapper objectMapper = new ObjectMapper();

            // Parse the JSON string into a tree structure and then convert it back to a pretty-printed string
            Object jsonObject = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } 
        catch (Exception e) 
        {
            e.printStackTrace();
            return null;
        }
    }
    

    public static String normalizeJson(String json) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Parse JSON into a Tree (removes unnecessary whitespace)
        Object jsonObject = objectMapper.readValue(json, Object.class);

        // Convert back to JSON with sorted keys and no extra spaces
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return objectMapper.writeValueAsString(jsonObject);
    }


    public static String getMongodbIdJson(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // Convert JSON String in JSON Node
            JsonNode jsonNode = objectMapper.readTree(json);

            // Extract "_id"
            if (jsonNode.has("_id") && jsonNode.get("_id").has("$oid")) {
                return jsonNode.get("_id").get("$oid").asText();
            }
            return "ID not found";

        } catch (Exception e) {
            e.printStackTrace();
            return "Error during ID extraction";
        }
    }


    public static String storeIncomingJson(String _json){
        String response = "";
        if (_json.isEmpty()) {
            return "error: JSON empty";
        }

        try {
            //1. Normalize and save
            String updatedNormalized = normalizeAndStore(_json);
            if (updatedNormalized.contains("error")) return updatedNormalized;
            if(updatedNormalized == null || updatedNormalized.isEmpty()){
                response = "error: JSON save failed because null or empty";
                System.err.println(response);
                return response;
            }

            //2. Save hash JSON in BC
            String entryID = MongoDBConnection.getMongodbIdJson(updatedNormalized);
            String blocknum = storeOnBlockchain(updatedNormalized, entryID);
            if (blocknum == null || blocknum.isEmpty()){
                response = "error: save in blockchain because blocknum null or empty";
                System.err.println(response);
                return response;
            }

            //3. update DB with block number
            updateDBwithBlockNum(entryID, blocknum);
            response = "blockchain block number saved in database";
            return response;
        } catch (Exception e) {
            response = "error: " + e.getMessage();
            System.out.println(response);
            e.printStackTrace();
            return response;
        }
    }


    private static String normalizeAndStore(String _json) {
        try {
            String normalized = MongoDBConnection.normalizeJson(_json);
            System.out.println("JSON Normalizzato:\n" + normalized);
            System.out.println("Salvataggio in corso su database...");
            String updatedNormalized = MongoDBConnection.storeStandardEntry(normalized);
            if (updatedNormalized.contains("error")){ return updatedNormalized; }
            return MongoDBConnection.normalizeJson(updatedNormalized);
        } catch (Exception e) {
            System.out.println("Errore salvataggio nel DB: " + e.getMessage());
            return null;
        }
    }


    private static String storeOnBlockchain(String _json, String entrID) {
        if (entrID.equals("")) return null;
        try {
            System.out.println("Salvataggio in corso su blockchain...");
            HashMap<String, String> settings = MongoDBConnection.getBCcredentials();
            SmartContractHash SCH = new SmartContractHash(
                    settings.get("address"),
                    settings.get("credentials"),
                    settings.get("contract"),
                    new BigInteger(settings.get("gaslimit")),
                    new BigInteger(settings.get("gasprice"))
            );

            String hashed = SmartContractHash.generateMD5(_json);
            String receipt = SCH.storeHash(hashed);
            System.out.println("Hashed md5: " + hashed + "Receipt SCH.storeHash(hashed): " + receipt);
            String blockNumber = SmartContractHash.extractField(receipt, "blockNumber");
            if (blockNumber.contains("0x")) {
                blockNumber = blockNumber.replaceFirst("^0x", "");
                int DecimalBlockNumber = Integer.parseInt(blockNumber, 16);
                blockNumber = "" + DecimalBlockNumber;
            }
            return (Integer.parseInt(blockNumber) > 0) ? blockNumber : null;

        } catch (Exception e) {
            System.out.println("Errore salvataggio in BC: " + e.getMessage());
            return null;
        }
    }


    private static void updateDBwithBlockNum(String entryID, String blocknum) {
        System.out.println("Aggiornamento database...");
        MongoDBConnection.storeBlock(entryID, blocknum);
    }


    public static String getValidationJson(String _json) {
        String response = "";
        if (!_json.isEmpty()) {
            try {
                HashMap<String, String> settings = MongoDBConnection.getBCcredentials();
                SmartContractHash SCH = new SmartContractHash(settings.get("address"),
                        settings.get("credentials"),
                        settings.get("contract"),
                        new BigInteger(settings.get("gaslimit")),
                        new BigInteger(settings.get("gasprice"))
                );
                String normalized = MongoDBConnection.normalizeJson(_json);
                String hashed = SmartContractHash.generateMD5(normalized);
                System.out.println("\nJSON Normalizzato:\n" + normalized + "\n" + "Hash Normalizzato:\n" + hashed);

                int blockNumber = SCH.checkHash(hashed);

                if(blockNumber > 0)
                {
                    response = "document hash in block: " + blockNumber;
                    System.out.println(response);
                }
                else
                {
                    response = "error: document hash not in blockchain";
                    System.out.println(response);
                }

            } catch (Exception e1)
            {
                response = "Si è verificato un errore: " + e1;
                System.out.println(response);
                e1.printStackTrace();
            }
        }
        return response;
    }


    public static String getBlockFromID(String _id) {
        String response = "";
        if (!_id.isEmpty()) {
            try {
                HashMap<String, String> settings = MongoDBConnection.getBCcredentials();
                SmartContractHash SCH = new SmartContractHash(settings.get("address"),
                        settings.get("credentials"),
                        settings.get("contract"),
                        new BigInteger(settings.get("gaslimit")),
                        new BigInteger(settings.get("gasprice"))
                );

                MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);

                // Convert String in ObjectID
                ObjectId objectId = new ObjectId(_id);

                // Search Doc
                Document doc = collection.find(new Document("_id", objectId)).first();

                String normalized = MongoDBConnection.normalizeJson(doc.toJson());
                String hashed = SmartContractHash.generateMD5(normalized);
                System.out.println("\nJSON Normalizzato:\n" + normalized + "\n" + "Hash Normalizzato:\n" + hashed);

                int blockNumber = SCH.checkHash(hashed);
                if(blockNumber > 0)
                {
                    response = "Doc hash in block: " + blockNumber;
                    System.out.println(response);
                    return response;
                }
                else
                {
                    response = "Error: no document hash in blockchain";
                    System.out.println(response);
                    return response;
                }
            } catch (Exception e1) {
                response = "Si è verificato un errore: " + e1;
                System.out.println(response);
                e1.printStackTrace();
            }
        }
        return response;
    }


    public static String getJsonFromID(String _id){
        JSONArray response = new JSONArray();
        if (!_id.isEmpty()) {
            try {
                MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);

                // Convert String in ObjectID
                ObjectId objectId = new ObjectId(_id);

                // Search Doc
                Document doc = collection.find(new Document("_id", objectId)).first();

                if (doc != null) {
                    response.put(new JSONObject(doc.toJson()));
                } else {
                    return "error: document not found";
                }
            } catch (IllegalArgumentException e) {
                return "error: invalid ID format";
            }
        }
        return response.toString();
    }


    public static String searchEntries(String _field, String _value) {
        try {
            MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);

            // Build filter
            Document filter = new Document(_field, _value);

            FindIterable<Document> results = collection.find(filter);

            // Convert results in JSON array
            JSONArray jsonArray = new JSONArray();
            for (Document doc : results) {
                jsonArray.put(new JSONObject(doc.toJson()));
            }

            if (jsonArray.isEmpty()) {
                return "error: no result found";
            }

            return jsonArray.toString();

        } catch (Exception e) {
            return new JSONObject().put("error", "invalid request: " + e.getMessage()).toString();
        }
    }

    // JavaSwing Methods
    public static List<String> getStandardEntries()
    {
        MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);
        List<String> entries = new ArrayList<>();
        FindIterable<Document> uglyEntries = collection.find();

        for (Document doc : uglyEntries) {
            entries.add(MongoDBConnection.prettyPrintJson(doc.toJson()));
        }
        return entries;
    }


    public static String jsonPanelId(String json) {
        try {
            // Create ObjectMapper instance
            ObjectMapper objectMapper = new ObjectMapper();

            // Parse JSON into a tree structure
            JsonNode jsonNode = objectMapper.readTree(json);

            // Extract the field
            String serial = jsonNode.has("num_seriale") ? jsonNode.get("num_seriale").asText() : null;
            String timestamp = jsonNode.has("timestamp") ? jsonNode.get("timestamp").asText() : null;

            if(serial != null && timestamp != null)
                serial = serial + " - " + timestamp;
            else if(serial == null && timestamp != null)
                serial = timestamp;

            return serial;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}