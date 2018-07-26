package edu.berkeley.riselab.rlqopt;

import edu.berkeley.riselab.rlqopt.experiments.Experiment;
import edu.berkeley.riselab.rlqopt.opt.Planner;
import edu.berkeley.riselab.rlqopt.opt.bushy.PostgresBushyPlanner;
import edu.berkeley.riselab.rlqopt.opt.learning.RLQOpt;
import edu.berkeley.riselab.rlqopt.opt.postgres.PostgresPlanner;
import edu.berkeley.riselab.rlqopt.opt.quickpick.QuickPickPlanner;
import edu.berkeley.riselab.rlqopt.workload.DatasetGenerator;
import edu.berkeley.riselab.rlqopt.workload.IMDBWorkloadGenerator;
import edu.berkeley.riselab.rlqopt.workload.WorkloadGenerator;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.calcite.sql.parser.SqlParseException;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.factory.Nd4j;

/** Unit test for simple App. */
public class DoExperiments extends TestCase {

  public DoExperiments() {
    super("Test of the Relational Algebra Suite");
  }

  /** @return the suite of tests being tested */
  public static Test suite() {
    return new TestSuite(DoExperiments.class);
  }

  private void printSorted(Map<Planner, Double> map) {
    Map<String, Double> treeMap = new TreeMap<>();
    for (Map.Entry<Planner, Double> entry : map.entrySet()) {
      treeMap.put(entry.getKey().toString(), entry.getValue());
    }
    System.out.println(treeMap);
  }

  // Print in the following format:
  // query <planner 1>  ...  <planner N>
  // <query 0 name>:  <planner 1 cost>  ...  <planner N cost>
  // ...
  // <query M name>:  <planner 1 cost>  ...  <planner N cost>
  private void printPerQuery(Map<Planner, List<Double>> perQueryCost) {
    // Collect stats.
    List<Planner> planners =
        perQueryCost
            .keySet()
            .stream()
            // Don't print nopt.  They are all log(0) anyway.
            .filter(planner -> !planner.getPlannerName().equals("nopt"))
            .sorted(Comparator.comparing(Planner::getPlannerName))
            .collect(Collectors.toList());
    int numQueries = perQueryCost.get(planners.get(0)).size();

    // costs[i] is all planner's costs for query i.
    List<List<Double>> costs = new ArrayList<>();
    for (int i = 0; i < numQueries; ++i) {
      costs.add(new ArrayList<>());
    }
    for (Planner p : planners) {
      List<Double> queryCostsByP = perQueryCost.get(p);
      for (int i = 0; i < queryCostsByP.size(); ++i) {
        costs.get(i).add(queryCostsByP.get(i));
      }
    }

    // Print stats.
    System.out.print("query\t");
    for (Planner p : planners) {
      System.out.print(p.getPlannerName() + "\t");
    }
    System.out.println();

    for (int i = 0; i < numQueries; ++i) {
      System.out.print("q" + i + "\t");
      for (Double cost : costs.get(i)) {
        System.out.printf("%.3f\t", cost);
      }
      System.out.println();
    }
  }

  /** Write or append to a .csv file. */
  private void writeLatencyCsv(
      String csvPath, DatasetGenerator d, List<Planner> planners, Map<Planner, Double> latencies) {

    List<String> plannerNames =
        planners.stream().map(Planner::getPlannerName).collect(Collectors.toList());
    String allPlanners = String.join(",", plannerNames);
    System.out.println("allPlanners = " + allPlanners);
    String schema = "num_relations,num_total_attributes," + allPlanners + "\n";

    try {
      if (!Files.exists(Paths.get(csvPath))) {
        Files.write(
            Paths.get(csvPath),
            schema.getBytes("utf8"),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      }
      StringBuilder sb = new StringBuilder();
      sb.append(d.numRelations());
      sb.append(',');
      sb.append(d.numTotalAttributes());
      sb.append(',');
      for (int i = 0; i < planners.size(); ++i) {
        sb.append(latencies.get(planners.get(i)));
        if (i != planners.size() - 1) sb.append(',');
      }
      sb.append('\n');

      Files.write(Paths.get(csvPath), sb.toString().getBytes("utf8"), StandardOpenOption.APPEND);
    } catch (Exception exception) {
      System.out.println(exception.toString());
    }
  }

  /*public void test1() throws OperatorException {
    int numRels = 9;
    if (System.getProperty("numRels") != null) {
      numRels = Integer.valueOf(System.getProperty("numRels"));
    }

    DatasetGenerator d = new DatasetGenerator(numRels, 12, 1000, 100);
    WorkloadGeneratorEasy workload = new WorkloadGeneratorEasy(d);

    LinkedList<Planner> planners = new LinkedList<>();
    planners.add(new NoPlanner());
    planners.add(new RLQOpt(workload));
    planners.add(new PostgresBushyPlanner());
    planners.add(new PostgresPlanner());
    planners.add(new VolcanoPlanner());
    planners.add(new QuickPickPlanner(100));

    Experiment e = new Experiment(workload, 1000, 1000, planners);
    e.run();

    d.describe();
    System.out.print("Improvement: ");
    printSorted(e.getBaselineImprovement());
    System.out.print("Planning latency: ");
    Map<Planner, Double> latencies = e.getBaselineLatency();
    printSorted(latencies);

    writeLatencyCsv("planning_latencies.csv", d, planners, latencies);
  }*/

  public void test2() throws OperatorException, SqlParseException {
    Nd4j.setDataType(DataBuffer.Type.FLOAT);

    String jobQueryFile = "join-order-benchmark/queries/queries.sql";
    if (System.getProperty("queryFile") != null) {
      jobQueryFile = System.getProperty("queryFile");
    }
    System.out.println("jobQueryFile = " + jobQueryFile);

    IMDBWorkloadGenerator workload =
        new IMDBWorkloadGenerator(
            "schematext.sql",
            "tablestats",
            jobQueryFile,
            //            "join-order-benchmark/queries/queries.sql",
            // These (1) covered join sizes: 6,7,8,9,10,11; (2) randomized, then split; (3) include
            // variants of templates (intended for use when handleSelections is on).
            //            "join-order-benchmark/queries/cv-4fold-20-fold0.sql",
            //                "join-order-benchmark/queries/cv-4fold-20-fold1.sql",
            //                "join-order-benchmark/queries/cv-4fold-20-fold2.sql",
            //                "join-order-benchmark/queries/cv-4fold-20-fold3.sql",
            IMDBWorkloadGenerator.MEMORY);

    final int numTraining = 16;
    final int numTesting = 20;
    // Hacky way to do cross-validation.  E.g., 16/20/16 means
    // trains on first 16 and report test performance on last 4.
    // Set to 0 to test on all.  Averaging is done manually.
    final int testPerfDropFirst = numTraining;

    // When non-null: load from this file without re-generation, or generate once and persist it.
    // Pass null to disable this caching behavior.
    String trainingDataPath = null; // "job-" + numTraining + ".dat";

    LinkedList<Planner> planners = new LinkedList<>();
    //    planners.add(new NoPlanner());
    //    planners.add(new IKKBZPlanner());
    planners.add(new PostgresPlanner());
    //    planners.add(new RightDeepPlanner());
    //    planners.add(new ZigZagPlanner());
    planners.add(new PostgresBushyPlanner());
    //        planners.add(new MinSelectPlanner());
    //      planners.add(new VolcanoPlanner());
    planners.add(new QuickPickPlanner(1000));
    planners.add(new QuickPickPlanner(10000));
    //    planners.add(new QuickPickPlanner(1));
    planners.add(new RLQOpt(workload, trainingDataPath));

    /*planners.add(new RLQOpt(workload, trainingDataPath));
    //        planners.add(new NoPlanner());
    planners.add(new RLQOpt(workload, trainingDataPath));
    planners.add(new PostgresBushyPlanner());
    planners.add(new RightDeepPlanner());
    planners.add(new PostgresPlanner());
    planners.add(new VolcanoPlanner());
    planners.add(new QuickPickPlanner(1000));
    planners.add(new QuickPickPlanner(1));*/

    // Experiment e = new Experiment(workload, 90, 113, planners);
    Experiment e = new Experiment(workload, numTraining, numTesting, planners);
    e.train();
    System.out.println("train() done, calling run()");

    e.run();

    System.out.println("Per query improvement: ");
    printPerQuery(e.getPerQueryImprovement());
    System.out.println("Per query latency: ");
    printPerQuery(e.getPerQueryLatency());

    System.out.print("Improvement: ");
    printSorted(e.getBaselineImprovement(testPerfDropFirst));
    System.out.print("Planning latency: ");
    printSorted(e.getBaselineLatency());

    reportNumEvaluations(workload, planners);
  }

  void reportNumEvaluations(WorkloadGenerator workload, List<Planner> planners) {
    workload.getStatsModel().reportNumEvaluations();
    for (Planner p : planners) {
      if (p instanceof RLQOpt) {
        RLQOpt learning = (RLQOpt) p;
        learning.reportInferenceNumEvals();
        break;
      }
    }
  }
}
