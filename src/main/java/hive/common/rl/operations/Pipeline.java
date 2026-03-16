package hive.common.rl.operations;

public class Pipeline extends Operation {

    private final Operation[] operations;

    public Pipeline(Operation... operations) {
        super(operations[0].inputSize, operations[operations.length - 1].outputSize);
        this.operations = operations;
        for(int i = 0; i < operations.length - 1; i ++) {
            if(operations[i].outputSize != operations[i + 1].inputSize) {
                throw new IllegalStateException("inputs and outputs must match size");
            }
        }
    }

    @Override
    public void clearEval() {
        super.clearEval();
        for(Operation operation : operations) {
            operation.clearEval();
        }
    }

    @Override
    public void clearGrad() {
        super.clearGrad();
        for(Operation operation : operations) {
            operation.clearGrad();
        }
    }

    @Override
    protected double[][] doEvaluate(double[][] input) {
        for(Operation operation : operations) {
            input = operation.evaluate(input);
        }
        return input;
    }

    @Override
    protected double[][] doCalculateGradient(double[][] input, double[][] output, double[][] gradient) {
        double[][] prevGradient = gradient;
        for(int i = operations.length - 1; i >= 0; i --) {
            prevGradient = operations[i].calculateGradient(prevGradient);
        }
        return prevGradient;
    }

    @Override
    protected void doTrain(double[][] gradient, double learningRate, double reward) {
        for(Operation operation : operations) {
            operation.train(learningRate, reward);
        }
    }

}
