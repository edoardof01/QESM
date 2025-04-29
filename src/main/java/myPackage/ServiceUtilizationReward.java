
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

// ServiceUtilizationReward corretto: calcolo su min(PoolSize, QueueLength)
public class ServiceUtilizationReward implements Reward {
    private final Sequencer sequencer;
    private final int poolSize;
    private double accumulatedService = 0.0;
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
        return elapsedTime.compareTo(BigDecimal.ZERO) > 0
                ? accumulatedService / elapsedTime.doubleValue()
                : 0.0;
    }


    @Override
    public void update(Sequencer.SequencerEvent event) {
        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            BigDecimal currentTime = sequencer.getCurrentRunElapsedTime();
            BigDecimal deltaT = currentTime.subtract(lastTime);

            if (deltaT.compareTo(BigDecimal.ZERO) > 0) {
                Succession lastSuccession = sequencer.getLastSuccession();
                if (lastSuccession != null) {
                    Marking marking = lastSuccession.getChild()
                            .getFeature(PetriStateFeature.class)
                            .getMarking();

                    int queueLength = marking.getTokens("queue");
                    int activeServices = Math.min(poolSize, queueLength);

                    accumulatedService += activeServices * deltaT.doubleValue();

                    // 👉 LOG DI DEBUG
                    System.out.printf(
                            "[DEBUG] deltaT = %.6f | activeServices = %d | accumulatedService = %.6f | elapsedTime = %.6f%n",
                            deltaT.doubleValue(), activeServices, accumulatedService, currentTime.doubleValue()
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
