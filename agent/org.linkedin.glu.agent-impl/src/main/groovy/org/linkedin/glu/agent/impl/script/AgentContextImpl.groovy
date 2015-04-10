/*
 * Copyright (c) 2012-2013 Yan Pujante
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

import org.linkedin.glu.agent.api.Shell
import org.linkedin.util.clock.Clock
import org.linkedin.util.clock.SystemClock
import org.linkedin.zookeeper.client.IZKClient

/**
 * @author yan@pongasoft.com */
public class AgentContextImpl implements AgentContext
{
  Clock clock = SystemClock.instance()
  Shell rootShell
  Shell shellForScripts
  Shell shellForCommands
  MOP mop
  ScriptLoader scriptLoader
  IZKClient zooKeeper
}