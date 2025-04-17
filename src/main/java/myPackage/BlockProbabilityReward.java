//BlockProbabilityReward.java
package myPackage;

import org.oristool.petrinet.Transition;
import org.oristool.analyzer.Succession;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.rewards.DiscreteRewardTime;
import org.oristool.simulator.rewards.Reward;
import org.oristool.simulator.rewards.RewardTime;
import org.oristool.simulator.rewards.RewardObserver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BlockProbabilityReward implements Reward {
    private final Sequencer sequencer;
    private final int queueSize;
    private int tokenCountInQueue = 0;
    private int blockCount = 0;
    private int arrivalCount = 0;

    private final List<RewardObserver> observers = new ArrayList<>();
    private final List<BigDecimal> arrivalTimes = new ArrayList<>();

    public BlockProbabilityReward(Sequencer sequencer, int queueSize) {
        this.sequencer = sequencer;
        this.queueSize = queueSize;
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
        return arrivalCount > 0 ? blockCount / (double) arrivalCount : 0.0;
    }

    @Override
    public void update(Sequencer.SequencerEvent event) {
        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            Succession lastSuccession = sequencer.getLastSuccession();

            if (lastSuccession != null) {
                Transition firedTransition = (Transition) lastSuccession.getEvent();
                String eventName = firedTransition.getName();

                // ✅ Transizioni di arrivo
                if ("arrival".equals(eventName) ||
                        "arrival1".equals(eventName) ||
                        "arrival2".equals(eventName) ||
                        "arrival3".equals(eventName) ||
                        "arrival4".equals(eventName)) {

                    arrivalCount++;
                    tokenCountInQueue++;
                    arrivalTimes.add(sequencer.getCurrentRunElapsedTime());

                    if (tokenCountInQueue > queueSize) {
                        blockCount++;
                        notifyObservers();
                    }
                }

                // ✅ Transizioni che svuotano la coda
                else if ("service".equals(eventName) || "abandon".equals(eventName)) {
                    tokenCountInQueue = Math.max(0, tokenCountInQueue - 1);
                }
            }
        }
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

    public ArrayList<BigDecimal> getArrivalTimes() {
        return new ArrayList<>(arrivalTimes);
    }

    // ✅ Getter opzionale per debug
    public int getBlockCount() {
        return blockCount;
    }
}
