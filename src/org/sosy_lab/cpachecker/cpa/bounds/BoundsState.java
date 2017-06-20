/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.bounds;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Ordering;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentSortedMap;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.LoopIterationReportingState;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.core.interfaces.conditions.AvoidanceReportingState;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;
import org.sosy_lab.cpachecker.util.assumptions.PreventingHeuristic;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;

public class BoundsState
    implements AbstractState, Partitionable, AvoidanceReportingState, LoopIterationReportingState {

  private final int deepestIteration;

  private final boolean stopIt;

  private final PersistentSortedMap<ComparableLoop, Integer> iterations;

  private final int returnFromCounter;

  private int hashCache = 0;

  public BoundsState() {
    this(false);
  }

  public BoundsState(boolean pStopIt) {
    this(pStopIt, PathCopyingPersistentTreeMap.<ComparableLoop, Integer>of(), 0, 0);
  }

  private BoundsState(boolean pStopIt, PersistentSortedMap<ComparableLoop, Integer> pIterations, int pDeepestIteration, int pReturnFromCounter) {
    Preconditions.checkArgument(pDeepestIteration >= 0);
    Preconditions.checkArgument(
        (pDeepestIteration == 0 && pIterations.isEmpty())
            || (pDeepestIteration > 0 && !pIterations.isEmpty()));
    this.stopIt = pStopIt;
    this.iterations = pIterations;
    this.deepestIteration = pDeepestIteration;
    this.returnFromCounter = pReturnFromCounter;
  }

  public BoundsState enter(Loop pLoop) {
    return enter(pLoop, Integer.MAX_VALUE);
  }

  public BoundsState enter(Loop pLoop, int pLoopIterationsBeforeAbstraction) {
    int iteration = getIteration(pLoop);
    if (pLoopIterationsBeforeAbstraction != 0
        && iteration >= pLoopIterationsBeforeAbstraction) {
      iteration = pLoopIterationsBeforeAbstraction;
    } else {
      ++iteration;
    }
    return new BoundsState(
        stopIt,
        iterations.putAndCopy(new ComparableLoop(pLoop), iteration),
        iteration > deepestIteration ? iteration : deepestIteration,
        returnFromCounter);
  }



  public BoundsState stopIt() {
    return new BoundsState(true, iterations, deepestIteration, returnFromCounter);
  }

  public BoundsState returnFromFunction() {
    return new BoundsState(stopIt, iterations, deepestIteration, returnFromCounter + 1);
  }

  @Override
  public int getIteration(Loop pLoop) {
    Integer iteration = iterations.get(new ComparableLoop(pLoop));
    return iteration == null ? 0 : iteration;
  }

  @Override
  public int getDeepestIteration() {
    return deepestIteration;
  }

  public int getReturnFromCounter() {
    return returnFromCounter;
  }

  @Override
  public Set<Loop> getDeepestIterationLoops() {
    return FluentIterable.from(iterations.entrySet()).filter(new Predicate<Entry<ComparableLoop, Integer>>() {

      @Override
      public boolean apply(Entry<ComparableLoop, Integer> pArg0) {
        return pArg0.getValue() == getDeepestIteration();
      }

    }).transform(new Function<Entry<ComparableLoop, Integer>, Loop>() {

      @Override
      public Loop apply(Entry<ComparableLoop, Integer> pArg0) {
        return pArg0.getKey().loop;
      }

    }).toSet();
  }

  @Override
  public Object getPartitionKey() {
    return this;
  }

  public boolean isStopState() {
    return stopIt;
  }

  @Override
  public boolean mustDumpAssumptionForAvoidance() {
    return isStopState();
  }

  @Override
  public String toString() {
    return " Deepest loop iteration " + deepestIteration;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BoundsState)) {
      return false;
    }

    BoundsState other = (BoundsState)obj;
    return this.stopIt == other.stopIt
        && this.returnFromCounter == other.returnFromCounter
        && this.iterations.equals(other.iterations);
  }

  @Override
  public int hashCode() {
    if (hashCache == 0) {
      hashCache = Objects.hash(stopIt, returnFromCounter, iterations);
    }
    return hashCache;
  }

  @Override
  public BooleanFormula getReasonFormula(FormulaManagerView manager) {
    BooleanFormulaManager bfmgr = manager.getBooleanFormulaManager();
    BooleanFormula reasonFormula = bfmgr.makeTrue();
    if (stopIt) {
      reasonFormula = bfmgr.and(reasonFormula, PreventingHeuristic.LOOPITERATIONS.getFormula(manager, getDeepestIteration()));
    }
    return reasonFormula;
  }

  private static class ComparableLoop implements Comparable<ComparableLoop> {

    private final Loop loop;

    public ComparableLoop(Loop pLoop) {
      Preconditions.checkNotNull(pLoop);
      this.loop = pLoop;
    }

    @Override
    public int hashCode() {
      return loop.hashCode();
    }

    @Override
    public boolean equals(Object pO) {
      if (this == pO) {
        return true;
      }
      if (pO instanceof ComparableLoop) {
        ComparableLoop other = (ComparableLoop) pO;
        return loop.equals(other.loop);
      }
      return false;
    }

    @Override
    public String toString() {
      return loop.toString();
    }

    @Override
    public int compareTo(ComparableLoop pOther) {
      // Compare by size
      int sizeComp = Integer.compare(loop.getLoopNodes().size(), pOther.loop.getLoopNodes().size());
      if (sizeComp != 0) {
        return sizeComp;
      }

      // If sizes are equal, compare lexicographically
      return Ordering.<CFANode>natural()
          .lexicographical()
          .compare(loop.getLoopNodes(), pOther.loop.getLoopNodes());
    }

  }
}
