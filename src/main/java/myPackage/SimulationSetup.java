//SimulationSetup.java

package myPackage;
import org.oristool.models.stpn.MarkingExpr;
import org.oristool.models.stpn.trees.StochasticTransitionFeature;
import org.oristool.petrinet.Marking;
import org.oristool.petrinet.PetriNet;
import org.oristool.petrinet.Place;
import org.oristool.petrinet.Transition;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.stpn.STPNSimulatorComponentsFactory;

import java.math.BigDecimal;
import java.util.List;

public class SimulationSetup {
    private final Sequencer sequencer;

    public SimulationSetup(List<BigDecimal> weights, int queueSize, int poolSize) {
        PetriNet pn = new PetriNet();
        Marking marking = new Marking();

        Place p1 = pn.addPlace("ph1");
        Place p2 = pn.addPlace("ph2");
        Place p3 = pn.addPlace("ph3");
        Place p4 = pn.addPlace("ph4");
        Place queue = pn.addPlace("queue");
        Place abandonRatePlace = pn.addPlace("abandonRate");

        // ✅ più token iniziali per far girare la rete
        marking.setTokens(p1, 50);
        marking.setTokens(abandonRatePlace,1);

        Transition t0 = pn.addTransition("t0");
        Transition t1 = pn.addTransition("t1");
        Transition t2 = pn.addTransition("t2");
        t0.addFeature(StochasticTransitionFeature.newExponentialInstance("1"));
        t1.addFeature(StochasticTransitionFeature.newExponentialInstance("2"));
        t2.addFeature(StochasticTransitionFeature.newExponentialInstance("3"));

        pn.addPrecondition(p1, t0); pn.addPostcondition(t0, p2);
        pn.addPrecondition(p2, t1); pn.addPostcondition(t1, p3);
        pn.addPrecondition(p3, t2); pn.addPostcondition(t2, p4);

        Transition[] arrivals = new Transition[4];
        arrivals[0] = pn.addTransition("arrival1");
        arrivals[1] = pn.addTransition("arrival2");
        arrivals[2] = pn.addTransition("arrival3");
        arrivals[3] = pn.addTransition("arrival4");

        Place[] phases = new Place[] { p1, p2, p3, p4 };
        for (int i = 0; i < 4; i++) {
            arrivals[i].addFeature(StochasticTransitionFeature.newExponentialInstance(String.valueOf(weights.get(i))));
            pn.addPrecondition(phases[i], arrivals[i]);
            pn.addPostcondition(arrivals[i], queue);
        }

        // ✅ servizio con ciclo di ritorno su p1
        Transition service = pn.addTransition("service");
        service.addFeature(StochasticTransitionFeature.newExponentialInstance(String.valueOf(0.05 * poolSize)));
        pn.addPrecondition(queue, service);
        pn.addPostcondition(service, p1); // ciclo

        // abbandono
        Transition abandon = pn.addTransition("abandon");
        abandon.addFeature(StochasticTransitionFeature.newExponentialInstance(new BigDecimal("1"),
                MarkingExpr.from("0.01*abandonRate*queue", pn)));
        pn.addPrecondition(queue, abandon);

        MinimalAnalysisLogger logger = new MinimalAnalysisLogger();
        STPNSimulatorComponentsFactory factory = new STPNSimulatorComponentsFactory();
        this.sequencer = new Sequencer(pn, marking, factory, logger);
    }

    public Sequencer getSequencer() {
        return sequencer;
    }
}
