package edu.berkeley.riselab.rlqopt.opt;

import edu.berkeley.riselab.rlqopt.Operator;

// this implements one transformation
// of the plan
public interface PlanningModule {

  // takes an operator returns an equivalent operator
  public Operator apply(Operator in, CostModel c);
}
