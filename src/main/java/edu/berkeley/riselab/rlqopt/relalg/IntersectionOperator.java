package edu.berkeley.riselab.rlqopt.relalg;

import edu.berkeley.riselab.rlqopt.Operator;
import edu.berkeley.riselab.rlqopt.OperatorException;
import edu.berkeley.riselab.rlqopt.OperatorParameters;

// implements a intersection operator
public class IntersectionOperator extends Operator {

  public IntersectionOperator(OperatorParameters params, Operator... source)
      throws OperatorException {
    super(params, source);
  }

  // override
  public boolean isValid(OperatorParameters params, Operator... source) {

    if (source.length != 2) return false;

    return true;
  }
}
