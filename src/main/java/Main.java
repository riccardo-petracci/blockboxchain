import static spark.Spark.*;
import java.io.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        try {
            MongoDBConnection.checkInitialization();
            port(Config.getInt("SERVER_PORT"));
            Routes.routesList();
            logger.info("Server avviato sulla porta {}", Config.getInt("SERVER_PORT"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}