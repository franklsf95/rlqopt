package edu.berkeley.riselab.rlqopt.opt.learning;

import edu.berkeley.riselab.rlqopt.Database;
import edu.berkeley.riselab.rlqopt.Operator;
import edu.berkeley.riselab.rlqopt.cost.CostModel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.deeplearning4j.datasets.iterator.impl.ListDataSetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

public class TrainingDataGenerator {

  Database db;
  String output;
  CostModel c;
  TrainingPlanner planner;
  String trainingDataFile;

  public TrainingDataGenerator(
      Database db, String output, CostModel c, TrainingPlanner planner, String trainingDataFile) {
    this.output = output;
    this.db = db;
    this.c = c;
    this.planner = planner;
    this.trainingDataFile = trainingDataFile;
  }

  public TrainingDataGenerator(Database db, String output, CostModel c, TrainingPlanner planner) {
    this(db, output, c, planner, null);
  }

  public void generateFile(LinkedList<Operator> workload, int t) {

    try {
      BufferedWriter writer = new BufferedWriter(new FileWriter(output));

      for (int i = 0; i < t; i++) for (Operator query : workload) planner.plan(query, c);

      for (TrainingDataPoint tr : planner.getTrainingData()) {
        writer.write(Arrays.toString(tr.featurize(db, c)).replace("[", "").replace("]", "") + "\n");
      }

      writer.close();
    } catch (IOException ex) {
    }
    ;
  }

  /** Returns non-null iff "this.trainingDataFile" points to a well-formed file. */
  public DataSet loadDataSet() {
    if (this.trainingDataFile == null) return null;
    DataSet dataSet = null;
    try {
      dataSet = new DataSet();
      dataSet.load(new File(this.trainingDataFile));
      System.out.println(
          "Dataset loaded from file: "
              + trainingDataFile
              + "; numExamples "
              + dataSet.numExamples());
    } catch (Exception e) {
      // Pass-through.
    }
    if (dataSet == null || dataSet.numExamples() == 0) return null;
    return dataSet;
  }

  public DataSet generateDataSet(LinkedList<Operator> workload, int t) {

    for (int i = 0; i < t; i++) {
      for (Operator query : workload) {
        System.out.println(query);
        planner.plan(query, c);
      }
    }

    LinkedList<INDArray> trainingExamples = new LinkedList();
    LinkedList<INDArray> reward = new LinkedList();

    int p = 0;

    for (TrainingDataPoint tr : planner.getTrainingData()) {
      float[] vector = tr.featurize(db, c);
      p = vector.length;

      float[] xBuffer = new float[p - 1];

      for (int ind = 0; ind < vector.length - 1; ind++) xBuffer[ind] = vector[ind];

      float[] yBuffer = new float[1];

      if (Double.isInfinite(vector[vector.length - 1])) continue;

      yBuffer[0] = vector[vector.length - 1];

      trainingExamples.add(Nd4j.create(xBuffer, new int[] {1, p - 1}));
      reward.add(Nd4j.create(yBuffer, new int[] {1, 1}));
    }

    int n = trainingExamples.size();
    System.out.println("dataset size " + n);
    INDArray batchedExamples = Nd4j.create(trainingExamples, new int[] {n, p - 1});
    INDArray batchedLabels = Nd4j.create(reward, new int[] {n, 1});

    DataSet dataSet = new DataSet(batchedExamples, batchedLabels);

    // Save dataset without normalization.
    if (this.trainingDataFile != null) {
      dataSet.save(new File(trainingDataFile));
      System.out.println("Dataset saved to file: " + trainingDataFile);
    }

    // Normalize.
    DataNormalizer.normalize(dataSet);
    return dataSet;
  }

  public DataSetIterator generateDataSetIterator(DataSet dataSet) {
    List<DataSet> listDs = dataSet.asList();
    // Use the full dataset as one batch.
    return new ListDataSetIterator(listDs, listDs.size()); // todo hyperparameter
  }

  public DataSetIterator generateDataSetIterator(LinkedList<Operator> workload, int t) {
    DataSet dataSet = generateDataSet(workload, t);
    return generateDataSetIterator(dataSet);
  }

  public DataSetIterator generateDataSetIterator(Operator query, int t) {
    LinkedList<Operator> workload = new LinkedList();
    workload.add(query);
    return generateDataSetIterator(workload, t);
  }

  public void generateFile(Operator query, int t) {
    LinkedList<Operator> workload = new LinkedList();
    workload.add(query);
    generateFile(workload, t);
  }
}
