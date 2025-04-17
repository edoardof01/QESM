// Main.java
package myPackage;

import org.oristool.simulator.rewards.Reward;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        // 1. Scegli modalità da terminale
        if (args.length == 0 || (!args[0].equalsIgnoreCase("static") && !args[0].equalsIgnoreCase("dynamic"))) {
            System.out.println("❗ Devi specificare 'static' o 'dynamic' come argomento.");
            return;
        }

        boolean useDynamicMode = args[0].equalsIgnoreCase("dynamic");

        // 2. Parametri base
        int queueSize = 8;
        int poolSize = 8;
        int rounds = useDynamicMode ? 5 : 1;

        // 3. Pesi iniziali normalizzati
        List<BigDecimal> weights = new ArrayList<>(List.of(
                new BigDecimal("0.4"),
                new BigDecimal("0.3"),
                new BigDecimal("0.2"),
                new BigDecimal("0.1")
        ));



        // 4. Setup per modalità dinamica
        DynamicCDFSampler dynamicSampler = null;
        if (useDynamicMode) {
            dynamicSampler = new DynamicCDFSampler(new BigDecimal("0.1"), new BigDecimal("0.01"), 100, 5000);
        }

        // 5. Loop principale per ogni round
        for (int round = 1; round <= rounds; round++) {
            System.out.println("\n==== ROUND " + round + " (" + (useDynamicMode ? "Dynamic" : "Static") + ") ====");

            // Costruzione modello e sequencer
            SimulationSetup setup = new SimulationSetup(weights, queueSize, poolSize);
            var sequencer = setup.getSequencer();

            // Registrazione dei Reward
            var abandonReward = new AbandonRateReward(sequencer);
            var blockReward = new BlockProbabilityReward(sequencer, queueSize);
            var utilizationReward = new ServiceUtilizationReward(sequencer);

            // Lista di tutti gli observer da rimuovere in caso di timeout
            List<Reward> observers = List.of(abandonReward, blockReward, utilizationReward);

            // Reward che imposta il limite massimo di tempo simulato
            var maxTimeReward = new MaxSimulationTimeReward(sequencer, new BigDecimal("100.0"), observers);

            // Avvia la simulazione: ogni reward può decidere quando fermare
            sequencer.simulate();

            System.out.println("siamo a riga 63");
            // Stampa KPI raccolti
            System.out.printf("Abbandono: %.4f\n", (double) abandonReward.evaluate());
            System.out.printf("Blocco:    %.4f\n", (double) blockReward.evaluate());
            System.out.printf("Utilizzo:  %.4f\n", (double) utilizationReward.evaluate());

            // Campionamento intertempi (per aggiornare pesi)
            var arrivalTimes = blockReward.getArrivalTimes();
            var interArrivals = new ArrayList<BigDecimal>();
            for (int i = 1; i < arrivalTimes.size(); i++) {
                BigDecimal diff = arrivalTimes.get(i).subtract(arrivalTimes.get(i - 1));
                interArrivals.add(diff);
                if (useDynamicMode) {
                    dynamicSampler.addInterArrivalTime(diff);
                }
            }

            // === STATIC ===
            if (!useDynamicMode) {
                if (interArrivals.isEmpty()) {
                    System.out.println("⚠️  Nessun intertempo per aggiornare i pesi.");
                    return;
                }
                var cdf = FunctionsCalculator.calculateCDF(interArrivals);
                var pdf = FunctionsCalculator.calculatePDF(cdf);
                var sampler = new CDFSampler(new BigDecimal("0.1"), new BigDecimal("0.01"));
                weights = sampler.evaluateAndAdjustWeights(pdf, weights);
                System.out.println("\n==== WEIGHTS ====" + weights.toString());
            }

            // === DYNAMIC ===
            if (useDynamicMode && !dynamicSampler.getRecentInterArrivalTimes().isEmpty()) {
                weights = dynamicSampler.updateWeights(weights);
            }

            if (interArrivals.isEmpty()) {
                System.out.println("⚠️  Nessun intertempo per aggiornare i pesi.");
                return;
            }

            // Stampa dei pesi aggiornati
            System.out.println("Pesi aggiornati:");
            for (int i = 0; i < weights.size(); i++) {
                System.out.printf("W%d = %.4f\n", i + 1, weights.get(i));
            }
        }
    }
}
