package myPackage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * DynamicCDFSampler: aggiorna i pesi W₁…Wₙ usando una sliding window
 * di inter-arrival times (fino a windowSize) e un fit basato su quantili.

 * Workflow:
 *  1. Mantiene gli ultimi `windowSize` inter-arrivals.
 *  2. Calcola la CDF empirica di questi valori.
 *  3. Estrae i 4 quantili (25%, 50%, 75%, 100%) come sample list.
 *  4. Invoca CDFSampler.evaluateAndAdjustWeights(sampleList, weights)
 *     per ottenere i pesi aggiornati.
 */
public class DynamicCDFSampler {
    private static final BigDecimal[] QUANTILES = {
            new BigDecimal("0.25"),
            new BigDecimal("0.50"),
            new BigDecimal("0.75"),
            new BigDecimal("1.00")
    };

    private final CDFSampler sampler;
    private final int windowSize;
    private final LinkedList<BigDecimal> recentInterArrivals = new LinkedList<>();

    /**
     * @param learningRate tasso di apprendimento per l'update
     * @param tolerance    tolleranza di convergenza
     * @param windowSize   massimi campioni da mantenere in memoria
     */
    public DynamicCDFSampler(BigDecimal learningRate,
                             BigDecimal tolerance,
                             int windowSize) {
        if (learningRate.compareTo(BigDecimal.ZERO) <= 0
                || tolerance.compareTo(BigDecimal.ZERO) <= 0
                || windowSize <= 0) {
            throw new IllegalArgumentException(
                    "learningRate, tolerance e windowSize devono essere > 0"
            );
        }
        this.sampler    = new CDFSampler(learningRate, tolerance, false);
        this.windowSize = windowSize;
    }

    /**
     * Aggiunge un nuovo inter-arrival time alla finestra.
     * Se si supera windowSize, elimina il più vecchio.
     */
    public void addInterArrivalTime(BigDecimal interArrivalTime) {
        recentInterArrivals.addLast(interArrivalTime);
        if (recentInterArrivals.size() > windowSize) {
            recentInterArrivals.pollFirst();
        }
    }

    /**
     * Aggiorna i pesi W₁…Wₙ usando i quantili degli inter-arrivals
     * nella finestra corrente.
     *
     * @param weights lista corrente di pesi (deve avere size == 4 per BPH₄)
     * @return lista di pesi aggiornata
     */
    public List<BigDecimal> updateWeights(List<BigDecimal> weights) {
        if (recentInterArrivals.isEmpty()) {
            throw new IllegalStateException(
                    "Nessun dato disponibile per l'aggiornamento."
            );
        }
        // 1) copia e ordina
        List<BigDecimal> sorted = new ArrayList<>(recentInterArrivals);
        Collections.sort(sorted);

        // 2) calcola CDF su 'sorted'
        List<BigDecimal> cdf = FunctionsCalculator.calculateCDF(sorted);

        // 3) estrae i 4 sample corrispondenti ai quantili
        List<BigDecimal> quantileSamples = new ArrayList<>(QUANTILES.length);
        for (BigDecimal q : QUANTILES) {
            // trova il primo indice i con cdf[i] >= q
            int idx = 0;
            while (idx < cdf.size() && cdf.get(idx).compareTo(q) < 0) {
                idx++;
            }
            // se idx == size, prendo l'ultimo elemento
            if (idx >= sorted.size()) {
                idx = sorted.size() - 1;
            }
            quantileSamples.add(sorted.get(idx));
        }

        // 4) delega a CDFSampler: calcola CDF/PDF dei 4 sample e aggiorna i pesi
        return sampler.evaluateAndAdjustWeights(quantileSamples, weights);
    }
}
