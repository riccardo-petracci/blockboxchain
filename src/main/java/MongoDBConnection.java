import com.mongodb.client.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.result.DeleteResult;
import org.bson.conversions.Bson;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;
import com.mongodb.MongoWriteException;

import java.math.BigInteger;
import java.util.*;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.bson.Document;
import org.bson.json.JsonWriterSettings;
import org.everit.json.schema.ValidationException;
import org.json.JSONArray;
import org.json.JSONObject;

public class MongoDBConnection {

    private final static String URI = Config.getEnvVariable("MONGO_URI");
    private final static String DATABASE = Config.getEnvVariable("MONGO_DB_NAME");
    private final static String COLLECTION_CREDENTIAL = Config.getEnvVariable("COLLECTION_CREDENTIAL");
    private final static String COLLECTION_ENTRIES = Config.getEnvVariable("COLLECTION_ENTRIES");
    private final static String COLLECTION_BLOCKS = Config.getEnvVariable("COLLECTION_BLOCKS");
    private final static String COLLECTION_SCHEMA = Config.getEnvVariable("COLLECTION_SCHEMA");
    private final static String GASPRICE = Config.getEnvVariable("GASPRICE");
    private final static String GASLIMIT = Config.getEnvVariable("GASLIMIT");
    private final static String[] COMPANYID = {"LUCESRL", "ITCSRL", "UNICAM", "BMTSRL"}; //REMOVE BEFORE PRODUCTION

    /* *********************************
     *       STRUCTURAL METHODS        *
     **********************************/

    public static void checkInitialization() {

        //1) check if collection are created
        try (MongoClient mongoClient = MongoClients.create(URI)) {
            MongoDatabase database = mongoClient.getDatabase(DATABASE);

            // Step 1: Ensure collections exist
            ensureCollectionExists(database, COLLECTION_CREDENTIAL);
            ensureCollectionExists(database, COLLECTION_ENTRIES);
            ensureCollectionExists(database, COLLECTION_BLOCKS);
            ensureCollectionExists(database, COLLECTION_SCHEMA);

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
                .into(new ArrayList<>())
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


    /* *********************************
     *        PUBLIC METHODS           *
     **********************************/

    /*get data filtered by
         - companyID
         - rilevazioni or manutenzioni (record with/out files)
         - range time (unixtime)
         - limit the number of records returned
     */
    public static String getFilteredRecords(
            String companyID,
            Boolean mustContainFiles,
            Long timeFrom,
            Long timeTo,
            int limit
    ) {
        MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);
        JSONArray result = new JSONArray();

        List<Bson> pipeline = new ArrayList<>();

    // filter upload_time
        if (timeFrom != null || timeTo != null) {
            Document timeFilter = new Document();
            if (timeFrom != null) timeFilter.append("$gte", timeFrom);
            if (timeTo != null) timeFilter.append("$lte", timeTo);

            pipeline.add(new Document("$match",
                    new Document("upload_time", timeFilter)
            ));
        }

        //filter files existence
        if (mustContainFiles != null) {
            pipeline.add(new Document("$match",
                    new Document("files",
                            new Document("$exists", mustContainFiles))
            ));
        }

        //filter companyID (ANYWHERE)
        pipeline.add(new Document("$match",
                new Document("$expr",
                        new Document("$function",
                                new Document("body",
                                        "function(doc, company) {" +
                                                "  function search(obj) {" +
                                                "    if (obj === null) return false;" +
                                                "    if (typeof obj === 'object') {" +
                                                "      for (let k in obj) {" +
                                                "        if (k === 'companyID' && obj[k] === company) return true;" +
                                                "        if (search(obj[k])) return true;" +
                                                "      }" +
                                                "    }" +
                                                "    return false;" +
                                                "  }" +
                                                "  return search(doc);" +
                                                "}")
                                        .append("args", Arrays.asList("$$ROOT", companyID))
                                        .append("lang", "js")
                        )
                )
        ));

        //sort & limit
        pipeline.add(new Document("$sort", new Document("_id", -1)));
        pipeline.add(new Document("$limit", limit));

        //execution
        AggregateIterable<Document> iterable = collection.aggregate(pipeline);

        for (Document doc : iterable) {
            result.put(new JSONObject(doc.toJson()));
        }

        return result.toString();
    }


    //main method to store data
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


    //method to get the blockchain block number of a given record id
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


    //get the number of the blockchain block passing the JSON (included MongoDB ID)
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


    //get the JSON passing the MongoDB ID
    public static String getJsonFromID(String _id) {
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


    //method to search entries using the couple field : value
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


    // TIPS: refactor this method.
    /* method that use getJsonFromID(_id) method:
        - if that method return something the ID exist
        - otherwise if error the ID does not exist */
    public static boolean checkID(String _id) {
        String ID = getJsonFromID(_id);
        if (ID.contains("error")) {
            return false;
        }
        return true;
    }



    //method to search a key of a json doc (also nested keys).
    public static Object getJsonKey(Object _doc, String _field) {
        if (_doc instanceof Document) {
            Document doc = (Document) _doc;
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                if (entry.getKey().equals(_field)) {
                    return entry.getValue();
                }
                Object result = getJsonKey(entry.getValue(), _field);
                if (result != null) return result;
            }
        } else if (_doc instanceof List) {
            for (Object item : (List<?>) _doc) {
                Object result = getJsonKey(item, _field);
                if (result != null) return result;
            }
        }
        return null;
    }


    //method check the existence of schemaID and return the related document
    public static Document checkSchemaID(String _schemaID) {
        MongoCollection<Document> collection = getCollection(COLLECTION_SCHEMA);
        Document query = new Document("schemaID", _schemaID);
        return collection.find(query).first();
    }


    //validate the document with the related JSON schema
    public static String documentValidated(Document _schemaDoc, Document _doc) {
        JSONObject schemaJson;

        if(_schemaDoc.containsKey("schema") && _schemaDoc.get("schema") instanceof Document){
            Document nested = _schemaDoc.get("schema" , Document.class);
            schemaJson = new JSONObject(nested.toJson());
        } else {
            schemaJson = new JSONObject(_schemaDoc.toJson());
        }

        JSONObject docJson = new JSONObject(_doc.toJson());
        SchemaValidator validator = new SchemaValidator(schemaJson);
        try {
            validator.validate(docJson);
            return "success";
        }catch (ValidationException ve){
            return "Validation failed: " + String.join("; ", ve.getAllMessages());
        }
    }

    /* *********************************
     *        PRIVATE METHODS          *
     **********************************/

    private static String normalizeAndStore(String _json) {
        try {
            String normalized = MongoDBConnection.normalizeJson(_json);
            System.out.println("JSON Normalizzato:\n" + normalized);
            System.out.println("Salvataggio in corso su database...");
            String updatedNormalized = MongoDBConnection.storeEntry(normalized);
            if (updatedNormalized.contains("error")){ return updatedNormalized; }
            return MongoDBConnection.normalizeJson(updatedNormalized);
        } catch (Exception e) {
            System.out.println("Errore salvataggio nel DB: " + e.getMessage());
            return null;
        }
    }


    private static String normalizeJson(String json) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Parse JSON into a Tree (removes unnecessary whitespace)
        Object jsonObject = objectMapper.readValue(json, Object.class);

        // Convert back to JSON with sorted keys and no extra spaces
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        return objectMapper.writeValueAsString(jsonObject);
    }

    private static String storeEntry(String _json)
    {
        String result = "";

        try {
            MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);

            // Convert JSON String to BSON Document
            Document doc = Document.parse(_json);

            // Insert into MongoDB
            collection.insertOne(doc);

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

    private static String getMongodbIdJson(String json) {
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

    private static boolean storeBlock(String _idref, String _block)
	{
		boolean result = true;

	        try {
                MongoCollection<Document> collection = getCollection(COLLECTION_BLOCKS);
                String _json = "{ \"collection_id\": \"" + _idref + "\", \"block\": " + _block + " }";;

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


	private static HashMap<String, String> getBCcredentials()
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


    private static void updateDBwithBlockNum(String entryID, String blocknum) {
        System.out.println("Aggiornamento database...");
        MongoDBConnection.storeBlock(entryID, blocknum);
    }


    /*  *********************************
     *      REMOVE BEFORE PRODUCTION    *
     *  ********************************/
    public static String allEntriesTEST(int _nEntries) {
        MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);

        JSONArray jsonArray = new JSONArray();

        try (MongoCursor<Document> cursor = collection
                .find()
                .sort(Sorts.descending("_id"))
                .limit(_nEntries)
                .iterator()) {

            while (cursor.hasNext()) {
                Document doc = cursor.next();
                JSONObject jsonObject = new JSONObject(doc.toJson());
                jsonArray.put(jsonObject);
            }
        }

        return jsonArray.toString();
    }

    public static boolean checkCompanyID(String _id) {
        //in fase di test con sole 3 aziende controllare dentro un array
        for (int i = 0; i < COMPANYID.length; i++) {
            if (_id.equals(COMPANYID[i])) {
                return true;
            }
        }
        return false;
    }

    public static long deleteLuce(){
        try {
            long counter = 0;
            MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);
            FindIterable<Document> entriesDocs = collection.find();
            for (Document doc : entriesDocs) {
                if (containsNameField(doc, "vat","02564320428")) {
                    collection.deleteOne(Filters.eq("_id", doc.getObjectId("_id")));
                    counter++;
                }
            }
            return counter;
        } catch (Exception e) {
        return 0;
        }
    }

    private static boolean containsNameField(Document _doc, String _field, String _targetValue) {
        for (Map.Entry<String, Object> entry : _doc.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (_field.equalsIgnoreCase(key) && value instanceof String) {
                String nameValue = ((String) value).toLowerCase();
                if (nameValue.contains(_targetValue.toLowerCase())) {
                    return true;
                }
            } else if (value instanceof Document) {
                if (containsNameField((Document) value, _field, _targetValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String addSchema(String _schema){
        String ID = "";
        try {
            MongoCollection<Document> collection = getCollection(COLLECTION_SCHEMA);
            Document doc = Document.parse(_schema);
            collection.insertOne(doc);
            String tmpDoc = doc.toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build());
            ID = getMongodbIdJson(tmpDoc);
        }catch (Exception e){
            // Catch any other exceptions
            String result = "error unexpected" + e.getMessage();
            System.err.println(result);
            e.printStackTrace();
            return "error unexpected" + e.getMessage();
        }
        return ID;
    }

    public static String getSchema(){
        // Obtain collection from DB
        MongoCollection<Document> collection = getCollection(COLLECTION_SCHEMA);

        // Create JSON array for all entries
        JSONArray jsonArray = new JSONArray();
        int counter = 0;

        // Query to obtain documents
        try (MongoCursor<Document> cursor = collection.find().iterator()) {
            while (cursor.hasNext()) {
                if (counter >= 50) break;
                Document doc = cursor.next();
                // Convert doc BSON in JSONObject and add to array
                JSONObject jsonObject = new JSONObject(doc.toJson());
                jsonArray.put(jsonObject);
                counter++;
            }
        }
        System.out.println("counter: " + counter);
        return jsonArray.toString();
    }

    public static boolean deleteSchema(String _id){
        try {
            MongoCollection<Document> collection = getCollection(COLLECTION_SCHEMA);
            Bson filter = Filters.eq("_id", new ObjectId(_id));
            DeleteResult result = collection.deleteOne(filter);
            return result.getDeletedCount() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }




    /* *******************************************************
    *  METHOD X /GETDATA USING JAVA FILTER INSTEAD PIPELINE  *
    *  ******************************************************/

//        public static String getFilteredRecords(
//            String companyID,
//            Boolean mustContainFiles,
//            Long timeFrom,       // Unix timestamp (nullable)
//            Long timeTo,         // Unix timestamp (nullable)
//            int limit
//    ) {
//
//        MongoCollection<Document> collection = getCollection(COLLECTION_ENTRIES);
//        JSONArray jsonArray = new JSONArray();
//
//        // Sort: ultimi documenti inseriti
//        Bson sort = Sorts.descending("_id");
//
//        try (MongoCursor<Document> cursor = collection.find().sort(sort).iterator()) {
//            while (cursor.hasNext() && jsonArray.length() < limit) {
//                Document doc = cursor.next();
//
//                // ====== FILTER 1 → CompanyID (ricorsivo) ======
//                if (!documentContainsKeyWithValue(doc, "companyID", companyID)) {
//                    continue;
//                }
//
//                // ====== FILTER 2 → files ======
//                boolean hasFiles = documentContainsKey(doc, "files");
//
//                if (mustContainFiles != null) {
//                    if (mustContainFiles && !hasFiles) continue;
//                    if (!mustContainFiles && hasFiles) continue;
//                }
//
//                // ====== FILTER 3 → Date range ======
//                if (timeFrom != null || timeTo != null) {
//                    Object timeValue = doc.get("upload_time");
//
//                    if (!(timeValue instanceof Number)) continue;
//                    long uploadTime = ((Number) timeValue).longValue();
//
//                    if (timeFrom != null && uploadTime < timeFrom) continue;
//                    if (timeTo != null && uploadTime > timeTo) continue;
//                }
//
//                // Passed all filters → add result
//                jsonArray.put(new JSONObject(doc.toJson()));
//            }
//        }
//
//        return jsonArray.toString();
//    }


//    private static boolean documentContainsKeyWithValue(Object obj, String keyToFind, String valueToMatch) {
//        if (obj == null) return false;
//
//        if (obj instanceof Document) {
//            Document doc = (Document) obj;
//
//            for (String key : doc.keySet()) {
//                Object value = doc.get(key);
//
//                if (key.equals(keyToFind) && valueToMatch.equals(String.valueOf(value))) {
//                    return true;
//                }
//
//                if (value instanceof Document || value instanceof List) {
//                    if (documentContainsKeyWithValue(value, keyToFind, valueToMatch)) {
//                        return true;
//                    }
//                }
//            }
//        }
//
//        if (obj instanceof List) {
//            for (Object item : (List<?>) obj) {
//                if (documentContainsKeyWithValue(item, keyToFind, valueToMatch)) {
//                    return true;
//                }
//            }
//        }
//
//        return false;
//    }
//
//    private static boolean documentContainsKey(Object obj, String keyToFind) {
//        if (obj == null) return false;
//
//        if (obj instanceof Document) {
//            Document doc = (Document) obj;
//
//            for (String key : doc.keySet()) {
//                Object value = doc.get(key);
//
//                if (key.equals(keyToFind)) {
//                    return true;
//                }
//
//                if (value instanceof Document || value instanceof List) {
//                    if (documentContainsKey(value, keyToFind)) {
//                        return true;
//                    }
//                }
//            }
//        }
//
//        if (obj instanceof List) {
//            for (Object item : (List<?>) obj) {
//                if (documentContainsKey(item, keyToFind)) {
//                    return true;
//                }
//            }
//        }
//        return false;
//    }
}