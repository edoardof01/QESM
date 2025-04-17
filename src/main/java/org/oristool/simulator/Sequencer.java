
package org.oristool.simulator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.oristool.analyzer.Succession;
import org.oristool.analyzer.log.AnalysisLogger;
import org.oristool.analyzer.state.State;
import org.oristool.math.OmegaBigDecimal;
import org.oristool.math.function.EXP;
import org.oristool.math.function.Erlang;
import org.oristool.math.function.Function;
import org.oristool.math.function.PartitionedFunction;
import org.oristool.models.pn.PetriStateFeature;
import org.oristool.models.pn.Priority;
import org.oristool.models.stpn.trees.EmpiricalTransitionFeature;
import org.oristool.models.stpn.trees.StochasticTransitionFeature;
import org.oristool.petrinet.Marking;
import org.oristool.petrinet.PetriNet;
import org.oristool.petrinet.Transition;
import org.oristool.simulator.samplers.*;
import org.oristool.simulator.stpn.SamplerFeature;

public class Sequencer {

    public enum SequencerEvent {
        RUN_START,
        RUN_END,
        FIRING_EXECUTED,
        SIMULATION_START,
        SIMULATION_END
    }

    private static final Random random = new Random();

    private final List<SequencerObserver> observers = new ArrayList<>();
    private final List<SequencerObserver> currentRunObservers = new ArrayList<>();

    private final PetriNet net;
    private final Marking initialMarking;
    private final SimulatorComponentsFactory<PetriNet, Transition> componentsFactory;
    private final AnalysisLogger logger;

    private long currentRunNumber;
    private BigDecimal currentRunElapsedTime;
    private long currentRunFirings;
    private Succession lastSuccession;

    // --- flag di stop globale ---
    private volatile boolean stopRequested = false;

    /**
     * Chiamalo per interrompere la simulazione
     */
    public void requestStop() {
        this.stopRequested = true;
    }

    public Sequencer(PetriNet net, Marking initialMarking,
                     SimulatorComponentsFactory<PetriNet, Transition> componentsFactory,
                     AnalysisLogger logger) {
        this.net = net;
        this.initialMarking = initialMarking;
        this.componentsFactory = componentsFactory;
        this.logger = logger;
    }

    public void simulate() {
        // Inizializza i sampler
        for (Transition t : net.getTransitions()) {
            if (!t.hasFeature(SamplerFeature.class)) {
                if (t.hasFeature(EmpiricalTransitionFeature.class)) {
                    var e = t.getFeature(EmpiricalTransitionFeature.class);
                    t.addFeature(new SamplerFeature(
                            new EmpiricalTransitionSampler(e.getHistogramCDF(), e.getLower(), e.getUpper())));
                } else if (t.hasFeature(StochasticTransitionFeature.class)) {
                    var s = t.getFeature(StochasticTransitionFeature.class);
                    var eft = s.density().getDomainsEFT();
                    var lft = s.density().getDomainsLFT();

                    if (s.density() instanceof EXP) {
                        t.addFeature(new SamplerFeature(new ExponentialSampler((EXP)s.density())));
                    } else if (s.density() instanceof Erlang) {
                        t.addFeature(new SamplerFeature(new ErlangSampler((Erlang)s.density())));
                    } else if (s.density().getDensities().size() == 1
                            && s.density().getDensities().get(0).isConstant()) {
                        t.addFeature(new SamplerFeature(
                                new UniformSampler(eft.bigDecimalValue(), lft.bigDecimalValue())));
                    } else if (s.density().getDensities().size() == 1
                            && s.density().getDensities().get(0).isExponential()
                            && lft.compareTo(OmegaBigDecimal.POSITIVE_INFINITY) != 0) {
                        t.addFeature(new SamplerFeature(
                                new TruncatedExponentialSampler(
                                        s.density().getDensities().get(0).getExponentialRate(),
                                        eft.bigDecimalValue(), lft.bigDecimalValue())));
                    } else if (s.density() instanceof Function) {
                        t.addFeature(new SamplerFeature(
                                new MetropolisHastings((Function)s.density())));
                    } else if (s.density() != null) {
                        t.addFeature(new SamplerFeature(
                                new PartitionedFunctionSampler((PartitionedFunction)s.density())));
                    } else {
                        throw new IllegalArgumentException(
                                "Unsupported density type on transition " + t);
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Transition " + t + " must have a stochastic or empirical feature");
                }
            }
        }

        var successorEvaluator = componentsFactory.getSuccessorEvaluator();
        var firableBuilder   = componentsFactory.getFirableTransitionSetBuilder();

        currentRunNumber = 0;
        logger.debug("Simulation started...");
        notifyObservers(SequencerEvent.SIMULATION_START);

        // ** Loop esterno: finché ci sono observer e non è stato richiesto stop
        while (!stopRequested && !observers.isEmpty()) {
            currentRunElapsedTime = BigDecimal.ZERO;
            currentRunFirings     = 0;

            State state = componentsFactory.getInitialStateBuilder().build(net, initialMarking);
            logger.debug("Initial state:\n" + state);

            logger.debug("Run " + currentRunNumber + " started...");
            notifyObservers(SequencerEvent.RUN_START);

            // ** Loop interno: finché ci sono observer di run e non stopRequested
            while (!stopRequested && !currentRunObservers.isEmpty()) {
                if (!state.hasFeature(PetriStateFeature.class)) {
                    throw new IllegalStateException("State without marking!");
                }
                var m = state.getFeature(PetriStateFeature.class).getMarking();
                var enabled = firableBuilder.getEnabledEvents(net, state);
                if (enabled.isEmpty()) {
                    logger.debug("No firable transitions.");
                    break;
                }

                // Trova minimo ttf
                BigDecimal minTtf = null;
                List<Transition> best = new ArrayList<>();
                for (var t : enabled) {

                    /* --- DA RIMUOVERE --- */
                    BigDecimal rate = BigDecimal.valueOf(
                            t.getFeature(StochasticTransitionFeature.class).clockRate().evaluate(m)
                    );

                    if (rate.compareTo(BigDecimal.ZERO) == 0) {
                        System.out.println("⚠️  Rate ZERO per transizione: " + t.getName());
                        System.out.println("    Marking attuale: " + m);
                        System.out.println("    ClockRate expression: " +
                                t.getFeature(StochasticTransitionFeature.class).clockRate());
                        continue; // saltala
                    }
                    /* --- DA RIMUOVERE --- */


                    BigDecimal ttf = state
                            .getFeature(TimedSimulatorStateFeature.class)
                            .getTimeToFire(t)
                            .divide(BigDecimal.valueOf(t.getFeature(StochasticTransitionFeature.class)
                                            .clockRate().evaluate(m)),
                                    MathContext.DECIMAL128);

                    if (minTtf == null || ttf.compareTo(minTtf) < 0) {
                        minTtf = ttf;
                        best.clear();
                        best.add(t);
                    } else if (ttf.compareTo(minTtf) == 0) {
                        best.add(t);
                    }
                }

                // Applica priorità
                int maxPrio = best.stream()
                        .filter(t -> t.hasFeature(Priority.class))
                        .mapToInt(t -> t.getFeature(Priority.class).value())
                        .max().orElse(-1);
                var firable = new ArrayList<Transition>();
                for (var t : best) {
                    if ((t.hasFeature(Priority.class) && t.getFeature(Priority.class).value() == maxPrio)
                            || (!t.hasFeature(Priority.class) && maxPrio == -1)) {
                        firable.add(t);
                    }
                }

                // Seleziona a caso secondo peso
                BigDecimal totalW = BigDecimal.ZERO;
                for (var t : firable) totalW = totalW.add(getWeight(t, net, m));
                BigDecimal needle = totalW.multiply(BigDecimal.valueOf(random.nextDouble()));

                BigDecimal acc = BigDecimal.ZERO;
                Transition fired = null;
                for (var t : firable) {
                    acc = acc.add(getWeight(t, net, m));
                    if (needle.compareTo(acc) < 0) {
                        fired = t;
                        break;
                    }
                }

                try {
                    lastSuccession = successorEvaluator.computeSuccessor(net, state, fired);
                } catch (Exception e) {
                    notifyObservers(SequencerEvent.SIMULATION_END);
                    return;
                }

                logger.debug("Fired: " + fired);
                currentRunFirings++;
                currentRunElapsedTime = currentRunElapsedTime.add(
                        state.getFeature(TimedSimulatorStateFeature.class)
                                .getTimeToFire(fired)
                );
                state = lastSuccession.getChild();
                notifyCurrentRunObservers();
            }

            logger.debug("Run " + currentRunNumber + " ended.");
            notifyObservers(SequencerEvent.RUN_END);
            currentRunNumber++;
        }

        logger.debug("Simulation ended.");
        notifyObservers(SequencerEvent.SIMULATION_END);
    }

    private BigDecimal getWeight(Transition t, PetriNet n, Marking m) {
        return BigDecimal.valueOf(t.getFeature(StochasticTransitionFeature.class)
                .weight().evaluate(m));
    }

    public void addObserver(SequencerObserver o) {
        if (!observers.contains(o)) observers.add(o);
    }
    public void removeObserver(SequencerObserver o) {
        observers.remove(o);
    }
    private void notifyObservers(SequencerEvent e) {
        for (var o : new ArrayList<>(observers)) o.update(e);
    }

    public void addCurrentRunObserver(SequencerObserver o) {
        if (!currentRunObservers.contains(o)) currentRunObservers.add(o);
    }
    public void removeCurrentRunObserver(SequencerObserver o) {
        currentRunObservers.remove(o);
    }
    private void notifyCurrentRunObservers() {
        for (var o : new ArrayList<>(currentRunObservers)) o.update(SequencerEvent.FIRING_EXECUTED);
    }

    public BigDecimal getCurrentRunElapsedTime() {
        return currentRunElapsedTime;
    }
    public long getCurrentRunFirings() {
        return currentRunFirings;
    }
    public long getCurrentRunNumber() {
        return currentRunNumber;
    }
    public Succession getLastSuccession() {
        return lastSuccession;
    }
    public Marking getInitialMarking() {
        return initialMarking;
    }
}
