/*
 * Copyright (c) 2013 Yan Pujante
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

package org.pongasoft.glu.provisioner.core.metamodel.impl.builder

import org.linkedin.glu.provisioner.core.model.builder.ModelBuilder
import org.linkedin.groovy.util.state.StateMachineImpl
import org.linkedin.util.clock.Timespan
import org.pongasoft.glu.provisioner.core.metamodel.GluMetaModel
import org.pongasoft.glu.provisioner.core.metamodel.KeysMetaModel
import org.pongasoft.glu.provisioner.core.metamodel.impl.AgentCliMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.AgentMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.CliMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.ConsoleCliMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.ConsoleMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.ConsolePluginMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.FabricMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.GluMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.HostMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.InstallMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.KeyStoreMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.KeysMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.PhysicalHostMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.ServerMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.StateMachineMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.ZooKeeperClusterMetaModelImpl
import org.pongasoft.glu.provisioner.core.metamodel.impl.ZooKeeperMetaModelImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author yan@pongasoft.com  */
public class GluMetaModelBuilder extends ModelBuilder<GluMetaModel>
{
  public static final String MODULE = GluMetaModelBuilder.class.getName();
  public static final Logger log = LoggerFactory.getLogger(MODULE);

  String gluVersion

  GluMetaModelImpl gluMetaModel = new GluMetaModelImpl()
  Map<String, FabricMetaModelImpl> fabrics = [:]
  Collection<AgentMetaModelImpl> agents = []
  Map<String, ConsoleMetaModelImpl> consoles = [:]
  Map<String, ZooKeeperClusterMetaModelImpl> zooKeeperClusters = [:]

  @Override
  Map doParseJsonGroovy(String jsonModel)
  {
    GluMetaModelJsonGroovyDsl.parseJsonGroovy(jsonModel)
  }

  GluMetaModelBuilder deserializeFromJsonMap(Map jsonModel)
  {
    // sanity check... leave it open for future versions
    def metaModelVersion = jsonModel.metaModelVersion ?: GluMetaModelImpl.META_MODEL_VERSION
    if(metaModelVersion != GluMetaModelImpl.META_MODEL_VERSION)
      throw new IllegalArgumentException("unsupported meta model version ${metaModelVersion}")

    if(jsonModel.gluVersion)
      gluMetaModel.gluVersion = jsonModel.gluVersion
    else
      gluMetaModel.gluVersion = gluVersion ?: System.getProperty('glu.version')

    // agents
    jsonModel.agents?.each { deserializeAgent(it) }

    if(jsonModel.agentCli)
      gluMetaModel.agentCli = deserializeCli(jsonModel.agentCli, new AgentCliMetaModelImpl())

    // consoles
    jsonModel.consoles?.each { deserializeConsole(it) }

    if(jsonModel.consoleCli)
      gluMetaModel.consoleCli = deserializeCli(jsonModel.consoleCli, new ConsoleCliMetaModelImpl())

    // zookeeperClusters
    jsonModel.zooKeeperClusters?.each { deserializeZooKeeperCluster(it) }

    // zooKeeperRoot ?
    if(jsonModel.zooKeeperRoot)
      gluMetaModel.zooKeeperRoot = jsonModel.zooKeeperRoot

    // state machine?
    gluMetaModel.stateMachine = deserializeStateMachine(jsonModel.stateMachine)

    // fabrics
    jsonModel.fabrics?.each { name, fabricModel -> deserializeFabric(name, fabricModel)}

    return this
  }

  StateMachineMetaModelImpl deserializeStateMachine(Map stateMachineModel)
  {
    if(stateMachineModel)
    {
      StateMachineImpl stateMachine =
        new StateMachineImpl(transitions: stateMachineModel.defaultTransitions)

      return new StateMachineMetaModelImpl(defaultTransitions: stateMachine,
                                           defaultEntryState: stateMachineModel.defaultEntryState)

    }
    else
      return null
  }

  /**
   * Deserialize a fabric
   */
  void deserializeFabric(String fabricName, Map fabricModel)
  {
    FabricMetaModelImpl fabric = findOrCreateFabric(fabricName)

    // handling console
    if(fabricModel.console)
    {
      if(fabric.console?.name && (fabricModel.console != fabric.console.name))
        throw new IllegalArgumentException("trying to redefine console for fabric [${fabricName}] ([${fabricModel.console}] != [${fabric.console.name}]")

      fabric.console = consoles[fabricModel.console]
      if(!fabric.console)
        throw new IllegalArgumentException("could not find console [${fabricModel.console}] for fabric [${fabricName}]")

      fabric.console.fabrics[fabricName] = fabric
    }

    // handling zooKeeperCluster
    if(fabricModel.zooKeeperCluster)
    {
      if(fabric.zooKeeperCluster?.name && (fabricModel.zooKeeperCluster != fabric.zooKeeperCluster.name))
        throw new IllegalArgumentException("trying to redefine zooKeeperCluster for fabric [${fabricName}] ([${fabricModel.zooKeeperCluster}] != [${fabric.zooKeeperCluster.name}]")

      fabric.zooKeeperCluster = zooKeeperClusters[fabricModel.zooKeeperCluster]
      if(!fabric.zooKeeperCluster)
        throw new IllegalArgumentException("could not find zooKeeperCluster [${fabricModel.zooKeeperCluster}] for fabric [${fabricName}]")

      fabric.zooKeeperCluster.fabrics[fabricName] = fabric
    }

    fabric.color = fabricModel.color

    fabric.keys = deserializeKeys(fabricModel.keys)
  }

  /**
   * Deserialize an agent
   */
  void deserializeAgent(Map agentModel)
  {
    AgentMetaModelImpl agent =
      deserializeServer(agentModel, new AgentMetaModelImpl(name: agentModel.name))

    if(!agent.resolvedName)
      throw new IllegalArgumentException("missing agent name or host for ${agentModel}")

    def fabric = findOrCreateFabric(agentModel.fabric)

    if(fabric)
    {
      if(fabric.agents.containsKey(agent.resolvedName))
        throw new IllegalArgumentException("duplicate agent name [${agent.resolvedName}] for fabric [${fabric.name}]")

      // linking the 2
      agent.fabric = fabric
      fabric.agents[agent.resolvedName] = agent
    }

    agents << agent
  }

  /**
   * Deserialize a console which can handle multiple fabrics
   */
  void deserializeConsole(Map consoleModel)
  {
    def plugins = consoleModel.plugins?.collect { deserializeConsolePlugin(it) }

    ConsoleMetaModelImpl console =
      deserializeServer(consoleModel,
                        new ConsoleMetaModelImpl(name: consoleModel.name ?: 'default',
                                                 fabrics: [:],
                                                 plugins: plugins ?: [],
                                                 externalHost: consoleModel.externalHost,
                                                 internalPath: consoleModel.internalPath,
                                                 externalPath: consoleModel.externalPath))

    if(consoleModel.dataSourceDriverUri)
      console.dataSourceDriverUri = new URI(consoleModel.dataSourceDriverUri)

    consoles[console.name] = console
  }

  /**
   * ZooKeeper cluster which is made of ZooKeeper servers and represent multiple fabrics
   */
  void deserializeZooKeeperCluster(Map zooKeeperClusterModel)
  {
    ZooKeeperClusterMetaModelImpl zooKeeperCluster =
      new ZooKeeperClusterMetaModelImpl(name: zooKeeperClusterModel.name ?: 'default',
                                        fabrics: [:],
                                        zooKeeperSessionTimeout: Timespan.parse(zooKeeperClusterModel.zooKeeperSessionTimeout),
                                        gluMetaModel: gluMetaModel)

    // handle the zookeepers making up the cluster
    def zooKeepers = []
    zooKeeperClusterModel.zooKeepers?.each {
      def zooKeeper = deserializeZooKeeper(it)
      // linking the 2
      zooKeeper.zooKeeperCluster = zooKeeperCluster
      zooKeepers << zooKeeper
    }
    zooKeeperCluster.zooKeepers = zooKeepers
    zooKeeperCluster.configTokens = deserializeConfigTokens(zooKeeperClusterModel.configTokens)

    zooKeeperClusters[zooKeeperCluster.name] = zooKeeperCluster
  }

  /**
   * A given ZooKeeper server (part of a cluster)
   */
  ZooKeeperMetaModelImpl deserializeZooKeeper(Map zooKeeperModel)
  {
    deserializeServer(zooKeeperModel, new ZooKeeperMetaModelImpl())
  }

  /**
   * @return a fabric (<code>null</code> if <code>modelFabricName</code> is <code>null</code>)
   */
  private FabricMetaModelImpl findOrCreateFabric(String modelFabricName)
  {
    if(!modelFabricName)
      return null

    FabricMetaModelImpl fabric = fabrics[modelFabricName]

    if(!fabric)
    {
      fabric = new FabricMetaModelImpl(name: modelFabricName,
                                       agents: [:],
                                       gluMetaModel: gluMetaModel)

      fabrics[modelFabricName] = fabric
    }

    return fabric
  }

  /**
   * Deserializes the cli model piece (super class).
   *
   * @return <code>impl</code>
   */
  private <T extends CliMetaModelImpl> T deserializeCli(Map cliModel, T impl)
  {
    impl.version = cliModel.version
    impl.gluMetaModel = gluMetaModel
    impl.host = deserializeHostMetaModel(cliModel.host ?: 'localhost')
    impl.install = deserializeInstall(cliModel.install)
    impl.configTokens = deserializeConfigTokens(cliModel.configTokens)

    return impl
  }

  /**
   * Deserializes the server model piece (super class).
   *
   * @return <code>impl</code>
   */
  private <T extends ServerMetaModelImpl> T deserializeServer(Map serverModel, T impl)
  {
    impl = deserializeCli(serverModel, impl)

    Map<String, Integer> ports = [:]
    if(serverModel.port)
      ports[ServerMetaModelImpl.MAIN_PORT_KEY] = serverModel.port
    if(serverModel.ports)
      ports.putAll(serverModel.ports)
    impl.ports = Collections.unmodifiableMap(ports)

    return impl
  }

  private ConsolePluginMetaModelImpl deserializeConsolePlugin(Map consolePluginModel)
  {
    ConsolePluginMetaModelImpl impl = new ConsolePluginMetaModelImpl(fqcn: consolePluginModel.fqcn)
    if(consolePluginModel.classPath)
    {
      impl.classPath = consolePluginModel.classPath.collect { new URI(it) }
    }
    return impl
  }

  private InstallMetaModelImpl deserializeInstall(Map installModel)
  {
    if(installModel)
    {
      new InstallMetaModelImpl(path: installModel.path)
    }
    else
      return null
  }

  private Map<String, Object> deserializeConfigTokens(Map configTokens)
  {
    if(configTokens == null)
      configTokens = [:]
    return Collections.unmodifiableMap(configTokens)
  }

  HostMetaModelImpl deserializeHostMetaModel(def host)
  {
    if(host instanceof String || host instanceof GString)
      new PhysicalHostMetaModelImpl(hostAddress: host)
    else
      throw new IllegalArgumentException("unsupported host type [${host}]")
  }

  KeysMetaModelImpl deserializeKeys(Map keysModel)
  {
    if(keysModel == null)
      return null

    KeysMetaModelImpl keysMetaModel = new KeysMetaModelImpl()

    [
      'agentKeyStore',
      'agentTrustStore',
      'consoleKeyStore',
      'consoleTrustStore'
    ].each { storeName ->
      keysMetaModel."${storeName}" = deserializeKeyStore(keysModel[storeName])
    }

    return keysMetaModel
  }

  KeyStoreMetaModelImpl deserializeKeyStore(Map keyStoreModel)
  {
    if(keyStoreModel == null)
      return null

    new KeyStoreMetaModelImpl(uri: new URI(keyStoreModel.uri),
                              checksum: keyStoreModel.checksum,
                              storePassword: keyStoreModel.storePassword,
                              keyPassword: keyStoreModel.keyPassword)
  }

  GluMetaModel toModel()
  {
    def newFabrics = fabrics.collectEntries { name, fabric ->
      fabric.agents = Collections.unmodifiableMap(fabric.agents)
      [name, fabric]
    }
    consoles.values().each { console ->
      console.fabrics = Collections.unmodifiableMap(console.fabrics)
      // sanity check (only one set of keys per console... may change in the future...)
      if(console.fabrics.size() > 1)
      {
        Map<String, KeysMetaModel> allKeys = console.fabrics.collectEntries { k, v -> [k, v.keys] }

        def fabricsWithDifferentKeys = allKeys.groupBy { k, v -> v }

        if(fabricsWithDifferentKeys.size() > 1)
        {
          throw new IllegalArgumentException("only one set of keys supported (at this time) in a given console [${console.name}]: those fabrics [${fabricsWithDifferentKeys.keySet()}] have different keys")
        }
      }
    }
    zooKeeperClusters.values().each { zooKeeperCluster ->
      zooKeeperCluster.fabrics = Collections.unmodifiableMap(zooKeeperCluster.fabrics)
      zooKeeperCluster.zooKeepers = Collections.unmodifiableList(zooKeeperCluster.zooKeepers)
    }
    gluMetaModel.fabrics = Collections.unmodifiableMap(newFabrics)

    gluMetaModel.agents = Collections.unmodifiableCollection(agents)
    gluMetaModel.consoles = Collections.unmodifiableMap(consoles)
    gluMetaModel.zooKeeperClusters = Collections.unmodifiableMap(zooKeeperClusters)

    return gluMetaModel
  }
}