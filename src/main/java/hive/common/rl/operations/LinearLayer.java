package hive.common.rl.operations;

public class LinearLayer extends Operation {

    private final double[][] weights;

    public LinearLayer(int inputSize, int nodes) {
        super(inputSize, nodes);
        weights = new double[nodes][inputSize];
    }

    @Override
    protected double[][] doEvaluate(double[][] input) {
        double[][] output = new double[input.length][outputSize];
        for(int i = 0; i < input.length; i ++) {
            for (int j = 0; j < weights.length; j++) {
                for (int k = 0; k < weights[j].length; k++) {
                    output[i][j] += input[i][k] * weights[j][k];
                }
            }
        }
        return output;
    }

    @Override
    protected double[][] doCalculateGradient(double[][] input, double[][] output, double[][] gradient) {
        double[][] resp = new double[input.length][inputSize];
        for(int i = 0; i < input.length; i ++) {
            for(int j = 0; j < inputSize; j ++) {
                resp[i][j] = input[i][j] * gradient[i][]
            }
        }
        return resp;
    }

    @Override
    protected void doTrain(double[][] gradient, double learningRate, double reward) {

    }

}
