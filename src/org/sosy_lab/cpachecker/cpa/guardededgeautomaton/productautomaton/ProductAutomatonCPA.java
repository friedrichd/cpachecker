/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.guardededgeautomaton.productautomaton;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeDomain;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.composite.CompositeStopOperator;
import org.sosy_lab.cpachecker.cpa.composite.CompositeTransferRelation;

public class ProductAutomatonCPA extends CompositeCPA {

  public static ProductAutomatonCPA create(
      Collection<ConfigurableProgramAnalysis> pAutomatonCPAs,
      boolean pUseProgressPrecisionAdjustment,
      Configuration pConfig) throws InvalidConfigurationException {

    Preconditions.checkNotNull(pAutomatonCPAs);
    Preconditions.checkArgument(pAutomatonCPAs.size() > 0);

    ImmutableList.Builder<AbstractDomain> domains = ImmutableList.builder();
    ImmutableList.Builder<TransferRelation> transferRelations = ImmutableList.builder();
    ImmutableList.Builder<StopOperator> stopOperators = ImmutableList.builder();
    ImmutableList.Builder<ConfigurableProgramAnalysis> lCPAs = ImmutableList.builder();

    for (ConfigurableProgramAnalysis sp : pAutomatonCPAs) {
      domains.add(sp.getAbstractDomain());
      transferRelations.add(sp.getTransferRelation());
      stopOperators.add(sp.getStopOperator());
      lCPAs.add(sp);
    }

    CompositeDomain compositeDomain = new CompositeDomain(domains.build());
    CompositeTransferRelation compositeTransfer = new ProductAutomatonTransferRelation(transferRelations.build(), pConfig);
    CompositeStopOperator compositeStop = new ProductAutomatonStopOperator(stopOperators.build());

    return new ProductAutomatonCPA(compositeDomain, compositeTransfer, compositeStop,
        lCPAs.build(), pUseProgressPrecisionAdjustment);
  }

  public ProductAutomatonCPA(AbstractDomain abstractDomain,
      CompositeTransferRelation transferRelation,
      CompositeStopOperator stopOperator,
      ImmutableList<ConfigurableProgramAnalysis> cpas, boolean pUseProgressPrecisionAdjustment) {

    super(null, null, stopOperator, null, cpas, null);

   /* super(abstractDomain, transferRelation, new MergeSepOperator(), stopOperator,
        pUseProgressPrecisionAdjustment?ProgressProductAutomatonPrecisionAdjustment.INSTANCE:ProductAutomatonPrecisionAdjustment.getInstance(), cpas);*/
  }

  @Override
  public AbstractState getInitialState (CFANode node, StateSpacePartition pPartition) throws InterruptedException {
    CompositeState lInitialElement = (CompositeState)super.getInitialState(node, pPartition);

    return ProductAutomatonElement.createElement(lInitialElement.getWrappedStates());
  }

}