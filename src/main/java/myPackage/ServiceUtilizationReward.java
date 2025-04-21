//ServiceUtilizationReward.java
package myPackage;

import org.oristool.analyzer.Succession;
import org.oristool.models.pn.PetriStateFeature;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.rewards.DiscreteRewardTime;
import org.oristool.simulator.rewards.Reward;
import org.oristool.simulator.rewards.RewardObserver;
import org.oristool.simulator.rewards.RewardTime;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

// Il tasso di utilizzo è pari al tasso della transizione Service
public class ServiceUtilizationReward implements Reward {
    private final Sequencer sequencer;
    private int serviceCount = 0;
    private final Set<RewardObserver> observers = new HashSet<>();

    public ServiceUtilizationReward(Sequencer sequencer) {
        this.sequencer = sequencer;
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
                ? serviceCount / elapsedTime.doubleValue()
                : 0.0;
    }


    @Override
    public void update(Sequencer.SequencerEvent event) {
        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            Succession lastSuccession = sequencer.getLastSuccession();

            if (lastSuccession != null) {
                String firedTransition = lastSuccession.getEvent().getName();
                if ("service".equals(firedTransition)) {
                    serviceCount++;
                }
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
