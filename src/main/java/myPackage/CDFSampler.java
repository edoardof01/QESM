
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


        List<BigDecimal> copy = new ArrayList<>(interArrivalTimes);
        Collections.sort(copy);
        List<BigDecimal> cdf = FunctionsCalculator.calculateCDF(copy);
        List<BigDecimal> pdf = FunctionsCalculator.calculatePDF(cdf);

        if (pdf.size() != nW) {
            // percentili di cut (es: [0%, 60%, 80%, 90%, 100%])
            BigDecimal[] percentiles = {
                    BigDecimal.ZERO,
                    new BigDecimal("0.60"),
                    new BigDecimal("0.80"),
                    new BigDecimal("0.90"),
                    BigDecimal.ONE
            };
            pdf = aggregatePdfByPercentiles(pdf, copy, percentiles);
            if (verbose) {
                System.out.printf("⚠️  PDF aggregata per percentili: %s%n", List.of(percentiles));
            }
        }

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

    private List<BigDecimal> aggregatePdfByPercentiles(List<BigDecimal> pdf, List<BigDecimal> originalValues, BigDecimal[] percentiles) {
        int n = percentiles.length - 1;
        List<BigDecimal> buckets = new ArrayList<>(Collections.nCopies(n, BigDecimal.ZERO));

        List<BigDecimal> sorted = new ArrayList<>(originalValues);
        Collections.sort(sorted);
        List<BigDecimal> cutoffs = new ArrayList<>();
        for (BigDecimal p : percentiles) {
            int idx = p.multiply(new BigDecimal(sorted.size())).intValue();
            if (idx >= sorted.size()) idx = sorted.size() - 1;
            cutoffs.add(sorted.get(idx));
        }

        for (int i = 0; i < sorted.size(); i++) {
            BigDecimal value = sorted.get(i);
            BigDecimal prob = pdf.get(i);

            for (int j = 0; j < n; j++) {
                if (value.compareTo(cutoffs.get(j)) >= 0 && value.compareTo(cutoffs.get(j + 1)) <= 0) {
                    buckets.set(j, buckets.get(j).add(prob));
                    break;
                }
            }
        }

        return buckets;
    }

    public List<BigDecimal> updateWithObservedPdf(
            List<BigDecimal> observedPdf,
            List<BigDecimal> weights) {

        if (observedPdf.size() != weights.size()) {
            throw new IllegalArgumentException("PDF e pesi devono avere la stessa lunghezza");
        }

        boolean isConverged = false;
        int maxIt = 1000, it = 0;
        List<BigDecimal> observed = estimateWeightsFromPDF(observedPdf);

        while (!isConverged && it < maxIt) {
            if (verbose && (it == 0 || it % 200 == 0 || it == maxIt - 1)) {
                System.out.printf("[Iter %4d] theor=%s | obs=%s%n", it, weights, observed);
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


}
