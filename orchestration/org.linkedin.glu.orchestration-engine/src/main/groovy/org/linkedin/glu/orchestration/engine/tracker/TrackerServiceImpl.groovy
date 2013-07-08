/*
 * Copyright (c) 2010-2010 LinkedIn, Inc
 * Portions Copyright (c) 2011-2012 Yan Pujante
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

import org.linkedin.groovy.util.concurrent.GroovyConcurrentUtils
import org.linkedin.util.clock.Clock
import org.linkedin.util.clock.SystemClock

import java.util.concurrent.TimeoutException
import org.apache.zookeeper.WatchedEvent
import org.linkedin.glu.agent.tracker.AgentsTracker
import org.linkedin.glu.agent.tracker.AgentsTrackerImpl
import org.linkedin.glu.agent.tracker.TrackerEventsListener
import org.linkedin.glu.orchestration.engine.fabric.Fabric
import org.linkedin.glu.orchestration.engine.fabric.FabricService
import org.linkedin.util.clock.Chronos
import org.linkedin.util.lifecycle.Destroyable
import org.linkedin.zookeeper.client.IZKClient
import org.linkedin.zookeeper.tracker.ErrorListener
import org.linkedin.zookeeper.tracker.NodeEventType
import org.linkedin.glu.agent.tracker.AgentInfo
import org.linkedin.glu.agent.tracker.MountPointInfo
import org.linkedin.glu.agent.api.MountPoint
import org.linkedin.util.annotations.Initializable
import org.linkedin.glu.agent.tracker.PrefixAgentInfoPropertyAccessor
import org.linkedin.glu.agent.tracker.AgentInfoPropertyAccessor

/**
 * @author ypujante
 */
class TrackerServiceImpl implements TrackerService, Destroyable
{
  public static final String MODULE = TrackerServiceImpl.class.getName();
  public static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MODULE);

  @Initializable(required = true)
  FabricService fabricService

  @Initializable
  String zookeeperRoot = "/org/glu"

  @Initializable
  AgentInfoPropertyAccessor agentInfoPropertyAccessor = PrefixAgentInfoPropertyAccessor.DEFAULT

  @Initializable
  Clock clock = SystemClock.INSTANCE

  private final def _trackers = [:]

  Map<String, AgentInfo> getAgentInfos(Fabric fabric)
  {
    return getAgentsTrackerByFabric(fabric).getAgentInfos()
  }

  AgentInfo getAgentInfo(String fabric, String agentName)
  {
    getAgentInfo(fabricService.findFabric(fabric), agentName)
  }

  AgentInfo getAgentInfo(Fabric fabric, String agentName)
  {
    return getAgentsTrackerByFabric(fabric).getAgentInfo(agentName)
  }

  def getAllInfosWithAccuracy(Fabric fabric)
  {
    return getAgentsTrackerByFabric(fabric).getAllInfosWithAccuracy()
  }

  Map<MountPoint, MountPointInfo> getMountPointInfos(Fabric fabric, String agentName)
  {
    return getAgentsTrackerByFabric(fabric).getMountPointInfos(agentName)
  }

  MountPointInfo getMountPointInfo(Fabric fabric, String agentName, mountPoint)
  {
    return getAgentsTrackerByFabric(fabric).getMountPointInfo(agentName, mountPoint)
  }

  @Override
  boolean clearAgentInfo(Fabric fabric, String agentName)
  {
    return getAgentsTrackerByFabric(fabric).clearAgentInfo(agentName)
  }

  @Override
  boolean clearAgentInfo(String fabric, String agentName)
  {
    return clearAgentInfo(fabricService.findFabric(fabric), agentName)
  }

  private final Object _mountPointEventsLock = new Object()

  @Override
  boolean waitForState(String fabric, String agentName, def mountPoint, String state, def timeout)
  {
    waitForState(fabricService.findFabric(fabric), agentName, mountPoint, state, timeout)
  }

  @Override
  boolean waitForState(Fabric fabric, String agentName, def mountPoint, String state, def timeout)
  {
    try
    {
      GroovyConcurrentUtils.awaitFor(clock, timeout, _mountPointEventsLock) {
        // this code is synchronized (see awaitFor documentation!)

        def mpi = getMountPointInfo(fabric, agentName, mountPoint)

        if(mpi?.error)
          return true

        return !mpi?.transitionState && mpi?.currentState == state
      }
    }
    catch(TimeoutException ignore)
    {
      return false;
    }

    return true
  }

  private def mountPointEventsListener = { events ->
    if(events)
    {
      synchronized(_mountPointEventsLock)
      {
        _mountPointEventsLock.notifyAll()
      }
    }
  }

  private synchronized AgentsTracker getAgentsTrackerByFabric(Fabric fabric)
  {
    def fabricName = fabric.name
    AgentsTracker tracker = _trackers[fabricName]?.tracker

    // we make sure that the fabric has not changed otherwise we need to change the tracker
    if(tracker && _trackers[fabricName]?.fabric != fabric)
    {
      tracker.destroy()
      tracker = null
      _trackers[fabricName] = null
    }

    if(!tracker)
    {
      if(fabric)
      {
        fabricService.withZkClient(fabricName) { IZKClient zkClient ->
          tracker = new AgentsTrackerImpl(zkClient,
                                          "${zookeeperRoot}/agents/fabrics/${fabricName}".toString())
          tracker.agentInfoPropertyAccessor = agentInfoPropertyAccessor
        }

        _trackers[fabricName] = [tracker: tracker, fabric: fabric]

        def eventsListener = { events ->
          events.each { event ->
            switch(event.eventType)
            {
              case NodeEventType.ADDED:
                if(log.isDebugEnabled())
                  log.debug "added ${event.nodeInfo.agentName} to fabric ${fabricName}"
                break

              case NodeEventType.DELETED:
              if(log.isDebugEnabled())
                log.debug "removed ${event.nodeInfo.agentName} from fabric ${fabricName}"
              break
            }
          }
        }
        tracker.registerAgentListener(eventsListener as TrackerEventsListener)

        def errorListener = { WatchedEvent event, Throwable throwable ->
          log.warn("Error detected in agent with ${event}", throwable)
        }

        tracker.registerErrorListener(errorListener as ErrorListener)

        tracker.registerMountPointListener(mountPointEventsListener as TrackerEventsListener)

        tracker.start()

        def timeout = '10s'
        try
        {
          Chronos c = new Chronos()
          log.info "Waiting for tracker ${fabricName} to start for ${timeout}..."
          tracker.waitForStart(timeout)
          log.info "Tracker for ${fabricName} successfully started in ${c.elapsedTimeAsHMS}"
        }
        catch(TimeoutException ignore)
        {
          log.warn "Tracker for ${fabricName} still not started after ${timeout}... continuing..."
        }

        if(log.isDebugEnabled())
          log.debug "Added tracker for ${fabricName}"
      }
      else
      {
        throw new IllegalArgumentException("unknown fabric ${fabricName}".toString())
      }
    }

    return tracker
  }

  public synchronized void destroy()
  {
    _trackers.values().each { map ->
      map.tracker.destroy()
    }
  }
}
