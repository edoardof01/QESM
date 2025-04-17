//ServiceUtilizationReward.java
package myPackage;

import org.oristool.analyzer.Succession;
import org.oristool.models.pn.PetriStateFeature;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.rewards.DiscreteRewardTime;
import org.oristool.simulator.rewards.Reward;
import org.oristool.simulator.rewards.RewardObserver;
import org.oristool.simulator.rewards.RewardTime;

import java.util.HashSet;
import java.util.Set;

public class ServiceUtilizationReward implements Reward {
    private final Sequencer sequencer;
    private int serviceCount = 0;
    private int totalRuns = 0;
    private final Set<RewardObserver> observers = new HashSet<>();

    public ServiceUtilizationReward(Sequencer sequencer) {
        this.sequencer = sequencer;
        this.sequencer.addObserver(this);
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
        return totalRuns > 0 ? serviceCount / (double) totalRuns : 0.0;
    }

    @Override
    public void update(Sequencer.SequencerEvent event) {
        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            Succession lastSuccession = sequencer.getLastSuccession();

            if (lastSuccession != null) {
                String firedTransition = lastSuccession.getEvent().getName();
                if ("service".equals(firedTransition)) {
                    serviceCount++;
                    notifyObservers();
                }
            }

            totalRuns++;
            notifyObservers();
        }
    }

    private boolean isServiceTransition(Succession succession) {
        return succession.getChild().getFeature(PetriStateFeature.class)
                .getEnabled().stream()
                .anyMatch(transition -> "service".equals(transition.getName()));
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

    // ✅ Getter utile per debug o metriche
    public int getServiceCount() {
        return serviceCount;
    }
}
