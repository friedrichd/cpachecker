/*
 *  CPAchecker is a tool for configurable software verification.
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
 */
package org.sosy_lab.cpachecker.util.statistics.output;

import java.io.File;
import java.io.PrintStream;
import java.util.function.Supplier;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.storage.StatStorage;

/**
 * Implements {@link Statistics} and therefore can be used to print statistics in the default
 * statistics file.
 */
public class BasicStatOutputStrategy extends StatOutputStrategy implements Statistics {

  private final String name;
  private final StatStorage storage;

  public BasicStatOutputStrategy(String name, StatStorage baseStorage, Supplier<String> loadTemplate) {
    super(loadTemplate);
    this.name = name;
    this.storage = baseStorage;
  }

  public BasicStatOutputStrategy(String name, StatStorage baseStorage, File templateFile) {
    super(templateFile);
    this.name = name;
    this.storage = baseStorage;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public void printStatistics(PrintStream out, Result result, UnmodifiableReachedSet reached) {
    out.print(replaceVariables(storage.getVariableMap()));
  }

}