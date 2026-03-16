package hive.common.rl.operations;

public abstract class Operation {

    public final int inputSize, outputSize;
    private double[][] input, output, gradient;
    private int batchSize;
    private boolean evaluated, calculatedGradient;

    public Operation(int inputSize, int outputSize) {
        this.inputSize = inputSize;
        this.outputSize = outputSize;
    }

    public final double[][] evaluate(double[][] input) {
        if(evaluated) {
            return output;
        }
        if(input[0].length != inputSize){
            throw new IllegalArgumentException("input length must be the same");
        }
        batchSize = input.length;
        this.input = input;
        output = doEvaluate(input);
        if(output.length != batchSize && output[0].length != outputSize) {
            throw new IllegalArgumentException("output length must be the same");
        }
        evaluated = true;
        return output;
    }

    public final double[][] calculateGradient(double[][] upperGradient) {
        if(calculatedGradient) {
            return gradient;
        }
        if(!evaluated) {
            throw new IllegalStateException("Must first evaluate before training");
        }
        if(input[0].length != inputSize) {
            throw new IllegalArgumentException("input length must be the input length");
        }
        gradient = doCalculateGradient(input, output, upperGradient);
        if(gradient.length != batchSize && gradient[0].length != inputSize) {
            throw new IllegalArgumentException("gradient length must be the output length");
        }
        calculatedGradient = true;
        return gradient;
    }

    public void clearEval(){
        evaluated = false;
        calculatedGradient = false;
    }

    public void clearGrad() {
        calculatedGradient = false;
    }

    public final void train(double learningRate, double reward) {
        if(!calculatedGradient) {
            throw new IllegalStateException("Must first calculate gradient");
        }
        doTrain(gradient, learningRate, reward);
    }

    protected abstract double[][] doEvaluate(double[][] input);

    protected abstract double[][] doCalculateGradient(double[][] input, double[][] output, double[][] gradient);

    protected void doTrain(double[][] gradient, double learningRate, double reward) {}

}
