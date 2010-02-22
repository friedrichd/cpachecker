/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
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
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cpa.uninitvars;

import common.configuration.Configuration;
import common.configuration.Option;
import common.configuration.Options;

import cfa.objectmodel.CFAFunctionDefinitionNode;
import cpa.common.LogManager;
import cpa.common.defaults.AbstractCPAFactory;
import cpa.common.defaults.MergeJoinOperator;
import cpa.common.defaults.MergeSepOperator;
import cpa.common.defaults.StaticPrecisionAdjustment;
import cpa.common.defaults.StopSepOperator;
import cpa.common.interfaces.AbstractDomain;
import cpa.common.interfaces.AbstractElement;
import cpa.common.interfaces.CPAFactory;
import cpa.common.interfaces.ConfigurableProgramAnalysis;
import cpa.common.interfaces.MergeOperator;
import cpa.common.interfaces.Precision;
import cpa.common.interfaces.PrecisionAdjustment;
import cpa.common.interfaces.StopOperator;
import cpa.common.interfaces.TransferRelation;
import exceptions.InvalidConfigurationException;

/**
 * @author Philipp Wendler
 */
@Options
public class UninitializedVariablesCPA implements ConfigurableProgramAnalysis {

  private static class UninitializedVariablesCPAFactory extends AbstractCPAFactory {
    
    @Override
    public ConfigurableProgramAnalysis createInstance() throws InvalidConfigurationException {      
      return new UninitializedVariablesCPA(getConfiguration(), getLogger());
    }
  }
  
  public static CPAFactory factory() {
    return new UninitializedVariablesCPAFactory();
  }
  
  
  @Option(name="analysis.entryFunction")
  private String entryFunction = "main";
  @Option(name="uninitVars.printWarnings")
  private String printWarnings = "main";
  @Option(name="uninitVars.merge", values={"sep", "join"}, required=true)
  private String mergeType = "";
  @Option(name="uninitVars.stop", values={"sep", "join"}, required=true)
  private String stopType = "";
  
  private final AbstractDomain abstractDomain;
  private final MergeOperator mergeOperator;
  private final StopOperator stopOperator;
  private final TransferRelation transferRelation;
  private final PrecisionAdjustment precisionAdjustment;
  
  private UninitializedVariablesCPA(Configuration config, LogManager logger) throws InvalidConfigurationException {
    
    config.inject(this);
    
    UninitializedVariablesDomain domain = new UninitializedVariablesDomain();
    
    MergeOperator mergeOp = null;
    if(mergeType.equals("sep")) {
      mergeOp = MergeSepOperator.getInstance();
    }
    if(mergeType.equals("join")) {
      mergeOp = new MergeJoinOperator(domain.getJoinOperator());
    }

    StopOperator stopOp = null;

    if(stopType.equals("sep")) {
      stopOp = new StopSepOperator(domain.getPartialOrder());
    }
    if(stopType.equals("join")){
      stopOp = new UninitializedVariablesStopJoin(domain);
    }

    this.abstractDomain = domain;
    this.mergeOperator = mergeOp;
    this.stopOperator = stopOp;
    this.transferRelation = new UninitializedVariablesTransferRelation(printWarnings, logger);
    this.precisionAdjustment = StaticPrecisionAdjustment.getInstance();
  }
  
  @Override
  public AbstractDomain getAbstractDomain() {
    return abstractDomain;
  }

  @Override
  public AbstractElement getInitialElement(CFAFunctionDefinitionNode pNode) {
    return new UninitializedVariablesElement(entryFunction);
  }

  @Override
  public Precision getInitialPrecision(CFAFunctionDefinitionNode pNode) {
    return null;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return mergeOperator;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

}
