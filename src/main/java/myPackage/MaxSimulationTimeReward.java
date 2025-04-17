package myPackage;

import org.oristool.analyzer.Succession;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.rewards.DiscreteRewardTime;
import org.oristool.simulator.rewards.Reward;
import org.oristool.simulator.rewards.RewardObserver;
import org.oristool.simulator.rewards.RewardTime;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
public class MaxSimulationTimeReward implements Reward {
    private final Sequencer sequencer;
    private final BigDecimal maxTime;
    private final Set<RewardObserver> observers = new HashSet<>();

    private final List<Reward> allRewardsToRemove;

    public MaxSimulationTimeReward(Sequencer sequencer, BigDecimal maxTime, List<Reward> allRewardsToRemove) {
        this.sequencer = sequencer;
        this.maxTime = maxTime;
        this.allRewardsToRemove = allRewardsToRemove;

        this.sequencer.addCurrentRunObserver(this); // 🔥 questa mancava
        this.sequencer.addObserver(this); // opzionale se vuoi anche eventi globali
    }

    @Override
    public void update(Sequencer.SequencerEvent event) {
        System.out.println("🔍 MaxSimulationTimeReward chiamato con evento: " + event);
        if (event == Sequencer.SequencerEvent.FIRING_EXECUTED) {
            System.out.println("siamo arrivati in MaxSimulationTimeReward");
            if (sequencer.getCurrentRunElapsedTime().compareTo(maxTime) >= 0) {
                System.out.println("🛑 Stop: raggiunto tempo massimo " + maxTime + "s");

                // 🔥 Rimuovi tutti gli observer passati
                for (Reward reward : allRewardsToRemove) {
                    sequencer.removeObserver(reward);
                    sequencer.removeCurrentRunObserver(reward);
                }

                // Rimuove anche se stesso
                sequencer.removeObserver(this);
                sequencer.removeCurrentRunObserver(this);
            }
        }
    }

    @Override public Object evaluate() { return null; }
    @Override public RewardTime getRewardTime() { return new DiscreteRewardTime(); }
    @Override public Sequencer getSequencer() { return sequencer; }
    @Override public void addObserver(RewardObserver observer) { observers.add(observer); }
    @Override public void removeObserver(RewardObserver observer) { observers.remove(observer); }
}
