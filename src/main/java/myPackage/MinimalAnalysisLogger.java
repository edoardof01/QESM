//MinimalAnalysisLogger.java
package myPackage;
import org.oristool.analyzer.log.AnalysisLogger;


public class MinimalAnalysisLogger implements AnalysisLogger {

    @Override
    public void log(String message) {
        // Stampa il messaggio su console
        System.out.println("[LOG] " + message);
    }

    @Override
    public void debug(String message) {
        // Stampa solo i messaggi di debug su console
        System.out.println("[DEBUG] " + message);
    }

    public void info(String message) {
        // Stampa i messaggi informativi su console
        System.out.println("[INFO] " + message);
    }

    public void warn(String message) {
        // Stampa i messaggi di avviso su console
        System.out.println("[WARN] " + message);
    }

    public void error(String message) {
        // Stampa i messaggi di errore su console
        System.err.println("[ERROR] " + message);
    }

    public void fatal(String message) {
        // Stampa i messaggi fatali su console
        System.err.println("[FATAL] " + message);
    }
}
