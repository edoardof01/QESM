//AbandonRateReward.java
package myPackage;

import org.oristool.analyzer.Succession;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.rewards.DiscreteRewardTime;
import org.oristool.simulator.rewards.Reward;
import org.oristool.simulator.rewards.RewardTime;
import org.oristool.simulator.rewards.RewardObserver;
import java.util.ArrayList;
import java.util.List;

public class AbandonRateReward implements Reward {
    private final Sequencer sequencer;
    private int abandonCount = 0;
    private int arrivalCount = 0;
    private final List<RewardObserver> observers = new ArrayList<>(); // Lista per gestire gli observer

    public AbandonRateReward(Sequencer sequencer) {
        this.sequencer = sequencer;
        this.sequencer.addCurrentRunObserver(this);
    }

    @Override
    public Sequencer getSequencer() {
        return sequencer;
    }

    @Override
    public RewardTime getRewardTime() {
        return new DiscreteRewardTime(); // Reward calcolato in modo discreto
    }

    @Override
    public void update(Sequencer.SequencerEvent event) {
        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            Succession lastSuccession = sequencer.getLastSuccession();

            if (lastSuccession != null) {
                String eventName = lastSuccession.getEvent().getName();

                if ("arrival".equals(eventName) ||
                        "arrival1".equals(eventName) ||
                        "arrival2".equals(eventName) ||
                        "arrival3".equals(eventName) ||
                        "arrival4".equals(eventName)) {
                    arrivalCount++;
                }

                if ("abandon".equals(eventName)) {
                    abandonCount++;
                    notifyObservers();
                }
            }
        }
    }


    @Override
    public Object evaluate() {
        return arrivalCount > 0 ? abandonCount / (double) arrivalCount : 0.0;
    }

    // Aggiunge un observer alla lista
    @Override
    public void addObserver(RewardObserver observer) {
        observers.add(observer);
    }

    // Rimuove un observer dalla lista
    @Override
    public void removeObserver(RewardObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        RewardEvent rewardEvent = RewardEvent.RUN_END; // Usa RUN_END per notificare a fine esecuzione
        for (RewardObserver observer : observers) {
            observer.update(rewardEvent);
        }
    }

    public int getArrivalCount() {
        return arrivalCount;
    }

    public void setArrivalCount(int arrivalCount) {
        this.arrivalCount = arrivalCount;
    }
}
