package myPackage;

import java.math.BigDecimal;
import java.util.*;

/**
 * DynamicCDFSampler: aggiorna i pesi W₁…Wₙ usando una sliding window
 * e PDF aggregata per percentili (es: [0–60%, 60–80%, 80–90%, 90–100%]).
 * Ora aggiorna automaticamente i pesi ogni N inter-arrivi (N=10).
 */
public class DynamicCDFSampler {
    private static final BigDecimal[] DEFAULT_PERCENTILES = {
            BigDecimal.ZERO,
            new BigDecimal("0.50"),
            new BigDecimal("0.70"),
            new BigDecimal("0.90"),
            BigDecimal.ONE
    };

    private final CDFSampler sampler;
    private final int windowSize;
    private final int updateFrequency;
    private final LinkedList<BigDecimal> recentInterArrivals = new LinkedList<>();
    private int arrivalsSinceLastUpdate = 0;
    private final boolean verbose;

    public DynamicCDFSampler(BigDecimal learningRate,
                             BigDecimal tolerance,
                             int windowSize,
                             boolean verbose) {
        if (learningRate.compareTo(BigDecimal.ZERO) <= 0
                || tolerance.compareTo(BigDecimal.ZERO) <= 0
                || windowSize <= 0) {
            throw new IllegalArgumentException(
                    "learningRate, tolerance e windowSize devono essere >, 0"
            );
        }
        this.sampler = new CDFSampler(learningRate, tolerance, verbose);
        this.windowSize = windowSize;
        this.updateFrequency = 10;
        this.verbose = verbose;
    }

    /**
     * Valuta e aggiorna i pesi usando una lista di inter-arrivi.
     * Questo metodo è l'interfaccia principale per il main loop.
     */
    public List<BigDecimal> evaluateAndAdjustWeights(List<BigDecimal> interArrivals, List<BigDecimal> weights) {
        // Step 1: Aggiungi tutti i nuovi inter-arrivi alla sliding window
        for (BigDecimal interArrival : interArrivals) {
            addInterArrivalTime(interArrival);
        }
        // Step 2: Aggiorna i pesi utilizzando i dati della sliding window
        // Usa il metodo esistente `updateWeights`
        return updateWeights(weights);
    }

    public void addInterArrivalTime(BigDecimal interArrivalTime) {
        recentInterArrivals.addLast(interArrivalTime);
        if (recentInterArrivals.size() > windowSize) {
            recentInterArrivals.pollFirst();
        }
        arrivalsSinceLastUpdate++;
    }

    /** Metodo esplicito per aggiornare i pesi (es., alla fine di un round) */
    public List<BigDecimal> updateWeights(List<BigDecimal> weights) {
        if (recentInterArrivals.isEmpty()) {
            throw new IllegalStateException("Nessun dato disponibile per l'aggiornamento.");
        }

        List<BigDecimal> sorted = new ArrayList<>(recentInterArrivals);
        Collections.sort(sorted);

        if (verbose) {
            System.out.println("📊 Inter-arrival times (sorted):");
            for (BigDecimal val : sorted) {
                System.out.print(val + " ");
            }
            System.out.println();
        }
        List<BigDecimal> cdf = FunctionsCalculator.calculateCDF(sorted);
        List<BigDecimal> pdf = FunctionsCalculator.calculatePDF(cdf);

        List<BigDecimal> aggregated = aggregatePdfByPercentiles(pdf, sorted);

        if (verbose) {
            System.out.println("📈 PDF aggregata per percentili:");
            for (int i = 0; i < aggregated.size(); i++) {
                BigDecimal percStart = DEFAULT_PERCENTILES[i].multiply(new BigDecimal("100"));
                BigDecimal percEnd = DEFAULT_PERCENTILES[i + 1].multiply(new BigDecimal("100"));
                System.out.printf("  [%s%% - %s%%]: %s%n", percStart, percEnd, aggregated.get(i));
            }
        }

        List<BigDecimal> updatedWeights = sampler.updateWithObservedPdf(aggregated, weights);
        arrivalsSinceLastUpdate = 0;

        return updatedWeights;
    }

    /** Controlla se si deve aggiornare automaticamente */
    public boolean shouldUpdateWeights() {
        return arrivalsSinceLastUpdate >= updateFrequency;
    }

    /** Aggrega la PDF in bucket secondo i percentili */
    private List<BigDecimal> aggregatePdfByPercentiles(List<BigDecimal> pdf,
                                                       List<BigDecimal> originalValues) {
        int n = DEFAULT_PERCENTILES.length - 1;
        List<BigDecimal> buckets = new ArrayList<>(Collections.nCopies(n, BigDecimal.ZERO));

        List<BigDecimal> sorted = new ArrayList<>(originalValues);
        Collections.sort(sorted);
        List<BigDecimal> cutoffs = new ArrayList<>();
        for (BigDecimal p : DEFAULT_PERCENTILES) {
            int idx = p.multiply(new BigDecimal(sorted.size())).intValue();
            if (idx >= sorted.size()) idx = sorted.size() - 1;
            cutoffs.add(sorted.get(idx));
        }

        for (int i = 0; i < sorted.size(); i++) {
            BigDecimal value = sorted.get(i);
            BigDecimal prob = pdf.get(i);

            for (int j = 0; j < n; j++) {
                if (value.compareTo(cutoffs.get(j)) >= 0 &&
                        value.compareTo(cutoffs.get(j + 1)) <= 0) {
                    buckets.set(j, buckets.get(j).add(prob));
                    break;
                }
            }
        }

        return buckets;
    }
}