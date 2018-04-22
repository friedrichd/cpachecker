/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.parser.eclipse.js;

import static org.sosy_lab.cpachecker.cfa.ast.java.QualifiedNameBuilder.qualifiedNameOf;

import java.util.function.BiFunction;
import org.eclipse.wst.jsdt.core.dom.ConditionalExpression;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.js.JSExpression;
import org.sosy_lab.cpachecker.cfa.ast.js.JSExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.js.JSIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.js.JSVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.AbstractCFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.js.JSAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.js.JSDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.js.JSStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.js.JSAnyType;

class ConditionalExpressionCFABuilder implements ConditionalExpressionAppendable {

  @Override
  public JSExpression append(
      final JavaScriptCFABuilder pBuilder, final ConditionalExpression pConditionalExpression) {
    final String resultVariableName = pBuilder.generateVariableName();
    final JSVariableDeclaration resultVariableDeclaration =
        new JSVariableDeclaration(
            FileLocation.DUMMY,
            false,
            JSAnyType.ANY,
            resultVariableName,
            resultVariableName,
            qualifiedNameOf(pBuilder.getFunctionName(), resultVariableName),
            null);
    final JSIdExpression resultVariableId =
        new JSIdExpression(
            FileLocation.DUMMY, JSAnyType.ANY, resultVariableName, resultVariableDeclaration);
    pBuilder.appendEdge(
        (pPredecessor, pSuccessor) ->
            new JSDeclarationEdge(
                resultVariableDeclaration.toASTString(),
                resultVariableDeclaration.getFileLocation(),
                pPredecessor,
                pSuccessor,
                resultVariableDeclaration));
    final CFANode exitNode = pBuilder.createNode();
    final JSExpression condition = pBuilder.append(pConditionalExpression.getExpression());

    final JavaScriptCFABuilder thenBranchBuilder =
        pBuilder.copy().appendEdge(assume(condition, true));
    final JSExpression thenValue =
        thenBranchBuilder.append(pConditionalExpression.getThenExpression());
    final JSExpressionAssignmentStatement thenStatement =
        new JSExpressionAssignmentStatement(FileLocation.DUMMY, resultVariableId, thenValue);
    final String operatorRightExprDescription =
        "? "
            + pConditionalExpression.getThenExpression()
            + " : "
            + pConditionalExpression.getElseExpression();
    pBuilder.addParseResult(
        thenBranchBuilder
            .appendEdge(
                (pPredecessor, pSuccessor) ->
                    new JSStatementEdge(
                        thenStatement.toASTString(),
                        thenStatement,
                        thenStatement.getFileLocation(),
                        pPredecessor,
                        pSuccessor))
            .appendEdge(
                exitNode, DummyEdge.withDescription("end true " + operatorRightExprDescription))
            .getParseResult());
    final JavaScriptCFABuilder elseBranchBuilder =
        pBuilder.copy().appendEdge(assume(condition, false));
    final JSExpression elseValue =
        elseBranchBuilder.append(pConditionalExpression.getElseExpression());
    final JSExpressionAssignmentStatement elseStatement =
        new JSExpressionAssignmentStatement(FileLocation.DUMMY, resultVariableId, elseValue);
    pBuilder.append(
        elseBranchBuilder
            .appendEdge(
                (pPredecessor, pSuccessor) ->
                    new JSStatementEdge(
                        elseStatement.toASTString(),
                        elseStatement,
                        elseStatement.getFileLocation(),
                        pPredecessor,
                        pSuccessor))
            .appendEdge(
                exitNode, DummyEdge.withDescription("end false " + operatorRightExprDescription))
            .getBuilder());
    return resultVariableId;
  }

  private BiFunction<CFANode, CFANode, AbstractCFAEdge> assume(
      final JSExpression pCondition, final boolean pTruthAssumption) {
    return (pPredecessor, pSuccessor) ->
        new JSAssumeEdge(
            pCondition.toASTString(),
            pCondition.getFileLocation(),
            pPredecessor,
            pSuccessor,
            pCondition,
            pTruthAssumption);
  }
}