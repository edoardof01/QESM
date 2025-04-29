
package myPackage;

import org.oristool.analyzer.Succession;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.rewards.DiscreteRewardTime;
import org.oristool.simulator.rewards.Reward;
import org.oristool.simulator.rewards.RewardObserver;
import org.oristool.simulator.rewards.RewardTime;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class InterarrivalCollectorReward implements Reward {
    private final Sequencer sequencer;
    private final List<BigDecimal> arrivalTimes = new ArrayList<>();
    private final List<RewardObserver> observers = new ArrayList<>();
    private final Map<String, List<BigDecimal>> arrivalTimesByType = new HashMap<>();
    private final Map<String, Integer> arrivalCount = new HashMap<>();

    //  per aggiornare i pesi ogni 10 inter-arrivi
    private final DynamicCDFSampler dynamicSampler;
    private List<BigDecimal> weights;

    public InterarrivalCollectorReward(Sequencer sequencer,
                                       DynamicCDFSampler dynamicSampler,
                                       List<BigDecimal> weights) {
        this.sequencer = sequencer;
        this.dynamicSampler = dynamicSampler;
        this.weights = weights;
        this.sequencer.addCurrentRunObserver(this);
    }

    @Override
    public Sequencer getSequencer() {
        return sequencer;
    }

    @Override
    public RewardTime getRewardTime() {
        return new DiscreteRewardTime();
    }

    @Override
    public Object evaluate() {
        return null; // non usato
    }

    @Override
    public void addObserver(RewardObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(RewardObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        for (var o : observers) {
            o.update(RewardEvent.RUN_END);
        }
    }

    @Override
    public void update(Sequencer.SequencerEvent event) {
        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            Succession last = sequencer.getLastSuccession();
            if (last != null && last.getEvent().getName().startsWith("arrival")) {
                String name = last.getEvent().getName();
                BigDecimal time = sequencer.getCurrentRunElapsedTime();

                arrivalTimes.add(time);
                arrivalTimesByType.putIfAbsent(name, new ArrayList<>());
                arrivalTimesByType.get(name).add(time);

                arrivalCount.put(name, arrivalCount.getOrDefault(name, 0) + 1);

                // Gestione aggiornamento dinamico
                if (dynamicSampler != null) {
                    if (!arrivalTimes.isEmpty()) {
                        int n = arrivalTimes.size();
                        if (n >= 2) {
                            BigDecimal delta = arrivalTimes.get(n - 1).subtract(arrivalTimes.get(n - 2));
                            dynamicSampler.addInterArrivalTime(delta);

                            if (dynamicSampler.shouldUpdateWeights()) {
                                weights = dynamicSampler.updateWeights(weights);
                                System.out.println("🔄 Pesi aggiornati dinamicamente dopo 10 inter-arrivi.");
                                System.out.println("📈 Nuovi pesi: " + weights);
                            }
                        }
                    }
                }
            }
        }
        notifyObservers();
    }

    /**  Per salvare il grafico della CDF */
    public void reportCDF(String outputPngPath) {
        if (arrivalTimes.size() < 2) {
            System.out.println("⚠️  Pochi arrivi per calcolare inter-arrival.");
            return;
        }

        List<BigDecimal> inters = new ArrayList<>();
        for (int i = 1; i < arrivalTimes.size(); i++) {
            inters.add(arrivalTimes.get(i).subtract(arrivalTimes.get(i - 1)));
        }

        List<BigDecimal> cdf = FunctionsCalculator.calculateCDF(inters);

        XYSeries series = new XYSeries("Empirical CDF");
        for (int i = 0; i < inters.size(); i++) {
            series.add(
                    inters.get(i).doubleValue(),
                    cdf.get(i).doubleValue()
            );
        }
        var dataset = new XYSeriesCollection(series);

        JFreeChart chart = ChartFactory.createXYStepChart(
                "Empirical CDF of Interarrival Times",
                "Interarrival Time",
                "CDF",
                dataset
        );

        try {
            ChartUtils.saveChartAsPNG(
                    new File(outputPngPath),
                    chart,
                    800, 600
            );
            System.out.println("✅ Grafico CDF salvato in: " + outputPngPath);
        } catch (Exception e) {
            System.err.println("❌ Errore salvando la CDF: " + e.getMessage());
        }
    }

    /** Per stampare statistiche di arrivo */
    public void reportArrivalStats() {
        int totalArrivals = arrivalCount.values().stream().mapToInt(i -> i).sum();

        System.out.println("\n==== ARRIVAL STATS ====");
        for (var entry : arrivalTimesByType.entrySet()) {
            String label = entry.getKey();
            List<BigDecimal> times = entry.getValue();
            if (times.size() < 2) continue;

            List<BigDecimal> inters = new ArrayList<>();
            for (int i = 1; i < times.size(); i++) {
                inters.add(times.get(i).subtract(times.get(i - 1)));
            }

            BigDecimal sum = inters.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(inters.size()), 6, RoundingMode.HALF_UP);

            double perc = 100.0 * arrivalCount.get(label) / (double) totalArrivals;

            System.out.printf("%s: count=%d (%.2f%%), avg interarrival = %.4f%n",
                    label, arrivalCount.get(label), perc, avg.doubleValue());
        }
    }
}
