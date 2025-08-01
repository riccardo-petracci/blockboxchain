import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.everit.json.schema.ValidationException;
import org.json.JSONObject;
import org.json.JSONException;

public class SchemaValidator {

    private final Schema schema;

    /**
     * Constructor: builds a SchemaValidator from a MongoDB schema document
     * @param schemaJson a JSONObject containing the JSON schema
     * @throws IllegalArgumentException if schema is invalid
     */
    public SchemaValidator(JSONObject schemaJson) {
        try {
            SchemaLoader loader = SchemaLoader.builder()
                    .schemaJson(schemaJson)
//                    .draftV7Support() // optional: enable Draft-07 support
                    .build();
            this.schema = loader.load().build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON Schema", e);
        }
    }

    /**
     * Validates a JSON payload against the loaded schema.
     * @param data the JSON object to validate
     * @throws ValidationException if validation fails
     */
    public void validate(JSONObject data) throws ValidationException {
        schema.validate(data);
    }

    /**
     * Utility method to check validity and return error messages instead of throwing
     */
    public static String getValidationErrors(JSONObject schemaJson, JSONObject dataJson) {
        try {
            SchemaValidator validator = new SchemaValidator(schemaJson);
            validator.validate(dataJson);
            return null; // valid
        } catch (ValidationException e) {
            return String.join("; ", e.getAllMessages());
        } catch (Exception ex) {
            return "Schema loading error: " + ex.getMessage();
        }
    }

}
