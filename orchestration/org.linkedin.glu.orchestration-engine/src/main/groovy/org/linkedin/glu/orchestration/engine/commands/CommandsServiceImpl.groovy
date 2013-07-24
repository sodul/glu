/*
 * Copyright (c) 2012 Yan Pujante
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

package org.linkedin.glu.orchestration.engine.commands

import org.apache.commons.io.input.TeeInputStream

import org.linkedin.glu.orchestration.engine.agents.AgentsService
import org.linkedin.glu.orchestration.engine.authorization.AuthorizationService
import org.linkedin.glu.orchestration.engine.commands.DbCommandExecution.CommandType
import org.linkedin.glu.orchestration.engine.fabric.Fabric
import org.linkedin.glu.utils.io.DemultiplexedOutputStream
import org.linkedin.glu.utils.io.NullOutputStream
import org.linkedin.util.annotations.Initializable
import org.linkedin.util.clock.Clock
import org.linkedin.util.clock.SystemClock
import org.linkedin.util.clock.Timespan

import org.linkedin.util.lang.MemorySize
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeoutException

import org.linkedin.glu.groovy.utils.collections.GluGroovyCollectionUtils
import org.linkedin.glu.groovy.utils.io.StreamType
import org.linkedin.glu.commands.impl.CommandExecution
import org.linkedin.glu.commands.impl.CommandExecutionIOStorage
import org.linkedin.glu.commands.impl.CommandStreamStorage
import org.linkedin.glu.commands.impl.GluCommandFactory
import org.linkedin.groovy.util.lang.GroovyLangUtils
import org.linkedin.glu.utils.concurrent.Submitter
import org.linkedin.glu.groovy.utils.concurrent.FutureTaskExecution
import org.linkedin.glu.groovy.utils.plugins.PluginService
import org.linkedin.glu.groovy.utils.plugins.NoPluginsPluginService
import org.linkedin.glu.orchestration.engine.agents.NoSuchAgentException

/**
 * @author yan@pongasoft.com  */
public class CommandsServiceImpl implements CommandsService
{
  public static final String MODULE = CommandsServiceImpl.class.getName();
  public static final Logger log = LoggerFactory.getLogger(MODULE);

  // will be dependency injected
  @Initializable(required = true)
  AgentsService agentsService

  @Initializable
  Clock clock = SystemClock.INSTANCE

  @Initializable
  Submitter submitter = FutureTaskExecution.DEFAULT_SUBMITTER

  @Initializable(required = true)
  CommandExecutionStorage commandExecutionStorage

  @Initializable(required = true)
  CommandExecutionIOStorage commandExecutionIOStorage

  @Initializable(required = true)
  PluginService pluginService = NoPluginsPluginService.INSTANCE

  /**
   * the timeout for waiting for command to complete */
  @Initializable(required = false)
  Timespan timeout = Timespan.parse('10s')

  /**
   * This is somewhat hacky but cannot do it in spring due to circular reference...
   */
  void setCommandExecutionIOStorage(CommandExecutionIOStorage storage)
  {
    storage.gluCommandFactory = createGluCommand as GluCommandFactory
    commandExecutionIOStorage = storage
  }

  @Initializable
  AuthorizationService authorizationService

  /**
   * using 255 by default because that is what a <code>String</code> in GORM can hold
   */
  @Initializable(required = true)
  MemorySize commandExecutionFirstBytesSize = MemorySize.parse('255')

  /**
   * This timeout represents how long to we are willing to wait for the command to complete before
   * returning. The command will still complete in the background... this allows "fast" commands
   * to be handled more naturally in the UI.
   */
  @Initializable
  Timespan defaultSynchronousWaitTimeout = Timespan.parse('1s')

  /**
   * This timeout represents how long to we are willing to wait for the command to wait for the
   * interrupt to propagate before we interrupt it on this side
   */
  @Initializable
  Timespan defaultInterruptTimeout = Timespan.parse('5s')

  /**
   * The commands that are currently executing  */
  private final Map<String, CommandExecution<DbCommandExecution>> _currentCommandExecutions = [:]

  @Override
  Map<String, DbCommandExecution> findCurrentCommandExecutions(Collection<String> commandIds = null)
  {
    synchronized(_currentCommandExecutions)
    {
      def map =
        commandIds == null ?
          _currentCommandExecutions :
          GluGroovyCollectionUtils.subMap(_currentCommandExecutions, commandIds)

      GluGroovyCollectionUtils.collectKey(map, [:]) { k, v -> v.command.copy() }
    }
  }

  @Override
  DbCommandExecution findCommandExecution(Fabric fabric, String commandId)
  {
    DbCommandExecution commandExecution

    synchronized(_currentCommandExecutions)
    {
      commandExecution = _currentCommandExecutions[commandId]?.command
      if(commandExecution != null)
      {
        if(commandExecution.fabric != fabric.name)
          return null
        else
          commandExecution = commandExecution.copy()
      }
    }

    if(!commandExecution)
      commandExecution = commandExecutionStorage.findCommandExecution(fabric.name, commandId)

    return commandExecution
  }

  @Override
  Map findCommandExecutions(Fabric fabric, String agentName, def params)
  {
    def map = commandExecutionStorage.findCommandExecutions(fabric.name, agentName, params)

    synchronized(_currentCommandExecutions)
    {
      // copy the currently executing commands
      map.currentCommandExecutions = findCurrentCommandExecutions().values()

      // replace db by current running
      map.commandExecutions = map.commandExecutions?.collect { DbCommandExecution ce ->
        def current = _currentCommandExecutions[ce.commandId]?.command
        if(current)
          return current.copy()
        else
          return ce
      }
    }

    return map
  }

  @Override
  String executeShellCommand(Fabric fabric, String agentName, args)
  {
    CommandExecution command = doExecuteShellCommand(fabric, agentName, args) { res ->
      // we do not care about the stream in this version, it will be properly stored in storage
      NullOutputStream.INSTANCE << res.stream
    }

    if(defaultSynchronousWaitTimeout)
    {
      try
      {
        // we are willing to wait a little bit for the command to complete before returning
        command.getExitValue(defaultSynchronousWaitTimeout)
      }
      catch (TimeoutException e)
      {
        // it is ok... we did not get any result during this amount of time
      }
      catch(Throwable th)
      {
        log.warn("command execution generated an unknown exception [ignored]", th)
      }
    }

    return command.id
  }

  @Override
  boolean interruptCommand(Fabric fabric, String agentName, String commandId)
  {
    // first we interrupt the command "remotely"
    def res = agentsService.interruptCommand(fabric, agentName, [id: commandId])

    CommandExecution commandExecution

    synchronized(_currentCommandExecutions)
    {
      commandExecution = _currentCommandExecutions[commandId]
    }

    if(commandExecution)
    {
      try
      {
        // we wait for the interrupt to propagate back
        commandExecution.waitForCompletion(defaultInterruptTimeout)
      }
      catch(TimeoutException e)
      {
        // not completed yet... forcing interrupting on this side
        res &= commandExecution.interruptExecution()
      }
    }

    return res
  }

  /**
   * Executes the command asynchronously
   */
  CommandExecution doExecuteShellCommand(Fabric fabric,
                                         String agentName,
                                         args,
                                         Closure onResultStreamAvailable)
  {
    // sanity check before executing the command
    if(!agentsService.getAgentInfo(fabric, agentName))
      throw new NoSuchAgentException(agentName)

    args = GluGroovyCollectionUtils.subMap(args, ['command', 'redirectStderr', 'stdin'])

    def pluginArgs = [
      fabric: fabric,
      agent: agentName,
      args: args,
      onResultStreamAvailable: onResultStreamAvailable
    ]

    pluginService.executeMethod(CommandsService,
                                "pre_executeCommand",
                                pluginArgs)

    (args, onResultStreamAvailable) = [[*:pluginArgs.args], pluginArgs.onResultStreamAvailable]


    // it is a shell command
    args.type = 'shell'

    // set the various parameters for the call
    args.fabric = fabric.name
    args.agent = agentName
    args.username = authorizationService.executingPrincipal

    // prepare the storage for the command execution (this should make a copy of stdin if
    // there is one)
    CommandExecution command = commandExecutionIOStorage.createStorageForCommandExecution(args)

    // define what will do asynchronously
    def asyncProcessing = { CommandStreamStorage storage ->

      try
      {
        def agentArgs = [*:command.args]

        // we execute the command on the proper agent (this is an asynchronous call which return
        // right away)
        storage.withOrWithoutStorageInput(StreamType.stdin) { stdin ->
          if(stdin)
            agentArgs.stdin = stdin

          agentsService.executeShellCommand(fabric, agentName, agentArgs)
        }

        boolean completed = false

        // this will block until the command completes but will loop "regularly"
        while(!completed)
        {
          completed = agentsService.waitForCommandNoTimeOutException(fabric,
                                                                     agentName,
                                                                     [
                                                                       id: command.id,
                                                                       username: args.username,
                                                                       timeout: timeout
                                                                     ])
        }


        def streamResultArgs = [
          id: command.id,
          exitErrorStream: true,
          exitValueStream: true,
          stdoutStream: true,
          username: args.username
        ]

        if(!command.redirectStderr)
          streamResultArgs.stderrStream = true

        // this is a blocking call
        agentsService.streamCommandResults(fabric, agentName, streamResultArgs) { res ->

          def streams = [:]

          // stdout
          new CommandExecutionStream(streamType: StreamType.stdout,
                                     commandExecutionFirstBytesSize: commandExecutionFirstBytesSize,
                                     captureStream: true,
                                     storage: storage,
                                     streams: streams).capture { stdout ->

            // stderr
            new CommandExecutionStream(streamType: StreamType.stderr,
                                       commandExecutionFirstBytesSize: commandExecutionFirstBytesSize,
                                       captureStream: !command.redirectStderr,
                                       storage: storage,
                                       streams: streams).capture { stderr ->

              // exitValue
              ByteArrayOutputStream exitValueStream = new ByteArrayOutputStream()
              streams[StreamType.exitValue.multiplexName] = exitValueStream

              ByteArrayOutputStream exitErrorStream = new ByteArrayOutputStream()
              streams[StreamType.exitError.multiplexName] = exitErrorStream

              // this will demultiplex the result
              DemultiplexedOutputStream dos = new DemultiplexedOutputStream(streams)

              try
              {
                dos.withStream { OutputStream os ->
                  onResultStreamAvailable(id: command.id, stream: new TeeInputStream(res.stream, os))
                }
              }
              catch(Throwable th)
              {
                long completionTime = clock.currentTimeMillis()

                GroovyLangUtils.noException {
                  commandExecutionStorage.endExecution(command.id,
                                                       completionTime,
                                                       stdout.bytes,
                                                       stdout.totalNumberOfBytes,
                                                       stderr.bytes,
                                                       stderr.totalNumberOfBytes,
                                                       th)
                }

                return [completionTime: completionTime, exception: th]
              }

              long completionTime = clock.currentTimeMillis()

              String exitError = toString(exitErrorStream)

              if(exitError)
              {
                GroovyLangUtils.noException {
                  commandExecutionStorage.endExecution(command.id,
                                                       completionTime,
                                                       stdout.bytes,
                                                       stdout.totalNumberOfBytes,
                                                       stderr.bytes,
                                                       stderr.totalNumberOfBytes,
                                                       exitError,
                                                       true)
                }

                return [completionTime: completionTime, exception: exitError]
              }

              // we now update the storage with the various results
              def exitValue = commandExecutionStorage.endExecution(command.id,
                                                                   completionTime,
                                                                   stdout.bytes,
                                                                   stdout.totalNumberOfBytes,
                                                                   stderr.bytes,
                                                                   stderr.totalNumberOfBytes,
                                                                   toString(exitValueStream),
                                                                   false).exitValue

              return [exitValue: exitValue, completionTime: completionTime]
            }
          }
        }
      }
      catch(Throwable th)
      {
        long completionTime = clock.currentTimeMillis()

        GroovyLangUtils.noException {
          commandExecutionStorage.endExecution(command.id,
                                               completionTime,
                                               null,
                                               null,
                                               null,
                                               null,
                                               th)
        }

        return [completionTime: completionTime, exception: th]
      }
    }

    // what to do when the command ends
    def endCommandExecution = {
      synchronized(_currentCommandExecutions)
      {
        command.command.isExecuting = false
        _currentCommandExecutions.remove(command.id)
      }

      pluginService.executeMethod(CommandsService,
                                  "post_executeCommand",
                                  [serviceResult: command])
    }

    synchronized(_currentCommandExecutions)
    {
      command.command.isExecuting = true
      _currentCommandExecutions[command.id] = command
      try
      {
        def future = command.asyncCaptureIO(submitter, asyncProcessing)
        future.onCompletionCallback = endCommandExecution
      }
      catch(Throwable th)
      {
        // this is to avoid the case when the command is added to the map but we cannot
        // run the asynchronous execution which will remove it from the map when complete
        endCommandExecution()
        throw th
      }
    }

    return command
  }

  /**
   * Factory to create a command (first try to read it from the db or store it first in the db)
   */
  def createGluCommand = { CommandExecution command ->

    final String commandId = command.id

    def ce = commandExecutionStorage.findCommandExecution(command.args.fabric, commandId)

    if(!ce)
    {
      ByteArrayOutputStream stdinFirstBytes = null

      Long stdinSize =
        command.storage.withStorageInputWithSize(StreamType.stdin,
                                                 [ len: commandExecutionFirstBytesSize.sizeInBytes ]) { m ->
          stdinFirstBytes = new ByteArrayOutputStream()
          stdinFirstBytes << m.stream
          return m.size
        } as Long

      ce = commandExecutionStorage.startExecution(command.args.fabric,
                                                  command.args.agent,
                                                  command.args.username,
                                                  command.args.command,
                                                  command.redirectStderr,
                                                  stdinFirstBytes?.toByteArray(),
                                                  stdinSize,
                                                  commandId,
                                                  CommandType.SHELL,
                                                  command.startTime)
    }

    return ce
  }

  @Override
  def withCommandExecutionAndWithOrWithoutStreams(Fabric fabric,
                                                  String commandId,
                                                  def args,
                                                  Closure closure)
  {
    def commandExecution = findCommandExecution(fabric, commandId)

    if(commandExecution?.fabric != fabric.name)
      throw new NoSuchCommandExecutionException(commandId)

    commandExecutionIOStorage.withOrWithoutCommandExecutionAndStreams(commandId, args) { m ->
      if(!m)
        throw new NoSuchCommandExecutionException(commandId)
      closure([commandExecution: commandExecution, stream: m.stream])
    }
  }

  private String toString(ByteArrayOutputStream stream)
  {
    if(stream)
      new String(stream.toByteArray(), "UTF-8")
    else
      null
  }
}