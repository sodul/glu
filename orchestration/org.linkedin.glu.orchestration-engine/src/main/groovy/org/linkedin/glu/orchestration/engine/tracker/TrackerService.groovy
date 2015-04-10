/*
 * Copyright (c) 2011-2014 Yan Pujante
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.linkedin.glu.orchestration.engine.tracker

import org.linkedin.glu.agent.api.MountPoint
import org.linkedin.glu.agent.tracker.AgentInfo
import org.linkedin.glu.agent.tracker.MountPointInfo
import org.linkedin.glu.orchestration.engine.fabric.Fabric

/**
 * @author ypujante
 */
interface TrackerService
{
  Map<String, AgentInfo> getAgentInfos(Fabric fabric)

  AgentInfo getAgentInfo(String fabric, String agentName)

  AgentInfo getAgentInfo(Fabric fabric, String agentName)

  /**
   * @return a map [accuracy: _accuracyLevel_,
   *                allInfos: [_agentName_: [info: _agentInfo_, mountPoints: _mountPointInfos_]]
   */
  def getAllInfosWithAccuracy(Fabric fabric)

  Map<MountPoint, MountPointInfo> getMountPointInfos(Fabric fabric, String agentName)

  MountPointInfo getMountPointInfo(Fabric fabric, String agentName, mountPoint)

  /**
   * Clears the agent info for the given agent
   * @return <code>true</code> if the agent was cleared, <code>false</code> if it was already cleared
   * @throws IllegalStateException when the agent is still up!
   */
  boolean clearAgentInfo(Fabric fabric, String agentName)

  /**
   * Clears the agent info for the given agent
   * @return <code>true</code> if the agent was cleared, <code>false</code> if it was already cleared
   * @throws IllegalStateException when the agent is still up!
   */
  boolean clearAgentInfo(String fabric, String agentName)

  boolean waitForState(String fabric, String agentName, def mountPoint, String state, def timeout)

  boolean waitForState(Fabric fabric, String agentName, def mountPoint, String state, def timeout)
}
