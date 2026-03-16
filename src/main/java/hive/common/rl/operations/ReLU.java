package hive.common.rl.operations;

public class ReLU extends Operation {

    public ReLU(int size) {
        super(size, size);
    }

    @Override
    protected double[][] doEvaluate(double[][] input) {
        double[][] output = new double[input.length][outputSize];
        for(int i = 0; i < input.length; i ++) {
            for(int j = 0; j < outputSize; j ++) {
                output[i][j] = Math.max(0, input[i][j]);
            }
        }
        return output;
    }

    @Override
    protected double[][] doCalculateGradient(double[][] input, double[][] output, double[][] gradient) {
        double[][] resp = new double[input.length][inputSize];
        for(int i = 0; i < input.length; i ++) {
            for(int j = 0; j < inputSize; j ++) {
                resp[i][j] = Math.max(0, gradient[i][j]);
            }
        }
        return resp;
    }

}
