package hive.common.rl;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.Arrays;
import java.util.List;

public class DecisionModel implements INBTSerializable<CompoundTag> {

    private static final double LEARNING_RATE = 0.05;
    private static final double KILLS_WEIGHT = 1;
    private static final double DAMAGE_WEIGHT = 0.025;
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
        double[] values = getValues(input);
        for (int i = 0; i < values.length; i++) {
            values[i] = Math.exp(values[i]);
            sum += values[i];
        }
        for (int i = 0; i < values.length; i++) {
            values[i] /= sum;
        }
        return values;
    }

    protected double[] getValues(DecisionModelInput input) {
        double[] arr = input.arr();
        double[] scores = new double[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            for (int j = 0; j < parameters[i].length; j++) {
                scores[i] += parameters[i][j] * arr[j];
            }
        }
        return scores;
    }

    protected double getReward(RewardState reward) {
        return KILLS_WEIGHT * reward.kills() + DAMAGE_WEIGHT * reward.damage();
    }

    public void train(List<TrainingData> datas, RewardState rewardState) {
        if (datas.isEmpty()) {
            return;
        }
        double reward = getReward(rewardState);
        double[][] d = new double[parameters.length][parameters[0].length];
        for (TrainingData data : datas) {
            double[] values = getValues(data.input());
            int index = data.decision().ordinal();
            double num = values[index];
            double denom = Arrays.stream(values).sum();
            if (denom == 0) {
                continue;
            }
            for (int j = 0; j < d[index].length; j++) {
                d[index][j] += parameters[index][j] * (1 - num / denom);
            }
        }
        for (int i = 0; i < parameters.length; i++) {
            for (int j = 0; j < parameters[i].length; j++) {
                parameters[i][j] += LEARNING_RATE * reward * d[i][j] / datas.size();
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
