package myPackage;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulationResult {
    public int round;
    public String mode;
    public double abbandono;
    public double blocco;
    public double utilizzo;
    public List<BigDecimal> weights;
    public Map<String, String> images;

    public SimulationResult(int round, String mode,
                            double abbandono, double blocco, double utilizzo,
                            List<BigDecimal> weights, String cdf, String hist, String fit) {
        this.round = round;
        this.mode = mode;
        this.abbandono = abbandono;
        this.blocco = blocco;
        this.utilizzo = utilizzo;
        this.weights = weights;
        this.images = new HashMap<>();
        this.images.put("cdf", cdf);
        this.images.put("hist", hist);
        this.images.put("fit", fit);
    }
}
