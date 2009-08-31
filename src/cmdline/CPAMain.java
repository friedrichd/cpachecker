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
package cmdline;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import logging.CPACheckerLogger;
import logging.CustomLogLevel;
import logging.LazyLogger;

import org.eclipse.cdt.core.dom.IASTServiceProvider;
import org.eclipse.cdt.core.dom.ICodeReaderFactory;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDeclarator;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.InternalASTServiceProvider;
import org.eclipse.core.resources.IFile;

import programtesting.QueryDrivenProgramTesting;
import cfa.CFABuilder;
import cfa.CFACheck;
import cfa.CFAMap;
import cfa.CFAReduction;
import cfa.CFASimplifier;
import cfa.CFATopologicalSort;
import cfa.CPASecondPassBuilder;
import cfa.DOTBuilder;
import cfa.DOTBuilderInterface;
import cfa.objectmodel.BlankEdge;
import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFAFunctionDefinitionNode;
import cfa.objectmodel.CFANode;
import cfa.objectmodel.c.GlobalDeclarationEdge;
import cmdline.stubs.StubConfiguration;
import cmdline.stubs.StubFile;

import compositeCPA.CompositeCPA;
import compositeCPA.CompositeStopOperator;

import cpa.art.ARTCPA;
import cpa.common.CPAAlgorithm;
import cpa.common.CPAWithRefinement;
import cpa.common.ReachedElements;
import cpa.common.interfaces.AbstractElementWithLocation;
import cpa.common.interfaces.ConfigurableProgramAnalysis;
import cpa.common.interfaces.Precision;
import cpa.symbpredabs.BlockCFABuilder;
import cpa.symbpredabs.summary.SummaryCFABuilder;
import cpa.symbpredabs.summary.SummaryDOTBuilder;
import cpaplugin.CPAConfiguration;
import cpaplugin.MainCPAStatistics;
import exceptions.CPAException;

@SuppressWarnings("restriction")
public class CPAMain {

  public static CPAConfiguration cpaConfig;
  public static MainCPAStatistics cpaStats;

  // used in the ShutdownHook to check whether the analysis has been
  // interrupted by the user
  private static boolean interrupted = true;

  private static ConfigurableProgramAnalysis getCPA
  (CFAFunctionDefinitionNode node) throws CPAException {
    ConfigurableProgramAnalysis compCpa = CompositeCPA.getCompositeCPA(node);

    if(CPAMain.cpaConfig.getBooleanValue("cpa.useART")){
      return ARTCPA.getARTCPA(node, compCpa);
    }
    return compCpa; 
  }

  public static void doRunAnalysis(IASTTranslationUnit ast)
  throws Exception {

    cpaStats = new MainCPAStatistics();

    LazyLogger.log(CustomLogLevel.INFO, "Analysis Started");
    LazyLogger.log(CustomLogLevel.MainApplicationLevel,
    "Parsing Finished");

    CFAFunctionDefinitionNode mainFunction = null;

    //long analysisStartingTime = System.currentTimeMillis();
    cpaStats.startProgramTimer();

    // Build CFA
    CFABuilder builder = new CFABuilder();
    ast.accept(builder);
    CFAMap cfas = builder.getCFAs();
    int numFunctions = cfas.size();
    Collection<CFAFunctionDefinitionNode> cfasMapList =
      cfas.cfaMapIterator();

    // check the CFA of each function
    // enable only while debugging/testing
    if(CPAMain.cpaConfig.getBooleanValue("cfa.check")){
      for(CFAFunctionDefinitionNode cfa:cfasMapList){
        CFACheck.check(cfa);
      }
    }

    if(CPAMain.cpaConfig.getBooleanValue("analysis.topSort")){
      for(CFAFunctionDefinitionNode cfa:cfasMapList){
        CFATopologicalSort topSort = new CFATopologicalSort();
        topSort.topologicalSort(cfa);
      }
    }

    CFAFunctionDefinitionNode cfa = cfas.getCFA(
        CPAMain.cpaConfig.getProperty("analysis.entryFunction"));

    // TODO Erkan Simplify each CFA
    if (CPAMain.cpaConfig.getBooleanValue("cfa.simplify")) {
      CFASimplifier simplifier = new CFASimplifier();
      simplifier.simplify(cfa);
    }

    // Insert call and return edges and build the supergraph
    if (CPAMain.cpaConfig.getBooleanValue("analysis.interprocedural")) {
      LazyLogger.log(CustomLogLevel.MainApplicationLevel,
      "Analysis is interprocedural ");
      LazyLogger.log(CustomLogLevel.MainApplicationLevel,
      "Adding super edges");
      boolean noExtCalls = CPAMain.cpaConfig.getBooleanValue(
      "analysis.noExternalCalls");
      CPASecondPassBuilder spbuilder =
        new CPASecondPassBuilder(cfas, noExtCalls);
      for (CFAFunctionDefinitionNode cfa2 : cfasMapList){
        spbuilder.insertCallEdges(cfa2.getFunctionName());
      }
    }

    if (CPAMain.cpaConfig.getBooleanValue(
    "analysis.useSummaryLocations")) {
      LazyLogger.log(CustomLogLevel.MainApplicationLevel,
      "Building Summary CFAs");
      mainFunction = cfas.getCFA(CPAMain.cpaConfig.getProperty(
      "analysis.entryFunction"));
      if (CPAMain.cpaConfig.getBooleanValue(
      "cfa.removeIrrelevantForErrorLocations")) {
        CFAReduction coi =
          new CFAReduction();
        mainFunction =
          coi.removeIrrelevantForErrorLocations(mainFunction);
      }
      SummaryCFABuilder summaryBuilder =
        new SummaryCFABuilder(mainFunction,
            builder.getGlobalDeclarations());
      mainFunction = summaryBuilder.buildSummary();
      LazyLogger.log(CustomLogLevel.MainApplicationLevel, "DONE");
      cfasMapList = cfas.cfaMapIterator();
    } else if (CPAMain.cpaConfig.getBooleanValue("analysis.useBlockEdges")){
      CPACheckerLogger.log(CustomLogLevel.MainApplicationLevel,
      "Building Block CFAs");
      mainFunction = cfas.getCFA(CPAMain.cpaConfig.getProperty(
      "analysis.entryFunction"));
      if (CPAMain.cpaConfig.getBooleanValue(
      "cfa.removeIrrelevantForErrorLocations")) {
        CFAReduction coi =
          new CFAReduction();
        mainFunction =
          coi.removeIrrelevantForErrorLocations(mainFunction);
      }
      BlockCFABuilder summaryBuilder =
        new BlockCFABuilder(mainFunction,
            builder.getGlobalDeclarations());
      mainFunction = summaryBuilder.buildBlocks();
      CPACheckerLogger.log(CustomLogLevel.MainApplicationLevel, "DONE");
      cfasMapList = cfas.cfaMapIterator();
    } else if (CPAMain.cpaConfig.getBooleanValue("analysis.useGlobalVars")){
      // add global variables at the beginning of main
      mainFunction = cfas.getCFA(CPAMain.cpaConfig.getProperty(
      "analysis.entryFunction"));
      if (CPAMain.cpaConfig.getBooleanValue(
      "cfa.removeIrrelevantForErrorLocations")) {
        CFAReduction coi =
          new CFAReduction();
        mainFunction =
          coi.removeIrrelevantForErrorLocations(mainFunction);
      }
      List<IASTDeclaration> globalVars = builder.getGlobalDeclarations();
      mainFunction = addGlobalDeclarations(mainFunction, globalVars);
    }

    // check the super CFA starting at the main function
    // enable only while debugging/testing
    if(CPAMain.cpaConfig.getBooleanValue("cfa.check")){
      CFACheck.check(cfas.getCFA(CPAMain.cpaConfig.getProperty(
      "analysis.entryFunction")));
    }

    LazyLogger.log(CustomLogLevel.MainApplicationLevel,
        numFunctions, " functions parsed");

    // Erkan: For interprocedural analysis, we start with the
    // main function and we proceed, we don't need to traverse
    // all functions separately

    if (false) {//!CPAMain.cpaConfig.getBooleanValue("analysis.interprocedural")) {
    } else if (CPAMain.cpaConfig.getBooleanValue("analysis.queryDrivenProgramTesting")) {
      if (mainFunction == null) {
        mainFunction = cfas.getCFA(CPAMain.cpaConfig.getProperty(
        "analysis.entryFunction"));
      }

      if (CPAMain.cpaConfig.getBooleanValue("dot.export")) {
        DOTBuilderInterface dotBuilder = null;
        if (CPAMain.cpaConfig.getBooleanValue(
        "analysis.useSummaryLocations")) {
          dotBuilder = new SummaryDOTBuilder();
        } else {
          dotBuilder = new DOTBuilder();
        }
        String dotPath = CPAMain.cpaConfig.getProperty("dot.path");
        dotBuilder.generateDOT(cfasMapList, mainFunction,
            new File(dotPath, "dot_main.dot").getPath());
      }

      LazyLogger.log(Level.INFO, "CPA Algorithm starting ... ");
      cpaStats.startAnalysisTimer();

      QueryDrivenProgramTesting.doIt(cfas, mainFunction);

      cpaStats.stopAnalysisTimer();

      LazyLogger.log(Level.INFO, "CPA Algorithm finished ");

      if (cpaStats.getErrorReached() == MainCPAStatistics.ERROR_UNKNOWN) {
        cpaStats.setErrorReached(false);
      }
      displayStatistics();
    } else {

      if (mainFunction == null) {
        mainFunction = cfas.getCFA(CPAMain.cpaConfig.getProperty(
        "analysis.entryFunction"));
      }

      if (CPAMain.cpaConfig.getBooleanValue("dot.export")) {
        DOTBuilderInterface dotBuilder = null;
        if (CPAMain.cpaConfig.getBooleanValue(
        "analysis.useSummaryLocations")) {
          dotBuilder = new SummaryDOTBuilder();
        } else {
          dotBuilder = new DOTBuilder();
        }
        String dotPath = CPAMain.cpaConfig.getProperty("dot.path");
        dotBuilder.generateDOT(cfasMapList, mainFunction,
            new File(dotPath, "dot" + "_main" + ".dot").getPath());
        //System.exit(0);
      }

      LazyLogger.log(CustomLogLevel.MainApplicationLevel,
      "CPA Algorithm Called");

      ConfigurableProgramAnalysis cpa = getCPA(mainFunction);

      LazyLogger.log(Level.INFO, "CPA Algorithm starting ... ");
      cpaStats.startAnalysisTimer();

      AbstractElementWithLocation initialElement =
        cpa.getInitialElement(mainFunction);
      Precision initialPrecision =
        cpa.getInitialPrecision(mainFunction);
      ReachedElements reached;
      CPAAlgorithm algo = new CPAAlgorithm(cpa, initialElement, initialPrecision);
      if(CPAMain.cpaConfig.getBooleanValue("cpa.useART")){
        CPAWithRefinement cpaWRef = new CPAWithRefinement();
        reached = cpaWRef.CPAWithRefinementAlgorithm(cpa, initialElement, initialPrecision);
      }
      else{
        reached = algo.CPA();
      }
      cpaStats.stopAnalysisTimer();

      LazyLogger.log(Level.INFO, "CPA Algorithm finished ");

      LazyLogger.log(CustomLogLevel.MainApplicationLevel,
          numFunctions, " Reached CPA Size: ", reached.size(),
          " for function: ", mainFunction.getFunctionName());
      System.out.println(" size of reached set: " + reached.size());
      System.out.println(" number of stops " + CompositeStopOperator.noOfOperations);
      if (!cpaConfig.getBooleanValue(
      "analysis.dontPrintReachableStates")) {
        reached.printStates();
      }
      if (cpaStats.getErrorReached() == MainCPAStatistics.ERROR_UNKNOWN) {
        cpaStats.setErrorReached(false);
      }
      displayStatistics();
    }
  }

  public static synchronized void displayStatistics() {
    cpaStats.printStatistics(new PrintWriter(System.out));
  }

  private static CFAFunctionDefinitionNode addGlobalDeclarations(
      CFAFunctionDefinitionNode cfa, List<IASTDeclaration> globalVars) {
    if (globalVars.isEmpty()) {
      return cfa;
    }
    // create a series of GlobalDeclarationEdges, one for each declaration,
    // and add them as successors of the input node
    List<CFANode> decls = new LinkedList<CFANode>();
    CFANode cur = new CFANode(0);
    cur.setFunctionName(cfa.getFunctionName());
    decls.add(cur);

    for (IASTDeclaration d : globalVars) {
      assert(d instanceof IASTSimpleDeclaration);
      IASTSimpleDeclaration sd = (IASTSimpleDeclaration)d;
      if (sd.getDeclarators().length == 1 &&
          sd.getDeclarators()[0] instanceof IASTFunctionDeclarator) {
        if (cpaConfig.getBooleanValue("analysis.useFunctionDeclarations")) {
          // do nothing
        }
        else {
          continue;
        }
      }
      GlobalDeclarationEdge e = new GlobalDeclarationEdge(
          d.getRawSignature(),
          ((IASTSimpleDeclaration)d).getDeclarators(),
          ((IASTSimpleDeclaration)d).getDeclSpecifier());
      CFANode n = new CFANode(0);
      n.setFunctionName(cur.getFunctionName());
      e.initialize(cur, n);
      decls.add(n);
      cur = n;
    }

    // now update the successors of cfa
    for (int i = 0; i < cfa.getNumLeavingEdges(); ++i) {
      CFAEdge e = cfa.getLeavingEdge(i);
      e.setPredecessor(cur);
    }
    if (cfa.getLeavingSummaryEdge() != null) {
      cfa.getLeavingSummaryEdge().setPredecessor(cur);
    }
    // and add a blank edge connecting the first node in decl with cfa
    BlankEdge be = new BlankEdge("INIT GLOBAL VARS");
    be.initialize(cfa, decls.get(0));

    return cfa;
  }

  static void printIfInterrupted() {
    if (interrupted) {
      cpaStats.stopAnalysisTimer();
      System.out.flush();
      System.err.flush();
      displayStatistics();
      System.out.println("\n" +
          "***************************************************" +
          "****************************\n" +
          "* WARNING:  Analysis interrupted!! The statistics " +
          "might be unreliable!        *\n" +
          "***************************************************" +
          "****************************\n"
      );
      System.out.flush();
    }
  }

  public static class ShutdownHook extends Thread {
    @Override
    public void run() {
      printIfInterrupted();
    }
  }

  public static void main(String[] args) {
    try {
      cpaConfig = new CPAConfiguration(args);
      String[] names =
        cpaConfig.getPropertiesArray("analysis.programNames");
      if (names == null || names.length != 1) {
        throw new Exception(
        "One non-option argument expected (filename)!");
      }
      IFile currentFile = new StubFile(names[0]);

      // Get Eclipse to parse the C in the current file
      IASTTranslationUnit ast = null;
      try {
        IASTServiceProvider p = new InternalASTServiceProvider();
        ast = p.getTranslationUnit(currentFile,
            createCodeReaderFactory(),
            new StubConfiguration());
      } catch (Exception e) {
        e.printStackTrace();
        e.getMessage();

        System.out.println("Eclipse had trouble parsing C");
        return;
      }
      CPACheckerLogger.init();

      // this is for catching Ctrl+C and printing statistics even in that
      // case. It might be useful to understand what's going on when
      // the analysis takes a lot of time...
      Runtime.getRuntime().addShutdownHook(new ShutdownHook());

      doRunAnalysis(ast);
      interrupted = false;
    } catch (Exception e) {
      System.out.println(e.getMessage());
      e.printStackTrace();
      System.out.flush();
      System.err.flush();
    }
  }
  
  /**
   * Get the right StubCodeReaderFactory depending on the current CDT version.
   * @return
   * @throws ClassNotFoundException If no matching factory is found.
   */
  private static ICodeReaderFactory createCodeReaderFactory() throws ClassNotFoundException {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    
    String factoryClassName;
    // determine CDT version by trying to load the IMacroCollector class which
    // only exists in CDT 4
    try {
      classLoader.loadClass("org.eclipse.cdt.core.dom.IMacroCollector");
      
      // CDT 4.0
      factoryClassName = "cmdline.stubs.StubCodeReaderFactoryCDT4";
    } catch (ClassNotFoundException e) {
      // not CDT 4.0
      factoryClassName = "cmdline.stubs.StubCodeReaderFactory";
    }

    // try to load factory class and execute the static getInstance() method
    try {
      Class<?> factoryClass = classLoader.loadClass(factoryClassName);
      Object factoryObject = factoryClass.getMethod("getInstance", (Class<?>[]) null)
                                                                  .invoke(null);
      
      return (ICodeReaderFactory) factoryObject;
    } catch (Exception e) {
      // simply wrap all possible exceptions in a ClassNotFoundException
      // this will terminate the program
      throw new ClassNotFoundException("Exception while instantiating " + factoryClassName, e);
    }
  }
}
