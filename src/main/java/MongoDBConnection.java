import com.mongodb.client.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.conversions.Bson;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import com.mongodb.MongoWriteException;
import static com.mongodb.client.model.Filters.*;

import java.math.BigInteger;
import java.util.HashMap;

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
    private final static String COLLECTION_BLOB = Config.getEnvVariable("COLLECTION_BLOB");
    private final static String GASPRICE = Config.getEnvVariable("GASPRICE");
    private final static String GASLIMIT = Config.getEnvVariable("GASLIMIT");
    public final static int STANDARD = 0;
    public final static int BLOB = 1;

    public static void checkInitialization() {

        //1) check if collection are created
        try (MongoClient mongoClient = MongoClients.create(URI)) {
            MongoDatabase database = mongoClient.getDatabase(DATABASE);

            // Step 1: Ensure collections exist
            ensureCollectionExists(database, COLLECTION_CREDENTIAL);
            ensureCollectionExists(database, COLLECTION_ENTRIES);
            ensureCollectionExists(database, COLLECTION_BLOCKS);
            ensureCollectionExists(database, COLLECTION_BLOB);

            // Step 2: Check if smart contract is already deployed
            MongoCollection<Document> contractCollection = database.getCollection(COLLECTION_CREDENTIAL);
            Document contractDoc = contractCollection.find().first();

            if (contractDoc == null) {
                System.out.println("Smart contract not deployed. Deploying now...");

                // Step 3: Deploy the contract (placeholder function)
                String deployedAddress = SmartContractHash.main();

                // Step 4: Save address in MongoDB
                contractDoc = new Document("address", SmartContractHash.getEndPoint())
                        .append("credentials", SmartContractHash.getPrivateKey())
                        .append("contract", deployedAddress)
                        .append("gaslimit", GASLIMIT)
                        .append("gasprice", GASPRICE);
                contractCollection.insertOne(contractDoc);

                System.out.println("Smart contract deployed and saved at address: " + deployedAddress);
            } else {
                System.out.println("Smart contract already deployed at: " + contractDoc.getString("contract"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void ensureCollectionExists(MongoDatabase db, String name) {
        boolean exists = db.listCollectionNames()
                .into(new java.util.ArrayList<>())
                .contains(name);
        if (!exists) {
            db.createCollection(name);
            System.out.println("Created collection: " + name);
        }
    }

    // Obtain MongoDB collection
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


	public static boolean storeBlock(String _idref, String _block, int _collection)
	{
		boolean result = true;

	        try {
                MongoCollection<Document> collection = getCollection(COLLECTION_BLOCKS);
                String _json = "";
	            // Define the document as a JSON string
                if (_collection == STANDARD) _json = "{ \"collection_id\": \"stand_" + _idref + "\", \"block\": " + _block + " }";
                if (_collection == BLOB) _json = "{ \"collection_id\": \"blob_" + _idref + "\", \"block\": " + _block + " }";

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


	public static String storeStandardEntry(String _json , int _collection)
	{
        String result = "";
        MongoCollection<Document> collection = null;

        try {
            if (_collection == STANDARD) {
                collection = getCollection(COLLECTION_ENTRIES);
            } else if (_collection == BLOB) {
                collection = getCollection(COLLECTION_BLOB);
            }

            System.out.println("collection: " + collection);

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


    public static String storeIncomingJson(String _json , int _collection){
        String response = "";
        if (_json.isEmpty()) {
            return "error: JSON empty";
        }

        try {
            //1. Normalize and save
            String updatedNormalized = normalizeAndStore(_json , _collection);
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
            updateDBwithBlockNum(entryID, blocknum , _collection);
            //response = "blockchain block number saved in database";
            response = entryID;
            return response;
        } catch (Exception e) {
            response = "error: " + e.getMessage();
            System.out.println(response);
            e.printStackTrace();
            return response;
        }
    }

    public static String storeBlob(String _companyName, String _fileName, long _fileSize, String _contentType, long _upTime, byte[] _bytes, int _collection) {
        String response = "";
        try {
//            MongoCollection<Document> collection = getCollection(COLLECTION_BLOB);
            Document doc = new Document("companyName", _companyName.toUpperCase())
                    .append("name", _fileName)
                    .append("size", _fileSize)
                    .append("contentType", _contentType)
                    .append("uploadTime", _upTime)
                    .append("content", _bytes); // store binary content

            response = storeIncomingJson(doc.toJson(), _collection);

        }catch (Exception e) {
            response = "Error: " + e.getMessage();
        }
        return response;
    }


    private static String normalizeAndStore(String _json , int _collection) {
        try {
            String normalized = MongoDBConnection.normalizeJson(_json);
            System.out.println("JSON Normalizzato:\n" + normalized);
            System.out.println("Salvataggio in corso su database...");
            String updatedNormalized = MongoDBConnection.storeStandardEntry(normalized , _collection);
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


    private static void updateDBwithBlockNum(String entryID, String blocknum , int _collection) {
        System.out.println("Aggiornamento database...");
        MongoDBConnection.storeBlock(entryID, blocknum , _collection);
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
        MongoCollection<Document> collection = null;
        int _collection = 0;

        if (!_id.isEmpty()) {
            try {
                HashMap<String, String> settings = MongoDBConnection.getBCcredentials();
                SmartContractHash SCH = new SmartContractHash(settings.get("address"),
                        settings.get("credentials"),
                        settings.get("contract"),
                        new BigInteger(settings.get("gaslimit")),
                        new BigInteger(settings.get("gasprice"))
                );

                // find doc with _id and analize if contains stand_ or blob_ to choose right collection
                MongoCollection<Document> blockCollection = getCollection(COLLECTION_BLOCKS);
                String pattern = "" + _id + "$";
                Bson filter = regex("collection_id", pattern);
                Document blockDoc = blockCollection.find(filter).first();
                System.out.println(blockDoc.toJson());
                if (blockDoc != null && blockDoc.containsKey("collection_id")) {
                    String collectionId = blockDoc.getString("collection_id");

                    if (collectionId != null && !collectionId.isEmpty()) {
                        String lower = collectionId.toLowerCase();
                        if (lower.contains("stand")) {
                            _collection = STANDARD;
                            System.out.println("collection_id contains 'stand' , collection: " + _collection);
                        } else if (lower.contains("blob")) {
                            _collection = BLOB;
                            System.out.println("collection_id contains 'blob' , collection: " + _collection);
                        } else {
                            System.out.println("collection_id contains neither 'stand' nor 'blob'");
                        }
                    } else {
                        System.out.println("collection_id is empty or null");
                    }
                } else {
                    System.out.println("Document not found or missing collection_id");
                }

                if (_collection == STANDARD) collection = getCollection(COLLECTION_ENTRIES);
                if (_collection == BLOB) collection = getCollection(COLLECTION_BLOB);

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


    public static String getJsonFromID(String _id, int _collection) {
        JSONArray response = new JSONArray();
        MongoCollection<Document> collection = null;

        if (!_id.isEmpty()) {
            try {
                if (_collection == STANDARD) collection = getCollection(COLLECTION_ENTRIES);
                if (_collection == BLOB) collection = getCollection(COLLECTION_BLOB);

                // Convert String in ObjectID
                ObjectId objectId = new ObjectId(_id);

                // Search Doc
                Document doc = collection.find(new Document("_id", objectId)).first();

                if (doc != null) {
                    response.put(new JSONObject(doc.toJson()));
                } else if (doc == null && _collection == STANDARD) {
                    return getJsonFromID(_id, BLOB);
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
}