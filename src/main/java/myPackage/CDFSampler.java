// CDFSampler.java
package myPackage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class CDFSampler {

    private BigDecimal learningRate;  // Tasso di apprendimento configurabile
    private BigDecimal tolerance;     // Tolleranza configurabile

    // Costruttore con valori di default
    public CDFSampler() {
        this.learningRate = new BigDecimal("0.1"); // Valore di default
        this.tolerance = new BigDecimal("0.01");   // Valore di default
    }

    // Costruttore con parametri personalizzati
    public CDFSampler(BigDecimal learningRate, BigDecimal tolerance) {
        if (learningRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Learning rate deve essere maggiore di 0.");
        }
        if (tolerance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Tolerance deve essere maggiore di 0.");
        }
        this.learningRate = learningRate;
        this.tolerance = tolerance;
    }

    // Getter e Setter per learningRate
    public BigDecimal getLearningRate() {
        return learningRate;
    }

    public void setLearningRate(BigDecimal learningRate) {
        if (learningRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Learning rate deve essere maggiore di 0.");
        }
        this.learningRate = learningRate;
    }

    // Getter e Setter per tolerance
    public BigDecimal getTolerance() {
        return tolerance;
    }

    public void setTolerance(BigDecimal tolerance) {
        if (tolerance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Tolerance deve essere maggiore di 0.");
        }
        this.tolerance = tolerance;
    }

    // Metodo principale: valuta e regola i pesi utilizzando la PDF
    public List<BigDecimal> evaluateAndAdjustWeights(List<BigDecimal> interArrivalTimes, List<BigDecimal> weights) {
        if (interArrivalTimes.isEmpty()) {
            throw new IllegalArgumentException("La lista dei tempi di arrivo è vuota.");
        }

        // Calcola CDF e PDF
        List<BigDecimal> cdf = FunctionsCalculator.calculateCDF(interArrivalTimes);
        List<BigDecimal> pdf = FunctionsCalculator.calculatePDF(cdf);

        boolean isConverged = false;
        int maxIterations = 1000;
        int iteration = 0;

        while (!isConverged && iteration < maxIterations) {
            List<BigDecimal> observedWeights = estimateWeightsFromPDF(pdf);
            compareWeights(weights, observedWeights);
            isConverged = adjustWeights(weights, observedWeights);
            iteration++;
        }

        if (!isConverged) {
            System.out.printf("⚠️  Convergenza non raggiunta dopo %d iterazioni.\n", maxIterations);
        } else {
            System.out.printf("✅ Convergenza raggiunta in %d iterazioni.\n", iteration);
        }

        return weights;
    }


    // Stima i pesi osservati direttamente dalla PDF (divide la pdf(i) per tutte le pdf(j)) e stima un peso
    private List<BigDecimal> estimateWeightsFromPDF(List<BigDecimal> pdf) {
        List<BigDecimal> observedWeights = new ArrayList<>();
        BigDecimal total = pdf.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        for (BigDecimal value : pdf) {
            observedWeights.add(value.divide(total, 10, RoundingMode.HALF_UP));
        }
        return observedWeights;
    }

    // Confronta i pesi teorici e osservati, stampandoli
    private void compareWeights(List<BigDecimal> theoreticalWeights, List<BigDecimal> observedWeights) {
        System.out.println("Confronto Teorici vs Osservati:");
        for (int i = 0; i < theoreticalWeights.size(); i++) {
            System.out.println("Peso teorico " + (i + 1) + ": " + theoreticalWeights.get(i) +
                    " | Peso osservato " + (i + 1) + ": " + observedWeights.get(i));
        }
    }

    // Corregge i pesi teorici in base alla differenza con i pesi osservati
    private boolean adjustWeights(List<BigDecimal> theoreticalWeights, List<BigDecimal> observedWeights) {
        boolean isConverged = true;

        for (int i = 0; i < theoreticalWeights.size(); i++) {
            BigDecimal observed = observedWeights.get(i);
            BigDecimal theoretical = theoreticalWeights.get(i);

            // Calcola la differenza
            BigDecimal difference = observed.subtract(theoretical);

            // Correggi il peso teorico usando il tasso di apprendimento
            BigDecimal adjustment = difference.multiply(learningRate);
            theoreticalWeights.set(i, theoretical.add(adjustment));

            // Normalizza per evitare pesi negativi
            if (theoreticalWeights.get(i).compareTo(BigDecimal.ZERO) < 0) {
                theoreticalWeights.set(i, BigDecimal.ZERO);
            }

            // Verifica il criterio di convergenza
            if (difference.abs().compareTo(tolerance) > 0) {
                isConverged = false;
            }
        }

        // Normalizza i pesi per garantire che la somma sia pari a 1
        normalizeWeights(theoreticalWeights);

        return isConverged;
    }

    // Normalizza i pesi in modo che sommino a 1
    private void normalizeWeights(List<BigDecimal> weights) {
        BigDecimal total = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        weights.replaceAll(weight -> weight.divide(total, 10, RoundingMode.HALF_UP));
    }
}
