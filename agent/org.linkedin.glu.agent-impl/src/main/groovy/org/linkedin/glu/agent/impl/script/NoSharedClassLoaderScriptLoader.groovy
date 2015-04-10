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
package org.linkedin.glu.agent.impl.script

import org.linkedin.util.reflect.ReflectUtils

/**
 * @author yan@pongasoft.com  */
public class NoSharedClassLoaderScriptLoader implements ScriptLoader<NoSharedClassLoaderLoadedScript>
{
  private static class NoSharedClassLoaderLoadedScript extends LoadedScript
  {
    GroovyClassLoader scriptClassLoader
  }

  @Override
  NoSharedClassLoaderLoadedScript loadScript(File scriptFile)
  {
    def classLoader = new GroovyClassLoader(getClass().classLoader)

    Class scriptClass = classLoader.parseClass(scriptFile)

    new NoSharedClassLoaderLoadedScript(script: scriptClass.newInstance(),
                                        scriptClassLoader: classLoader)
  }

  @Override
  NoSharedClassLoaderLoadedScript loadScript(String className, Collection<File> classPath)
  {
    def classLoader = new GroovyClassLoader(getClass().classLoader)

    classPath?.each { classLoader.addURL(it.toURI().toURL())}

    new NoSharedClassLoaderLoadedScript(script: ReflectUtils.forName(className, classLoader).newInstance(),
                                        scriptClassLoader: classLoader)
  }

  @Override
  void unloadScript(NoSharedClassLoaderLoadedScript loadedScript)
  {
    loadedScript?.scriptClassLoader?.clearCache()
    loadedScript?.scriptClassLoader = null
    loadedScript?.script = null
  }
}