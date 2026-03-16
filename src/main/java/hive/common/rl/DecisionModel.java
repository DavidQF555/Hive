package hive.common.rl;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.List;

public class DecisionModel implements INBTSerializable<CompoundTag> {

    private static final double LEARNING_RATE = 0.05;
    private final double[][] parameters = new double[DecisionState.values().length][11];

    public DecisionState evaluate(DecisionModelInput input) {
        double[] prob = evaluateSoftmax(input);
        int max = 0;
        for (int i = 1; i < parameters.length; i++) {
            if (prob[i] > prob[max]) {
                max = i;
            }
        }
        return DecisionState.values()[max];
    }

    protected double[] evaluateSoftmax(DecisionModelInput input) {
        double sum = 0;
        double[] values = getExpValues(input);
        for (double value : values) {
            sum += value;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] /= sum;
        }
        return values;
    }

    protected double[] getExpValues(DecisionModelInput input) {
        double[] arr = input.arr();
        double[] scores = new double[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            for (int j = 0; j < parameters[i].length; j++) {
                scores[i] += parameters[i][j] * arr[j];
            }
        }
        for (int i = 0; i < scores.length; i++) {
            scores[i] = Math.exp(scores[i]);
        }
        return scores;
    }

    protected double getReward(RewardState reward) {
        if (reward.kills() > 0) {
            return 1 - Math.exp(reward.kills() / -2.0);
        } else {
            double x;
            if(reward.damageDealt() <= 0) {
                x = reward.damageTaken() * -0.001 + reward.deaths() * -0.25 - 5;
            }
            else {
                x = reward.damageDealt() * 0.025 + reward.damageTaken() * -0.001 + reward.deaths() * -0.25;
            }
            double exp = 0.5 * Math.exp(-x);
            return (0.5 - exp) / (1 + exp);
        }
    }

    public void train(List<TrainingData> data, RewardState rewardState) {
        if (data.isEmpty()) {
            return;
        }
        double reward = getReward(rewardState);
        double[][] gradients = new double[parameters.length][parameters[0].length];
        for (TrainingData entry : data) {
            double[] values = evaluateSoftmax(entry.input());
            int index = entry.decision().ordinal();
            for (int i = 0; i < gradients.length; i++) {
                for (int j = 0; j < gradients[i].length; j++) {
                    if (i == index) {
                        gradients[i][j] += entry.input().arr()[j] * (1 - values[i]);
                    } else {
                        gradients[i][j] -= entry.input().arr()[j] * values[i];
                    }
                }
            }
        }
        for (int i = 0; i < parameters.length; i++) {
            for (int j = 0; j < parameters[i].length; j++) {
                parameters[i][j] += LEARNING_RATE * reward * gradients[i][j] / data.size();
            }
        }
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (double[] parameter : parameters) {
            for (int j = 0; j < parameter.length; j++) {
                list.add(DoubleTag.valueOf(parameter[j]));
            }
        }
        tag.put("Parameters", list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        if (nbt.contains("Parameters", Tag.TAG_LIST)) {
            ListTag parameters = nbt.getList("Parameters", Tag.TAG_DOUBLE);
            for (int i = 0; i < this.parameters.length; i++) {
                for (int j = 0; j < this.parameters[i].length; j++) {
                    int index = i * this.parameters[i].length + j;
                    if (index < parameters.size()) {
                        this.parameters[i][j] = parameters.getDouble(index);
                    }
                }
            }
        }
    }
}
