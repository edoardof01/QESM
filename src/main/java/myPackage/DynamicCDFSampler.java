//DynamicCDFSampler.java

package myPackage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DynamicCDFSampler {

    private BigDecimal learningRate;
    private BigDecimal tolerance;
    private int windowSize;
    private long maxDuration; // Limite di tempo in millisecondi

    private LinkedList<BigDecimal> recentInterArrivalTimes;

    // Costruttore con valori di default
    public DynamicCDFSampler() {
        this.learningRate = new BigDecimal("0.1");
        this.tolerance = new BigDecimal("0.01");
        this.windowSize = 100;
        this.maxDuration = 5000; // Default: 5000 ms (5 secondi)
        this.recentInterArrivalTimes = new LinkedList<>();
    }


    // Costruttore personalizzato
    public DynamicCDFSampler(BigDecimal learningRate, BigDecimal tolerance, int windowSize, long maxDuration) {
        if (learningRate.compareTo(BigDecimal.ZERO) <= 0 || tolerance.compareTo(BigDecimal.ZERO) <= 0 || windowSize <= 0 || maxDuration <= 0) {
            throw new IllegalArgumentException("I parametri devono essere positivi.");
        }
        this.learningRate = learningRate;
        this.tolerance = tolerance;
        this.windowSize = windowSize;
        this.maxDuration = maxDuration;
        this.recentInterArrivalTimes = new LinkedList<>();
    }

    public BigDecimal getLearningRate() {
        return learningRate;
    }
    public void setLearningRate(BigDecimal learningRate) {
        this.learningRate = learningRate;
    }
    public BigDecimal getTolerance() {
        return tolerance;
    }
    public void setTolerance(BigDecimal tolerance) {
        this.tolerance = tolerance;
    }
    public int getWindowSize() {
        return windowSize;
    }
    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }
    public LinkedList<BigDecimal> getRecentInterArrivalTimes() {
        return recentInterArrivalTimes;
    }
    public void setRecentInterArrivalTimes(LinkedList<BigDecimal> recentInterArrivalTimes) {
        this.recentInterArrivalTimes = recentInterArrivalTimes;
    }


    // Aggiunge un nuovo intertempo alla finestra
    public void addInterArrivalTime(BigDecimal interArrivalTime) {
        recentInterArrivalTimes.add(interArrivalTime);

        // Rimuove l'elemento più vecchio se la finestra supera la dimensione massima
        if (recentInterArrivalTimes.size() > windowSize) {
            recentInterArrivalTimes.pollFirst();
        }
    }

    // Aggiorna i pesi dinamicamente sulla base dei dati nella finestra
    public List<BigDecimal> updateWeights(List<BigDecimal> weights) {
        if (recentInterArrivalTimes.isEmpty()) {
            throw new IllegalStateException("Non ci sono dati sufficienti per aggiornare i pesi.");
        }

        long startTime = System.currentTimeMillis(); // Inizia il timer

        // Calcola la CDF
        List<BigDecimal> cdf = FunctionsCalculator.calculateCDF(new ArrayList<>(recentInterArrivalTimes));
        List<BigDecimal> pdf = FunctionsCalculator.calculatePDF(cdf);

        // Definisce i quantili di riferimento
        BigDecimal[] quantiles = { BigDecimal.valueOf(0.25), BigDecimal.valueOf(0.50), BigDecimal.valueOf(0.75), BigDecimal.valueOf(1.00) };

        // Campiona i quantili
        List<BigDecimal> sampledQuantiles = sampleQuantiles(cdf, quantiles);

        // Stima i pesi osservati
        List<BigDecimal> observedWeights = estimateWeights(sampledQuantiles);

        // Verifica se il tempo massimo è stato superato
        if (System.currentTimeMillis() - startTime > maxDuration) {
            System.out.println("Interruzione: Tempo massimo superato.");
            return weights; // Restituisce i pesi correnti senza ulteriori modifiche
        }

        // Corregge i pesi teorici in base a quelli osservati
        adjustWeights(weights, observedWeights);

        return weights;
    }

    // Campiona i quantili dalla CDF
    private List<BigDecimal> sampleQuantiles(List<BigDecimal> cdf, BigDecimal[] quantiles) {
        List<BigDecimal> sampledQuantiles = new ArrayList<>();
        for (BigDecimal quantile : quantiles) {
            sampledQuantiles.add(sampleCDFAtQuantile(cdf, quantile));
        }
        return sampledQuantiles;
    }

    private BigDecimal sampleCDFAtQuantile(List<BigDecimal> cdf, BigDecimal quantile) {
        for (int i = 0; i < cdf.size(); i++) {
            if (cdf.get(i).compareTo(quantile) >= 0) {
                return BigDecimal.valueOf(i);
            }
        }
        return BigDecimal.ZERO;
    }

    private List<BigDecimal> estimateWeights(List<BigDecimal> sampledQuantiles) {
        List<BigDecimal> observedWeights = new ArrayList<>();
        BigDecimal total = sampledQuantiles.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        for (BigDecimal sample : sampledQuantiles) {
            observedWeights.add(sample.divide(total, RoundingMode.HALF_UP));
        }
        return observedWeights;
    }

    private void adjustWeights(List<BigDecimal> theoreticalWeights, List<BigDecimal> observedWeights) {
        for (int i = 0; i < theoreticalWeights.size(); i++) {
            BigDecimal observed = observedWeights.get(i);
            BigDecimal theoretical = theoreticalWeights.get(i);
            BigDecimal difference = observed.subtract(theoretical);
            BigDecimal adjustment = difference.multiply(learningRate);

            theoreticalWeights.set(i, theoretical.add(adjustment));

            if (theoreticalWeights.get(i).compareTo(BigDecimal.ZERO) < 0) {
                theoreticalWeights.set(i, BigDecimal.ZERO);
            }
        }
        normalizeWeights(theoreticalWeights);
    }

    private void normalizeWeights(List<BigDecimal> weights) {
        BigDecimal total = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        weights.replaceAll(weight -> weight.divide(total, RoundingMode.HALF_UP));
    }

    // Setter per modificare il limite di tempo
    public void setMaxDuration(long maxDuration) {
        if (maxDuration <= 0) {
            throw new IllegalArgumentException("Il tempo massimo deve essere positivo.");
        }
        this.maxDuration = maxDuration;
    }

    // Getter per ottenere il limite di tempo
    public long getMaxDuration() {
        return maxDuration;
    }
}
