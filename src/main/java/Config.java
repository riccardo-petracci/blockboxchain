import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private static final Dotenv dotenv = Dotenv.load();

    public static String getEnvVariable(String key) {
        return dotenv.get(key);
    }

    public static int getInt(String key) {
        String value = dotenv.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Errore nel parsing del valore numerico per la chiave: " + key);
            }
        }
        throw new RuntimeException("Chiave non trovata nel .env: " + key);
    }
}
