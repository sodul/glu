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

package org.linkedin.glu.agent.impl.command

import org.linkedin.glu.agent.impl.script.AbstractScriptFactoryFactory
import org.linkedin.glu.agent.impl.script.ScriptFactory
import org.linkedin.util.annotations.Initializable
import org.linkedin.glu.commands.impl.CommandExecutionIOStorage

/**
 * @author yan@pongasoft.com */
public class CommandGluScriptFactoryFactory extends AbstractScriptFactoryFactory
{
  @Initializable(required = true)
  CommandExecutionIOStorage ioStorage

  @Override
  protected ScriptFactory doCreateScriptFactory(def args)
  {
    if(args.scriptFactory)
    {
      if(args.scriptFactory instanceof ScriptFactory)
        return args.scriptFactory
      else
        args = args.scriptFactory
    }

    if(args['class'] == "CommandGluScriptFactory")
      return new CommandGluScriptFactory(ioStorage: ioStorage)

    return null
  }
}