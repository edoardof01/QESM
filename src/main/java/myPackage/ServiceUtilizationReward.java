package myPackage;

import org.oristool.analyzer.Succession;
import org.oristool.models.pn.PetriStateFeature;
import org.oristool.petrinet.Marking;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.rewards.DiscreteRewardTime;
import org.oristool.simulator.rewards.Reward;
import org.oristool.simulator.rewards.RewardObserver;
import org.oristool.simulator.rewards.RewardTime;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * ServiceUtilizationReward CORRETTO: calcola l'utilizzo reale dei server
 * basandosi sui token effettivamente presenti nel place "servers".
 * Logica: utilization = (poolSize - serversLiberi) / poolSize
 * dove serversLiberi = marking.getTokens("servers")
 */
public class ServiceUtilizationReward implements Reward {
    private final Sequencer sequencer;
    private final int poolSize;
    private double accumulatedUtilization = 0.0;
    private BigDecimal lastTime = BigDecimal.ZERO;
    private final Set<RewardObserver> observers = new HashSet<>();

    public ServiceUtilizationReward(Sequencer sequencer, int poolSize) {
        this.sequencer = sequencer;
        this.poolSize = poolSize;
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
        BigDecimal elapsedTime = sequencer.getCurrentRunElapsedTime();
        if (elapsedTime.compareTo(BigDecimal.ZERO) <= 0 || poolSize == 0) {
            return 0.0;
        }

        // Utilization = (tempo cumulativo server occupati) / (tempo simulazione × poolSize)
        double maxPossibleUtilization = elapsedTime.doubleValue() * poolSize;
        return accumulatedUtilization / maxPossibleUtilization;
    }

    @Override
    public void update(Sequencer.SequencerEvent event) {
        if (event == Sequencer.SequencerEvent.RUN_START) {
            // Reset per nuova simulazione
            accumulatedUtilization = 0.0;
            lastTime = BigDecimal.ZERO;
            return;
        }

        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            BigDecimal currentTime = sequencer.getCurrentRunElapsedTime();
            BigDecimal deltaT = currentTime.subtract(lastTime);

            if (deltaT.compareTo(BigDecimal.ZERO) > 0) {
                Succession lastSuccession = sequencer.getLastSuccession();
                if (lastSuccession != null) {
                    Marking marking = lastSuccession.getChild()
                            .getFeature(PetriStateFeature.class)
                            .getMarking();
                    int serversLiberi = marking.getTokens("servers");
                    int serversOccupati = poolSize - serversLiberi;

                    // Sanity check
                    if (serversOccupati < 0) {
                        System.err.printf("⚠️ Errore: serversOccupati = %d (serversLiberi = %d, poolSize = %d)%n",
                                serversOccupati, serversLiberi, poolSize);
                        serversOccupati = 0;
                    }

                    // Accumula il tempo pesato per l'utilizzo
                    accumulatedUtilization += serversOccupati * deltaT.doubleValue();

                    // LOG DI DEBUG
                    double currentUtilization = accumulatedUtilization / (currentTime.doubleValue() * poolSize);
                    System.out.printf(
                            "[UTIL DEBUG] t=%.3f, Δt=%.3f, servers(liberi=%d, occupati=%d), acc=%.3f, util=%.3f%n",
                            currentTime.doubleValue(), deltaT.doubleValue(),
                            serversLiberi, serversOccupati, accumulatedUtilization, currentUtilization
                    );
                }
                lastTime = currentTime;
            }
        }
        notifyObservers();
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
        RewardEvent rewardEvent = RewardEvent.RUN_END;
        for (RewardObserver observer : observers) {
            observer.update(rewardEvent);
        }
    }
}