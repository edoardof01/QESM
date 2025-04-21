package myPackage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CDFSampler {

    private final BigDecimal learningRate;  // Tasso di apprendimento configurabile
    private final BigDecimal tolerance;     // Tolleranza configurabile
    private final boolean verbose;          // Per abilitare log

    public CDFSampler() {
        this.learningRate = new BigDecimal("0.1");
        this.tolerance    = new BigDecimal("0.01");
        this.verbose      = false;
    }

    public CDFSampler(BigDecimal learningRate, BigDecimal tolerance, boolean verbose) {
        if (learningRate.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Learning rate deve essere > 0.");
        if (tolerance.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Tolerance deve essere > 0.");
        this.learningRate = learningRate;
        this.tolerance    = tolerance;
        this.verbose      = verbose;
    }

    public List<BigDecimal> evaluateAndAdjustWeights(
            List<BigDecimal> interArrivalTimes,
            List<BigDecimal> weights) {

        if (interArrivalTimes.isEmpty())
            throw new IllegalArgumentException("La lista dei tempi di arrivo è vuota.");
        int nW = weights.size();

        // 1) Copia + sort + CDF/PDF
        List<BigDecimal> copy = new ArrayList<>(interArrivalTimes);
        Collections.sort(copy);
        List<BigDecimal> cdf = FunctionsCalculator.calculateCDF(copy);
        List<BigDecimal> pdf = FunctionsCalculator.calculatePDF(cdf);

        // 2) Se la PDF ha lunghezza diversa dal numero di pesi, la “riduciamo” in nW bucket
        if (pdf.size() != nW) {
            pdf = aggregatePdf(pdf, nW);
            if (verbose) {
                System.out.printf("⚠️  Aggreghiamo PDF da %d a %d elementi%n",
                        interArrivalTimes.size(), nW);
            }
        }

        // 3) Ora pdf.size() == weights.size(), possiamo continuare come prima
        boolean isConverged = false;
        int maxIt = 1000, it = 0;
        while (!isConverged && it < maxIt) {
            List<BigDecimal> observed = estimateWeightsFromPDF(pdf);
            if (verbose && (it == 0 || it % 200 == 0 || it == maxIt -1)) {
                System.out.printf("[Iter %4d] theor=%s | obs=%s%n",
                        it, weights, observed);
            }
            isConverged = adjustWeights(weights, observed);
            it++;
        }
        if (verbose) {
            if (isConverged)
                System.out.printf("✅ Convergenza in %d it.%n", it);
            else
                System.out.printf("⚠️  No conv. dopo %d it.%n", maxIt);
        }
        return weights;
    }

    /** Raggruppa la pdf di lunghezza M in k bucket, sommando i valori. */
    private List<BigDecimal> aggregatePdf(List<BigDecimal> pdf, int k) {
        List<BigDecimal> agg = new ArrayList<>(Collections.nCopies(k, BigDecimal.ZERO));
        int M = pdf.size();
        for (int i = 0; i < M; i++) {
            // mappa l’indice i in un bucket [0..k-1]
            int bucket = (int)((long)i * k / M);
            if (bucket >= k) bucket = k-1;
            agg.set(bucket, agg.get(bucket).add(pdf.get(i)));
        }
        return agg;
    }

    private List<BigDecimal> estimateWeightsFromPDF(List<BigDecimal> pdf) {
        BigDecimal tot = pdf.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> obs = new ArrayList<>(pdf.size());
        for (BigDecimal v : pdf) {
            obs.add(v.divide(tot, 6, RoundingMode.HALF_UP));
        }
        return obs;
    }

    private boolean adjustWeights(List<BigDecimal> theor, List<BigDecimal> obs) {
        boolean conv = true;
        int n = theor.size();
        // update + clipping
        for (int i = 0; i < n; i++) {
            BigDecimal diff = obs.get(i).subtract(theor.get(i));
            BigDecimal upd  = theor.get(i).add(diff.multiply(learningRate));
            theor.set(i, upd.max(BigDecimal.ZERO));
            if (diff.abs().compareTo(tolerance) > 0) conv = false;
        }
        // normalize Σ=1
        BigDecimal sum = theor.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal uni = BigDecimal.ONE.divide(new BigDecimal(n), 6, RoundingMode.HALF_UP);
            for (int i = 0; i < n; i++) theor.set(i, uni);
        } else {
            for (int i = 0; i < n; i++) {
                theor.set(i, theor.get(i)
                        .divide(sum, 6, RoundingMode.HALF_UP));
            }
        }
        return conv;
    }
}
