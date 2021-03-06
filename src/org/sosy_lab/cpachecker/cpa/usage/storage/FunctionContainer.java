/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usage.storage;

import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sosy_lab.cpachecker.cpa.lock.effects.LockEffect;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@SuppressFBWarnings(
  justification = "Serialization of container is useless and not supported",
  value = "SE_BAD_FIELD"
)
public class FunctionContainer extends AbstractUsageStorage {

  private static final long serialVersionUID = 1L;
  // private final Set<FunctionContainer> internalFunctionContainers;
  private final List<LockEffect> effects;
  private final StorageStatistics stats;

  private final Set<FunctionContainer> joinedWith;

  private final Set<TemporaryUsageStorage> storages;

  public static FunctionContainer createInitialContainer() {
    return new FunctionContainer(new StorageStatistics(), new ArrayList<>());
  }

  private FunctionContainer(StorageStatistics pStats, List<LockEffect> pEffects) {
    super();
    stats = pStats;
    stats.numberOfFunctionContainers.inc();
    effects = pEffects;
    joinedWith = new IdentityHashSet<>();
    storages = new HashSet<>();
  }

  public FunctionContainer clone(List<LockEffect> pEffects) {
    return new FunctionContainer(this.stats, pEffects);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hashCode(effects);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  public void join(FunctionContainer funcContainer) {
    stats.totalJoins.inc();
    if (joinedWith.contains(funcContainer)) {
      //We may join two different exit states to the same parent container
      stats.hitTimes.inc();
      return;
    }
    if (!funcContainer.isEmpty() || !funcContainer.joinedWith.isEmpty()) {
      joinedWith.add(funcContainer);
    }
  }

  public void join(TemporaryUsageStorage pRecentUsages) {
    stats.copyTimer.start();
    copyUsagesFrom(pRecentUsages);
    stats.copyTimer.stop();
  }

  public void clearStorages() {
    storages.forEach(TemporaryUsageStorage::clear);
    storages.clear();
  }

  public void registerTemporaryContainer(TemporaryUsageStorage storage) {
    storages.add(storage);
  }

  public StorageStatistics getStatistics() {
    return stats;
  }

  public Set<FunctionContainer> getContainers() {
    return joinedWith;
  }

  public List<LockEffect> getLockEffects() {
    return effects;
  }

  public static class StorageStatistics {
    private StatCounter hitTimes = new StatCounter("Number of hits into cache");
    private StatCounter totalJoins = new StatCounter("Total number of joins");
    private StatCounter numberOfFunctionContainers = new StatCounter("Total number of function containers");

    private StatTimer copyTimer = new StatTimer("Time for coping usages");

    public void printStatistics(StatisticsWriter out) {
      out.spacer()
         .put(copyTimer)
         .put(totalJoins)
         .put(hitTimes)
         .put(numberOfFunctionContainers);
    }
  }
}
