import static spark.Spark.delete;
import static spark.Spark.get;
import static spark.Spark.post;

public class Routes {

    public static void routesList(){

        //Save Data blob and JSON in unique file , handle multipart or JSON raw in unique end point
        post("/saveData", RoutesCore::saveData);

        get("/getData", RoutesCore::getData);

        // Validation from ID
        get("/getValidationID", RoutesCore::getValidationID);

        // Validation from JSON
        get("/getValidationJson", RoutesCore::getValidationJson);

        // GET JSON from ID
        get("/getJsonFromID", RoutesCore::getJsonFromID);

        // GET JSON from parameter X
        get("/search", RoutesCore::search);

            /* **********************************
                    REMOVE BEFORE PRODUCTION
                **********************************/

        // GET ALL n last entries without specify data or companyID
        get("/getAllEntriesTEST", RoutesCore::getDataTEST);

        delete("/deleteLuce", RoutesCore::deleteLuce);

        post("/addSchema" , RoutesCore::addSchema);

        get("/getSchema" , RoutesCore::getSchema);

        delete("/deleteSchema", RoutesCore::deleteSchema);
    }
}
