/*
 * Copyright (c) 2010-2010 LinkedIn, Inc
 * Portions Copyright (c) 2011-2014 Yan Pujante
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


package org.linkedin.glu.agent.impl.script

import org.linkedin.glu.agent.api.MountPoint
import org.linkedin.glu.agent.api.DuplicateMountPointException
import org.linkedin.glu.agent.api.NoSuchMountPointException
import org.linkedin.glu.agent.api.ScriptException
import org.linkedin.glu.agent.api.Agent
import org.linkedin.glu.groovy.utils.GluGroovyLangUtils
import org.linkedin.groovy.util.lang.GroovyLangUtils
import org.slf4j.Logger

import org.linkedin.glu.agent.api.ScriptIllegalStateException
import org.linkedin.glu.agent.api.Timers
import org.linkedin.glu.agent.api.StateManager
import org.linkedin.util.clock.Timespan
import org.linkedin.groovy.util.state.StateMachine
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeoutException
import org.linkedin.groovy.util.concurrent.GroovyConcurrentUtils

import org.linkedin.util.annotations.Initializable
import org.linkedin.glu.groovy.utils.concurrent.FutureExecution

/**
 * Manager for scripts
 *
 * @author ypujante@linkedin.com
 */
def class ScriptManagerImpl implements ScriptManager
{
  public static final String MODULE = ScriptManagerImpl.class.getName();
  public static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MODULE);

  final Map<MountPoint, ScriptNode> _scripts = new LinkedHashMap<MountPoint, ScriptNode>()

  @Initializable(required = true)
  ScriptFactory rootScriptFactory = new FromClassNameScriptFactory(RootScript)

  @Initializable(required = true)
  AgentContext agentContext

  @Initializable
  ScriptFactoryFactory scriptFactoryFactory = new ScriptFactoryFactoryImpl()

  private volatile boolean _shutdown = false
  Timespan scriptGracePeriod1 = Timespan.parse('1s')
  Timespan scriptGracePeriod2 = Timespan.parse('1m')

  synchronized ScriptNode installRootScript(actionArgs)
  {
    def scriptConfig = new ScriptConfig(shell: agentContext.shellForScripts,
                                        agentContext: agentContext)

    def rootScript = rootScriptFactory.createScript(scriptConfig)

    ScriptDefinition sd = new ScriptDefinition(MountPoint.ROOT,
                                               null,
                                               rootScriptFactory,
                                               [:])

    def rootNode = createNode(scriptConfig, sd, rootScript)

    addScriptNode(rootNode)

    rootNode.executeAction(action: 'install', actionArgs: actionArgs).get()

    rootNode.log.info("installed")

    return rootNode
  }

  /**
   * the root script
   */
  synchronized def getRootScript()
  {
    return _scripts[MountPoint.ROOT]
  }

  synchronized def getMountPoints()
  {
    return _scripts.keySet().collect { it }
  }

  /**
   * @return <code>true</code> if there is a script mounted at the given mount point
   */
  synchronized boolean isMounted(mountPoint)
  {
    mountPoint = MountPoint.create(mountPoint)
    return _scripts[mountPoint] != null
  }

  /**
   * Install scripts.
   *
   * @see Agent#installScript(Object) for details
   */
  synchronized ScriptNode installScript(args)
  {
    // handle mountPoint first
    def mountPoint = MountPoint.create(args.mountPoint)
    if(!mountPoint)
      throw new IllegalArgumentException('mountPoint is required')

    // first we check if the script is already installed
    if(_scripts[mountPoint])
      throw new DuplicateMountPointException(mountPoint.path)

    // now we locate the right node
    ScriptNode parentNode = getScript(args.parent ?: MountPoint.ROOT)

    def initParameters = args.initParameters ?: [:]

    ScriptDefinition sd = new ScriptDefinition(mountPoint,
                                               parentNode.mountPoint,
                                               scriptFactoryFactory.createScriptFactory(args),
                                               initParameters)

    // each script will have its shell pointing to a different tmp root relative to the mount point
    def shell = agentContext.shellForScripts
    def fs = shell.fileSystem
    shell = shell.newShell(fs.newFileSystem(fs.root,
                                            fs.tmpRoot.createRelative(mountPoint.path)))

    def scriptConfig = new ScriptConfig(agentContext: agentContext,
                                        shell: shell)
    
    def childScript

    try
    {
      childScript = sd.scriptFactory.createScript(scriptConfig)
    }
    catch(Exception e)
    {
      log.warn("Error while instantiating script: mp=${mountPoint}; ex=${GluGroovyLangUtils.toString(e)}; args=${args}")
      if(log.isDebugEnabled())
        log.debug("Error while instantiating script: ${mountPoint}", e)
      throw new ScriptException(mountPoint.path, e)
    }

    ScriptNode childNode  = parentNode.addChild(mountPoint,
                                                initParameters,
                                                childScript) {
        
      createNode(scriptConfig, sd, it)
    }

    addScriptNode(childNode)

    childNode.log.info("installScript(${args})")

    return childNode
  }

  /**
   * This is a helper method
   */
  private ScriptNode createNode(ScriptConfig scriptConfig,
                                ScriptDefinition sd,
                                script)
  {
    StateMachine stateMachine =  agentContext.mop.createStateMachine(script: script)

    def scriptProperties = [:]
    def scriptClosures = [:]

    def log = LoggerFactory.getLogger("org.linkedin.glu.agent.script.${sd.mountPoint}")

    def smClosures =
      ScriptWrapperImpl.getAvailableActionsClosures(stateMachine,
                                                    "${script.class.name} [${sd.mountPoint}]")

    scriptClosures.putAll(smClosures)

    def timers = [
        schedule: { args -> new FutureExecutionAdapter(futureExecution: getScript(sd.mountPoint).scheduleTimer(args)) },
        cancel: { args -> getScript(sd.mountPoint).cancelTimer(args) }
    ] as Timers

    def stateManager = [
        getState             : { getScript(sd.mountPoint).state },
        getFullState         :  { getScript(sd.mountPoint).fullState },
        forceChangeState     :  { currentState, error ->
          getScript(sd.mountPoint).forceChangeState(currentState, error)
        },
        waitForShutdownState : { getScript(sd.mountPoint).waitForShutdownInvocation() }
    ] as StateManager

    scriptProperties.putAll(
    [
      getMountPoint: { sd.mountPoint },
      getChildren: { locateChildrenNodes(sd.mountPoint) },
      getShell: { scriptConfig.shell },
      getRootShell: { agentContext.rootShell },
      getParent: { getScript(sd.parent) },
      getParams: { self.scriptDefinition.initParameters },
      getState: { stateManager.state },
      getStateManager: { stateManager },
      getLog: { log },
      getSelf: { getScript(mountPoint) },
      getTimers: { timers },
      getAgentZooKeeper: { agentContext.zooKeeper }
    ])

    script =
      agentContext.mop.wrapScript(script: script,
                                   scriptProperties: scriptProperties,
                                   scriptClosures: scriptClosures)

    return new ScriptNode(agentContext, sd, stateMachine, script, log)
  }
  
  /**
   * Returns the script mounted at the provided mount point (<code>null</code> if there is no
   * such script)
   */
  synchronized ScriptNode findScript(mountPoint)
  {
    mountPoint = MountPoint.create(mountPoint)
    return _scripts[mountPoint]
  }

  /**
   * Returns the script mounted at the provided mount point. The difference with find it that 
   * it throws an exception.
   */
  ScriptNode getScript(mountPoint)
  {
    def node = findScript(mountPoint)
    if(!node)
    {
      mountPoint = MountPoint.create(mountPoint)
      log.warn("Not found: ${mountPoint}")
      throw new NoSuchMountPointException(mountPoint)
    }
    return node
  }

  /**
   * @return the log for the given mountpoint
   */
  Logger findLog(mountPoint)
  {
    return findScript(mountPoint)?.log
  }

  /**
   * Clears the error of the script mounted at the provided mount point
   */
  void clearError(mountPoint)
  {
    def node = getScript(mountPoint)
    node.log.info("clearError")
    node.clearError()
  }

  /**
   * Uninstall the script
   */
  void uninstallScript(mountPoint, boolean force)
  {
    mountPoint = MountPoint.create(mountPoint)

    ScriptNode node = findScript(mountPoint)
    if(node)
    {
      synchronized(this)
      {
        def currentState = node.state.currentState
        if(currentState != StateMachine.NONE)
        {
          if(!force)
          {
            def msg = "cannot unsinstall script at ${mountPoint}: state is ${currentState}".toString()
            node.log.warn(msg)
            throw new ScriptIllegalStateException(msg)
          }
          else
          {
            node.log.warn("forcing uninstall of script in state ${currentState}")
          }
        }

        if(node.childrenMountPoints)
        {
          def msg = "cannot unsinstall script at ${mountPoint}: uninstall children first!".toString()
          node.log.warn(msg)
          throw new ScriptIllegalStateException(msg)
        }

        // we stop the execution first
        node.shutdown()

        // and leave some room for shutting down
        try
        {
          node.waitForShutdown(scriptGracePeriod1.durationInMilliseconds)
        }
        catch(TimeoutException e)
        {
          node.log.warn("cannot shutdown the script within ${scriptGracePeriod1}")
        }

        // we destroy the child
        findScript(node.parentMountPoint).removeChild(node)

        // we clean up the temp space
        GroovyLangUtils.noException {
          node.shell.rmdirs(node.shell.fileSystem.tmpRoot)
        }

        def scriptConfig = new ScriptConfig(shell: node.shell,
                                            agentContext: agentContext)

        // give a chance to the factory to "release" some resources
        node.scriptDefinition.scriptFactory.destroyScript(scriptConfig)

        removeScriptNode(mountPoint)

        node.log.info("uninstalled")
      }

      // we do this outside of the synchronized block in a separate thread
      Thread.startDaemon {
        try
        {
          node.waitForShutdown(scriptGracePeriod2.durationInMilliseconds)
        }
        catch(TimeoutException e)
        {
          node.log.warn("cannot shutdown the script within ${scriptGracePeriod2}... interrupting")
          node.interruptCurrentExecution()
        }
      }
    }
  }

  /**
   * {@inheridoc}
   */
  public getState(mountPoint)
  {
    def node = getScript(mountPoint)
    def state = node.state
    node.log.info("getState: ${state}")
    return state
  }

  def getFullState(mountPoint)
  {
    def node = getScript(mountPoint)
    def fullState = node.fullState
    node.log.info("getFullState: ${fullState}")
    return fullState
  }

  /**
   * Executes the action on the software that was installed on the given mount point. Note
   * that contrary to the similar action on the agent, this method is blocking.
   *
   * @param args.mountPoint same mount point provided during {@link #installScript(Object)}
   * @param args.action the lifecycle method you want to execute
   * @param args.actionArgs the arguments to provide the action
   * @return the value returned by the action
   */
  FutureExecution executeAction(args)
  {
    def node = getScript(args.mountPoint)
    try
    {
      def res = node.executeAction(args)
      node.log.info("executeAction(${args}): ${res.id}")
      return res
    }
    catch (Throwable th)
    {
      node.log.error("executeAction(${args})", th)
      throw th
    }
  }

  /**
   * {@inheritDoc}
   */
  boolean interruptAction(args)
  {
    def res = false
    def node = findScript(args.mountPoint)
    if(node)
    {
      res = node.interruptAction(args)
      node.log.info("interruptAction(${args})")
    }
    return res
  }

  /**
   * Waits for the script to be in the state
   *
   * @param args.mountPoint the mount point of the script you want to wait for
   * @param args.state the desired state to wait for
   * @param args.timeout if not <code>null</code>, the amount of time to wait maximum
   * @return <code>true</code> if the state was reached within the timeout, <code>false</code>)
   */
  boolean waitForState(args)
  {
    def node = getScript(args.mountPoint)
    def res = node.waitForState(args.state, args.timeout)
    node.log.info("waitForState(${args}): ${res}")
    return res
  }

  /**
   * {@inheritDoc}
   */
  def waitForAction(args)
  {
    def node = getScript(args.mountPoint)
    def res = node.waitForAction(args)
    node.log.info("waitForAction(${args}): ${res}")
    return res
  }

  /**
   * Executes the call on the software that was installed on the given mount point. Note
   * that this method is a blocking call and will wait for the result of the call.
   *
   * @param args.mountPoint same mount point provided during {@link #installScript(Object)}
   * @param args.call the call you want to execute
   * @param args.callArgs the arguments to provide the call
   * @return whatever value the call returns
   */
  def executeCall(args)
  {
    def node = getScript(args.mountPoint)
    try
    {
      def res = node.executeCall(args).get()
      node.log.info("executeCall(${args}): ${res instanceof InputStream ? 'InputStream': res}")
      return res
    }
    catch (Throwable th)
    {
      node.log.error("executeCall(${args})", th)
      throw th
    }
  }

  /**
   * Returns all the children nodes of mountPoint
   */
  synchronized private def locateChildrenNodes(mountPoint)
  {
    return locateChildrenNodes(getScript(mountPoint))
  }

  /**
   * Returns all the children nodes the node
   */
  synchronized private def locateChildrenNodes(ScriptNode scriptNode)
  {
    return scriptNode.childrenMountPoints.collect {
      getScript(it)
    }
  }

  void shutdown()
  {
    synchronized(this)
    {
      if(_shutdown)
        return

      _shutdown = true
    }
    
    scriptNodes.each { it.shutdown() }
  }

  void waitForShutdown()
  {
    if(!_shutdown)
      throw new IllegalStateException("call shutdown first")

    scriptNodes.each { it.waitForShutdown() }
  }

  void waitForShutdown(timeout)
  {
    if(!_shutdown)
      throw new IllegalStateException("call shutdown first")

    GroovyConcurrentUtils.waitForShutdownMultiple(agentContext.getClock(), timeout, scriptNodes)
  }

  synchronized Collection<ScriptNode> getScriptNodes()
  {
    _scripts.values()
  }

  synchronized void addScriptNode(ScriptNode node)
  {
    _scripts[node.mountPoint] = node
    // start node when not in shutdown mode
    if(!_shutdown)
      node.start()
  }

  synchronized void removeScriptNode(MountPoint mountPoint)
  {
    _scripts.remove(mountPoint)
  }
}
