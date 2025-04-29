

package myPackage;
import lombok.Getter;
import org.oristool.models.stpn.MarkingExpr;
import org.oristool.models.stpn.trees.StochasticTransitionFeature;
import org.oristool.petrinet.Marking;
import org.oristool.petrinet.PetriNet;
import org.oristool.petrinet.Place;
import org.oristool.petrinet.Transition;
import org.oristool.simulator.Sequencer;
import org.oristool.simulator.stpn.STPNSimulatorComponentsFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Getter
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
        Place blockedAttempts = pn.addPlace("blockedAttempts");
        Place abandonPlace = pn.addPlace("abandonRate");

        BigDecimal serviceRate = BigDecimal.valueOf(20);

        BigDecimal abandonRate = BigDecimal.valueOf(1);

        // ✅ più token iniziali per far girare la rete
        marking.setTokens(p1, 50);
        marking.setTokens(abandonPlace,1);

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


        // QUEUE ARRIVALS
        BigDecimal sumW = weights.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal factor = new BigDecimal("4.0")
                .divide(sumW, 10, RoundingMode.HALF_UP);
        String cond = "If(queue < " + queueSize + ", 1, 0)"; // per costruire la guardia boolean->0/1
        MarkingExpr guard = MarkingExpr.from(cond, pn);

        for (int i = 0; i < 4; i++) {
            BigDecimal lambda_i = weights.get(i).multiply(factor);
            // uso il tasso base = lambda_i con 0/1 a seconda che queue<queueSize o meno
            arrivals[i].addFeature(
                    StochasticTransitionFeature.newExponentialInstance(lambda_i, guard)
            );

            pn.addPrecondition(phases[i],   arrivals[i]);
            pn.addPostcondition(arrivals[i], queue);
        }


        // BLOCK
        String blockCond = "If(queue >= " + queueSize + ", 1, 0)";
        MarkingExpr blockGuard = MarkingExpr.from(blockCond, pn);

        for (int i = 0; i < 4; i++) {
            Transition blocked = pn.addTransition("blocked" + (i + 1));
            BigDecimal lambda_i = weights.get(i).multiply(factor);

            blocked.addFeature(
                    StochasticTransitionFeature.newExponentialInstance(lambda_i, blockGuard)
            );

            pn.addPrecondition(phases[i], blocked);
            pn.addPostcondition(blocked, blockedAttempts);
        }


        // SERVIZIO
        Transition service = pn.addTransition("service");
        service.addFeature(StochasticTransitionFeature.newExponentialInstance(String.valueOf(serviceRate.multiply(BigDecimal.valueOf(0.01*poolSize)))));
        pn.addPrecondition(queue, service);
        pn.addPostcondition(service, p1);


        // ABBANDONO
        Transition abandon = pn.addTransition("abandon");
        abandon.addFeature(StochasticTransitionFeature.newExponentialInstance(new BigDecimal("1"),
                MarkingExpr.from("0.01*abandonRate*queue", pn)));
        pn.addPrecondition(queue, abandon);


        MinimalAnalysisLogger logger = new MinimalAnalysisLogger();
        STPNSimulatorComponentsFactory factory = new STPNSimulatorComponentsFactory();
        this.sequencer = new Sequencer(pn, marking, factory, logger);
    }

}
