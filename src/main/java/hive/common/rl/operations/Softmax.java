package hive.common.rl.operations;

public class Softmax extends Operation {

    public Softmax(int size) {
        super(size, size);
    }

    @Override
    protected double[][] doEvaluate(double[][] input) {
        double[][] output = new double[input.length][outputSize];
        for(int i = 0; i < input.length; i ++) {
            double sum = 0;
            for(int j = 0; j < outputSize; j ++) {
                output[i][j] = Math.exp(input[i][j]);
                sum += output[i][j];
            }
            if(sum != 0) {
                for (int j = 0; j < outputSize; j++) {
                    output[i][j] /= sum;
                }
            }
        }
        return output;
    }

    @Override
    protected double[][] doCalculateGradient(double[][] input, double[][] output, double[][] gradient) {
        double[][] resp = new double[input.length][inputSize];
        for(int i = 0; i < input.length; i ++) {
            for(int j = 0; j < inputSize; i ++) {
                resp[i][j] = output[i][j] * (1 - output[i][j]) * gradient[i][j];
            }
        }
        return resp;
    }

}
