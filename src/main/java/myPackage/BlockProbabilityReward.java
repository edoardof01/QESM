package myPackage;

import org.oristool.analyzer.Succession;
import org.oristool.petrinet.Transition;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.rewards.DiscreteRewardTime;
import org.oristool.simulator.rewards.Reward;
import org.oristool.simulator.rewards.RewardObserver;
import org.oristool.simulator.rewards.RewardTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BlockProbabilityReward implements Reward {
    private final Sequencer sequencer;

    private int arrivalCount = 0;   // arrivi riusciti
    private int blockCount   = 0;   // arrivi bloccati

    private final List<BigDecimal> arrivalTimes = new ArrayList<>();
    private final List<RewardObserver> observers = new ArrayList<>();

    public BlockProbabilityReward(Sequencer sequencer) {
        this.sequencer = sequencer;
        // Osserviamo anche l’inizio di ogni run per resettare i contatori
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
    public void update(Sequencer.SequencerEvent event) {
        // Reset contatori all’inizio di ciascuna run
        if (event == Sequencer.SequencerEvent.RUN_START) {
            arrivalCount = 0;
            blockCount   = 0;
            arrivalTimes.clear();
            return;
        }

        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            Succession last = sequencer.getLastSuccession();
            if (last == null) return;

            String name = last.getEvent().getName();
            if (name.startsWith("arrival")) {
                arrivalCount++;
                arrivalTimes.add(sequencer.getCurrentRunElapsedTime());
            }
            else if (name.startsWith("blocked")) {
                blockCount++;
            }

            // Notifica gli observers a fine run se vuoi trigger immediato
            if (name.startsWith("blocked")) {
                notifyObservers();
            }
        }
    }

    @Override
    public Object evaluate() {
        int totalAttempts = arrivalCount + blockCount;
        return totalAttempts > 0
                ? (double) blockCount / totalAttempts
                : 0.0;
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
        for (RewardObserver o : observers) {
            o.update(RewardEvent.RUN_END);
        }
    }

    /**
     * @return i timestamp (simulati) di ciascun arrivo riuscito
     */
    public List<BigDecimal> getArrivalTimes() {
        return new ArrayList<>(arrivalTimes);
    }

    /**
     * @return quante volte abbiamo tentato un arrivo e siamo stati bloccati
     */
    public int getBlockCount() {
        return blockCount;
    }
}
