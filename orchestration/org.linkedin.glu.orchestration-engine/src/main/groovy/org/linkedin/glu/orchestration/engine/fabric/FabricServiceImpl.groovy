/*
 * Copyright (c) 2010-2010 LinkedIn, Inc
 * Portions Copyright (c) 2011-2013 Yan Pujante
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

package org.linkedin.glu.orchestration.engine.fabric

import org.apache.zookeeper.ZooDefs.Ids
import org.apache.zookeeper.CreateMode
import org.apache.zookeeper.KeeperException.NoNodeException
import org.linkedin.util.lifecycle.Configurable
import org.linkedin.util.clock.Timespan
import org.linkedin.zookeeper.client.ZKClient
import org.linkedin.util.lifecycle.Destroyable
import org.linkedin.glu.agent.rest.client.ConfigurableFactory
import org.linkedin.util.annotations.Initializable
import org.linkedin.zookeeper.client.IZKClient
import org.apache.zookeeper.KeeperException

/**
 * This service will manage fabrics
 *
 * @author ypujante@linkedin.com */
class FabricServiceImpl implements FabricService, Destroyable
{
  public static final String MODULE = FabricServiceImpl.class.getName();
  public static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MODULE);

  @Initializable
  ConfigurableFactory configurableFactory

  @Initializable(required = true)
  FabricStorage fabricStorage

  @Initializable
  String zookeeperAgentsFabricRoot = "/org/glu/agents/names"

  @Initializable
  String prefix = "glu"

  @Initializable
  Timespan zkClientWaitForStartTimeout = Timespan.parse('10s')

  /**
   * In memory cache of fabrics (small list, changes rarely...)
   */
  private volatile Map<String, FabricWithZkClient> _fabrics = [:]

  /**
   * @return a collection of all the fabrics known by the service (cached list)
   */
  Collection<Fabric> getFabrics()
  {
    return loadFabrics().values().collect { it.fabric }
  }

  /**
   * @return a map where the key is the agent name and the value is the fabric name the agent
   * belongs (as defined in ZooKeeper)
   */
  Map<String, String> getAgents()
  {
    def agents = [:]

    loadFabrics().values().zkClient.each {
      agents.putAll(doGetAgents(it))
    }

    return agents
  }

  /**
   * @param zkClient which ZooKeeper connection to use
   * @return a map where the key is the agent name and the value is the fabric the agent has been
   * assigned to or <code>null</code> if the agent has not been assigned to any fabric
   */
  private Map<String, String> doGetAgents(IZKClient zkClient)
  {
    def agents = [:]

    if(zkClient)
    {
      try
      {
        zkClient.getChildren(zookeeperAgentsFabricRoot).each { agent ->
          def path = computeZKAgentsFabricPath(agent)

          if(zkClient.exists(path))
          {
            agents[agent] = zkClient.getStringData(path)
          }
          else
          {
            agents[agent] = null
          }
        }
      }
      catch(NoNodeException e)
      {
        if(log.isDebugEnabled())
        {
          log.debug("ignored exception", e)
        }
      }
    }

    return agents
  }

  protected String computeZKAgentsFabricPath(String agentName)
  {
    "${computeZKAgentsPath(agentName)}/fabric".toString()
  }

  protected String computeZKAgentsPath(String agentName)
  {
    "${zookeeperAgentsFabricRoot}/${agentName}".toString()
  }

  /**
   * @return the fabric or <code>null</code>
   */
  Fabric findFabric(String fabricName)
  {
    return findFabricWithZkClient(fabricName)?.fabric
  }

  /**
   * @return the fabric or <code>null</code>
   */
  private FabricWithZkClient findFabricWithZkClient(String fabricName)
  {
    return loadFabrics()[fabricName]
  }

  /**
   * @return the list of fabric names
   */
  Collection<String> listFabricNames()
  {
    return loadFabrics().keySet().collect { it }
  }

  /**
   * Executes the closure with the ZooKeeper connection (instance of {@link IZKClient}) associated
   * to the fabric
   *
   * @return whatever the closure returns
   */
  def withZkClient(String fabricName, Closure closure)
  {
    closure(findFabricWithZkClient(fabricName)?.zkClient)
  }

  @Override
  boolean isConnected(String fabricName)
  {
    withZkClient(fabricName) { IZKClient zkClient ->
      zkClient?.isConnected()
    }
  }

  /**
   * Sets the fabric for the given agent: write it in ZooKeeper
   */
  void setAgentFabric(String agentName, String fabricName)
  {
    withZkClient(fabricName) { zkClient ->
      zkClient.createOrSetWithParents(computeZKAgentsFabricPath(agentName),
                                      fabricName,
                                      Ids.OPEN_ACL_UNSAFE,
                                      CreateMode.PERSISTENT)
    }
  }

  /**
   * Clears the fabric for the given agent (from ZooKeeper)
   */
  boolean clearAgentFabric(String agentName, String fabricName)
  {
    withZkClient(fabricName) { zkClient ->
      try
      {
        zkClient.deleteWithChildren(computeZKAgentsPath(agentName))
        return true
      }
      catch (KeeperException.NoNodeException e)
      {
        return false
      }
    }
  }

  /**
   * Configures the agent on the given host (builds a default config url)
   */
  void configureAgent(InetAddress host, String fabricName)
  {
    doConfigureAgent(host.hostAddress, fabricName)
  }

  /**
   * Configures the agent given its configuration URI
   */
  void configureAgent(URI agentConfigURI, String fabricName)
  {
    doConfigureAgent(agentConfigURI, fabricName)
  }

  /**
   * Configures the agent given its configuration URI
   */
  private void doConfigureAgent(def agent, String fabricName)
  {
    def zkConnectString = findFabric(fabricName).zkConnectString
    configurableFactory?.withRemoteConfigurable(agent) { Configurable c ->
      // YP note: using GString as a key in a map is a recipe for disaster => using string!
      def config = [:]
      config["${prefix}.agent.zkConnectString".toString()] = zkConnectString
      config["${prefix}.agent.fabric".toString()] = fabricName
      c.configure(config)
    }
  }

  /**
   * resets the fabrics cache which will force the connection to ZooKeeper to be dropped.
   */
  synchronized void resetCache()
  {
    _fabrics.values().zkClient.each {
      it.destroy()
    }

    _fabrics = [:]

    log.info "Cache cleared"
  }

  private synchronized Map<String, FabricWithZkClient> loadFabrics()
  {
    if(!_fabrics)
    {
      def fabrics = [:]

      fabricStorage.loadFabrics().each { Fabric fabric ->
        try
        {
          FabricWithZkClient fi = new FabricWithZkClient(fabric: fabric)

          fi.zkClient = new ZKClient(fabric.zkConnectString,
                                     fabric.zkSessionTimeout,
                                     null)
          fi.zkClient.start()
          fabrics[fabric.name] = fi
          fi.zkClient.waitForStart(zkClientWaitForStartTimeout)
        }
        catch (Exception e)
        {
          log.warn("Could not connect to fabric ${fabric.name}: [${e.message}]... ignoring")
          if(log.isDebugEnabled())
            log.debug("Could not connect to fabric ${fabric.name}... ignoring", e)
        }
      }

      _fabrics = fabrics

      if(_fabrics)
        log.info "Loaded fabrics: ${_fabrics.values().fabric.name}"
    }

    return _fabrics
  }

  public synchronized void destroy()
  {
    _fabrics.values().zkClient.each {
      it.destroy()
    }
  }
}
