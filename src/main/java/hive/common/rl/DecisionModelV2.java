package hive.common.rl;

import hive.common.rl.operations.LinearLayer;
import hive.common.rl.operations.Pipeline;
import hive.common.rl.operations.ReLU;
import hive.common.rl.operations.Softmax;

public class DecisionModelV2 {

    private static final int EXPANSION_SIZE = 20;
    private final Pipeline pipeline = new Pipeline(
            new LinearLayer(DecisionModelInput.SIZE, EXPANSION_SIZE),
            new ReLU(EXPANSION_SIZE),
            new LinearLayer(EXPANSION_SIZE, DecisionState.values().length),
            new Softmax(DecisionModelInput.SIZE)
    );

    public DecisionState evaluate(DecisionModelInput input) {
        double[] prob = pipeline.evaluate(input.arr());
        int max = 0;
        DecisionState[] parameters = DecisionState.values();
        for (int i = 1; i < parameters.length; i++) {
            if (prob[i] > prob[max]) {
                max = i;
            }
        }
        return DecisionState.values()[max];
    }

}
