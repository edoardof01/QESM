package myPackage;

import org.oristool.simulator.rewards.Reward;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        // 1. Scegli modalità
        if (args.length == 0 ||
                (!args[0].equalsIgnoreCase("static") && !args[0].equalsIgnoreCase("dynamic"))) {
            System.out.println("❗ Devi specificare 'static' o 'dynamic' come argomento.");
            return;
        }
        boolean useDynamicMode = args[0].equalsIgnoreCase("dynamic");

        // 2. Parametri base
        int queueSize = 8;
        int poolSize  = 8;
        int rounds    = 3;

        // 3. Pesi iniziali normalizzati
        List<BigDecimal> weights = new ArrayList<>(List.of(
                new BigDecimal("0.9"),
                new BigDecimal("0.05"),
                new BigDecimal("0.03"),
                new BigDecimal("0.02")
        ));

        // 4. Sampler dinamico (se richiesto)
        DynamicCDFSampler dynamicSampler = null;
        if (useDynamicMode) {
            dynamicSampler = new DynamicCDFSampler(
                    new BigDecimal("0.1"),   // learningRate
                    new BigDecimal("0.01"),  // tolerance
                    100                       // windowSize (n° campioni)
            );
        }

        // 5. Loop principale
        for (int round = 1; round <= rounds; round++) {
            System.out.println("\n==== ROUND " + round +
                    " (" + (useDynamicMode ? "Dynamic" : "Static") + ") ====");

            // --- Setup del modello e del sequencer ---
            SimulationSetup setup = new SimulationSetup(weights, queueSize, poolSize);
            var sequencer = setup.getSequencer();

            // --- Reward per KPI ---
            var abandonReward     = new AbandonRateReward(sequencer);
            var blockReward       = new BlockProbabilityReward(sequencer);
            var utilizationReward = new ServiceUtilizationReward(sequencer);
            List<Reward> observers = List.of(abandonReward, blockReward, utilizationReward);

            // --- Collector per inter-arrival + grafico CDF ---
            var arrivalCollector = new InterarrivalCollectorReward(sequencer);

            // --- Reward per stop temporale ---
            new MaxSimulationTimeReward(sequencer, new BigDecimal("100.0"), observers);

            // --- Esegui simulazione ---
            sequencer.simulate();

            // --- Statistiche dettagliate per ciascun arrivalX ---
            arrivalCollector.reportArrivalStats();

            // --- Stampa KPI raccolti ---
            System.out.printf("Abbandono: %.4f%n", (double) abandonReward.evaluate());
            System.out.printf("Blocco:    %.4f%n", (double) blockReward.evaluate());
            System.out.printf("Utilizzo:  %.4f%n", (double) utilizationReward.evaluate());

            // --- Genera e salva il grafico della CDF ---
            // salva in cdf_round1.png, cdf_round2.png, ecc.
            arrivalCollector.reportCDF("cdf_round" + round + ".png");

            // --- Estrai inter‐arrival dalla coda ---
            var arrivalTimes  = blockReward.getArrivalTimes();
            List<BigDecimal> interArrivals = new ArrayList<>();
            for (int i = 1; i < arrivalTimes.size(); i++) {
                BigDecimal delta = arrivalTimes.get(i)
                        .subtract(arrivalTimes.get(i - 1));
                interArrivals.add(delta);
                if (useDynamicMode) {
                    dynamicSampler.addInterArrivalTime(delta);
                }
            }

            // === STATIC UPDATE ===
            if (!useDynamicMode) {
                if (interArrivals.isEmpty()) {
                    System.out.println("⚠️  Nessun intertempo per aggiornare i pesi.");
                } else {
                    // passo tutti gli inter-arrival (non solo quantili)
                    var sampler = new CDFSampler(
                            new BigDecimal("0.1"),   // learningRate
                            new BigDecimal("0.01"),  // tolerance
                            true                     // verbose
                    );
                    weights = sampler.evaluateAndAdjustWeights(interArrivals, weights);

                    System.out.println("\n==== WEIGHTS STATIC ====");
                    for (int i = 0; i < weights.size(); i++) {
                        System.out.printf("W%d = %.4f%n", i + 1, weights.get(i));
                    }
                }
            }

            // === DYNAMIC UPDATE ===
            if (useDynamicMode) {
                weights = dynamicSampler.updateWeights(weights);
                System.out.println("\n==== WEIGHTS DYNAMIC ====");
                for (int i = 0; i < weights.size(); i++) {
                    System.out.printf("W%d = %.4f%n", i + 1, weights.get(i));
                }
            }
        }
    }
}
