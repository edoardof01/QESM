package myPackage;

import com.google.gson.GsonBuilder;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.oristool.simulator.rewards.Reward;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        // Scegli modalità
        if (args.length == 0 ||
                (!args[0].equalsIgnoreCase("static") && !args[0].equalsIgnoreCase("dynamic"))) {
            System.out.println("❗ Devi specificare 'static' o 'dynamic' come argomento.");
            return;
        }
        boolean useDynamicMode = args[0].equalsIgnoreCase("dynamic");
        String mode = useDynamicMode ? "dynamic" : "static";

        // Prepara cartella di output
        File outDir = new File("output");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        // Parametri base
        int queueSize = 8;
        int poolSize = 8;
        int rounds = 1;
        if (args.length >= 2) {
            try {
                rounds = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("❗ Numero di round non valido, uso 1.");
            }
        }

        // Pesi iniziali normalizzati
        List<BigDecimal> weights = new ArrayList<>(List.of(
                new BigDecimal("0.9"),
                new BigDecimal("0.05"),
                new BigDecimal("0.03"),
                new BigDecimal("0.02")
        ));

        // Loop principale
        for (int round = 1; round <= rounds; round++) {
            System.out.println("\n==== ROUND " + round + " (" + mode + ") ====");

            // --- Setup simulazione ---
            SimulationSetup setup = new SimulationSetup(weights, queueSize, poolSize);
            var sequencer = setup.getSequencer();

            // Rewards
            var abandonReward = new AbandonRateReward(sequencer);
            var blockReward = new BlockProbabilityReward(sequencer);
            var utilizationReward = new ServiceUtilizationReward(sequencer, poolSize);
            List<Reward> observers = List.of(abandonReward, blockReward, utilizationReward);

            // Crea il sampler e il collector per questo round
            CDFSampler sampler;
            DynamicCDFSampler dynamicSampler = null;
            if (useDynamicMode) {
                sampler = new CDFSampler(
                        new BigDecimal("0.2"),
                        new BigDecimal("0.02"),
                        true);
                dynamicSampler = new DynamicCDFSampler(
                        new BigDecimal("0.2"),
                        new BigDecimal("0.02"),
                        30,
                        true);
            } else {
                sampler = new CDFSampler(
                        new BigDecimal("0.2"),
                        new BigDecimal("0.02"),
                        true);
            }

            // `weights` is passed so the collector can report the weights used for this round
            var arrivalCollector = new InterarrivalCollectorReward(sequencer, dynamicSampler, weights);

            // Tempo massimo simulazione
            new MaxSimulationTimeReward(sequencer, new BigDecimal("100.0"), observers);

            // Simulazione
            sequencer.simulate();

            // Statistiche
            arrivalCollector.reportArrivalStats();
            double abbandono = (double) abandonReward.evaluate();
            double blocco = (double) blockReward.evaluate();
            double utilizzo = (double) utilizationReward.evaluate();

            System.out.printf("Abbandono: %.4f%n", abbandono);
            System.out.printf("Blocco:    %.4f%n", blocco);
            System.out.printf("Utilizzo:  %.4f%n", utilizzo);

            // --- Esporta JSON dei risultati ---
            SimulationResult result = new SimulationResult(
                    round, mode, abbandono, blocco, utilizzo,
                    new ArrayList<>(weights),
                    "cdf_round" + round + ".png",
                    "interarrival_hist_round" + round + ".png",
                    "bph_fit_chart_round" + round + ".png"
            );
            try (FileWriter writer = new FileWriter(new File(outDir, "round_" + round + "_results.json"))) {
                new GsonBuilder().setPrettyPrinting().create().toJson(result, writer);
            }
            System.out.println("📄 JSON salvato: output/round_" + round + "_results.json");

            // --- Grafico CDF empirica ---
            arrivalCollector.reportCDF(new File(outDir, "cdf_round" + round + ".png").getPath());

            // --- Istogramma inter-arrival ---
            var arrivalTimes = arrivalCollector.getArrivalTimes();
            List<BigDecimal> interArrivals = new ArrayList<>();
            for (int i = 1; i < arrivalTimes.size(); i++) {
                BigDecimal delta = arrivalTimes.get(i).subtract(arrivalTimes.get(i - 1));
                interArrivals.add(delta);
            }
            plotInterarrivalHistogram(interArrivals, 20,
                    new File(outDir, "interarrival_hist_round" + round + ".png").getPath());

            // --- Update PESI e grafico BPH ---
            if (interArrivals.isEmpty()) {
                System.out.println("⚠️ Nessun intertempo per aggiornare i pesi.");
            } else {
                // Aggiorna i pesi per il prossimo round
                if (!useDynamicMode) {
                    sampler.evaluateAndAdjustWeights(interArrivals, weights);
                } else {
                    System.out.println("Modalità dinamica: pesi aggiornati automaticamente durante la simulazione");
                }

                System.out.println("\n==== PESI AGGIORNATI ====");
                for (int i = 0; i < weights.size(); i++) {
                    System.out.printf("W%d = %.4f%n", i + 1, weights.get(i));
                }

                List<BigDecimal> pdfAggregata = sampler.evaluateAndAdjustWeights(interArrivals, new ArrayList<>(weights));
                plotBPH(pdfAggregata, weights,
                        new File(outDir, "bph_fit_chart_round" + round + ".png").getPath());
            }
        }
    }

    public static void plotBPH(List<BigDecimal> pdfAggregata, List<BigDecimal> pesiBPH, String filename) throws IOException {
        XYSeries pdfSeries = new XYSeries("PDF aggregata");
        int n = pdfAggregata.size();
        for (int i = 0; i < n; i++) {
            double x = (i + 0.5) / n;
            pdfSeries.add(x, pdfAggregata.get(i).doubleValue());
        }

        XYSeries bphSeries = new XYSeries("Bernstein PDF");
        int resolution = 200;
        for (int j = 0; j <= resolution; j++) {
            double x = j / (double) resolution;
            double fx = bernsteinPDF(x, pesiBPH);
            bphSeries.add(x, fx);
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(pdfSeries);
        dataset.addSeries(bphSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "BPH Fit",
                "x (normalizzato)",
                "f(x)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false
        );

        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesLinesVisible(0, false);
        renderer.setSeriesShapesVisible(0, true);  // PDF aggregata: solo punti

        renderer.setSeriesLinesVisible(1, true);
        renderer.setSeriesShapesVisible(1, false); // BPH: solo linea

        plot.setRenderer(renderer);
        ChartUtils.saveChartAsPNG(new File(filename), chart, 800, 600);
        System.out.println("✅ Grafico salvato come " + filename);
    }

    private static double bernsteinPDF(double x, List<BigDecimal> weights) {
        int n = weights.size();
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += weights.get(i).doubleValue() * binomial(n - 1, i) *
                    Math.pow(x, i) * Math.pow(1 - x, n - 1 - i);
        }
        return sum;
    }

    private static long binomial(int n, int k) {
        if (k < 0 || k > n) return 0;
        if (k == 0 || k == n) return 1;
        long res = 1;
        for (int i = 1; i <= k; i++) {
            res = res * (n - (k - i)) / i;
        }
        return res;
    }

    public static void plotInterarrivalHistogram(List<BigDecimal> interArrivals, int buckets, String filename) throws IOException {
        if (interArrivals.isEmpty()) return;

        //  Trova min e max per normalizzazione
        BigDecimal min = Collections.min(interArrivals);
        BigDecimal max = Collections.max(interArrivals);
        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) == 0) range = BigDecimal.ONE;

        //  Inizializza bucket
        int[] histogram = new int[buckets];
        for (BigDecimal val : interArrivals) {
            BigDecimal normalized = val.subtract(min).divide(range, 6, RoundingMode.HALF_UP);
            int index = normalized.multiply(BigDecimal.valueOf(buckets)).intValue();
            if (index >= buckets) index = buckets - 1;
            histogram[index]++;
        }

        //  Costruisci la serie
        XYSeries histSeries = new XYSeries("Distribuzione inter-arrivi");
        for (int i = 0; i < buckets; i++) {
            double x = (i + 0.5) / buckets; // centro del bucket
            histSeries.add(x, histogram[i]);
        }

        //  Dataset e grafico
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(histSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Istogramma inter-arrivi",
                "Inter-arrivo normalizzato",
                "Frequenza",
                dataset,
                PlotOrientation.VERTICAL,
                false, true, false
        );

        XYPlot plot = chart.getXYPlot();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesLinesVisible(0, false);
        renderer.setSeriesShapesVisible(0, true); // punti per istogramma
        plot.setRenderer(renderer);

        ChartUtils.saveChartAsPNG(new File(filename), chart, 800, 600);
        System.out.println("📊 Istogramma inter-arrivi salvato in " + filename);
    }
}
