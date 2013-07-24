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

package test.setup

import junit.framework.AssertionFailedError
import org.linkedin.glu.groovy.utils.shell.Shell
import org.linkedin.glu.groovy.utils.shell.ShellImpl
import org.linkedin.groovy.util.io.GroovyIOUtils
import org.linkedin.util.io.resource.Resource
import org.pongasoft.glu.packaging.setup.PackagerContext
import org.pongasoft.glu.provisioner.core.metamodel.GluMetaModel
import org.pongasoft.glu.provisioner.core.metamodel.impl.builder.GluMetaModelBuilder

import java.nio.file.Files

/**
 * @author yan@pongasoft.com  */
public abstract class BasePackagerTest extends GroovyTestCase
{
  public static final Object DIRECTORY = new Object()
  public static final Object FILE = new Object()

  public static class BinaryResource
  {
    Resource resource
  }

  /**
   * Convenient call from subclasses
   */
  public static BinaryResource toBinaryResource(Resource resource)
  {
    new BinaryResource(resource: resource)
  }

  public Closure zipContent(Shell shell, def expectedResources, boolean bulkFailure = true)
  {
    def closure = { Resource r ->
      shell.withTempFile { Resource t ->
        shell.unzip(r, t)
        checkPackageContent(expectedResources, t, bulkFailure)
      }
    }

    return closure
  }

  public Closure tarContent(Shell shell, def expectedResources, boolean bulkFailure = true)
  {
    def closure = { Resource r ->
      shell.withTempFile { Resource t ->
        shell.untar(r, t)
        checkPackageContent(expectedResources, t, bulkFailure)
      }
    }

    return closure
  }

  public static final int CONFIG_TEMPLATES_COUNT = 22

  public static final String GLU_VERSION = 'g.v.0'
  public static final String ZOOKEEPER_VERSION = 'z.v.1'
  public static final String JETTY_VERSION = "j.v.2"

  public static final String DEFAULT_KEYS = """
  keys: [
    agentKeyStore: [
      uri: 'agent.keystore',
      checksum: 'JSHZAn5IQfBVp1sy0PgA36fT_fD',
      storePassword: 'nacEn92x8-1',
      keyPassword: 'nWVxpMg6Tkv'
    ],

    agentTrustStore: [
      uri: 'agent.truststore',
      checksum: 'CvFUauURMt-gxbOkkInZ4CIV50y',
      storePassword: 'nacEn92x8-1',
      keyPassword: 'nWVxpMg6Tkv'
    ],

    consoleKeyStore: [
      uri: 'console.keystore',
      checksum: 'wxiKSyNAHN2sOatUG2qqIpuVYxb',
      storePassword: 'nacEn92x8-1',
      keyPassword: 'nWVxpMg6Tkv'
    ],

    consoleTrustStore: [
      uri: 'console.truststore',
      checksum: 'qUFMIePiJhz8i7Ow9lZmN5pyZjl',
      storePassword: 'nacEn92x8-1',
    ],
  ]
"""

  Shell rootShell = ShellImpl.createRootShell()
  GluMetaModel testModel

  @Override
  protected void setUp() throws Exception
  {
    super.setUp()
    GluMetaModelBuilder builder = new GluMetaModelBuilder()
    builder.deserializeFromJsonGroovyDsl(rootShell.replaceTokens(testModelFile.text,
                                                                 [
                                                                   'glu.version': GLU_VERSION,
                                                                   'zookeeper.version': ZOOKEEPER_VERSION
                                                                 ]))
    testModel = builder.toGluMetaModel()
  }

  protected GluMetaModel toGluMetaModel(String gluMetaModelString)
  {
    GluMetaModelBuilder builder = new GluMetaModelBuilder()

    builder.deserializeFromJsonGroovyDsl(gluMetaModelString)

    return builder.toGluMetaModel()
  }

  protected File getTestModelFile()
  {
    new File('../org.linkedin.glu.packaging-all/src/cmdline/resources/models/tutorial/glu-meta-model.json.groovy').canonicalFile
  }

  protected File getConfigTemplatesRoot()
  {
    new File('../org.linkedin.glu.packaging-setup/src/cmdline/resources/config-templates').canonicalFile
  }

  protected Resource getConfigTemplatesRootResource()
  {
    rootShell.toResource(configTemplatesRoot)
  }

  protected File getKeysRootDir()
  {
    new File('../../dev-keys').canonicalFile
  }

  protected Resource getKeysRootResource()
  {
    rootShell.toResource(keysRootDir)
  }

  protected PackagerContext createPackagerContext(Shell shell)
  {
    new PackagerContext(shell: shell,
                        keysRoot: keysRootResource)
  }

  /**
   * Copy all the configs from the config root
   */
  protected Collection<Resource> copyConfigs(Resource toConfigRoot)
  {
    Resource dir = rootShell.cp(configTemplatesRootResource, toConfigRoot)
    int configFilesCount = 0
    rootShell.eachChildRecurse(dir) { Resource r ->
      if(!r.isDirectory())
        configFilesCount++
    }
    assertEquals(CONFIG_TEMPLATES_COUNT, configFilesCount)
    return [toConfigRoot]
  }

  protected void checkContent(String expectedContent, Shell shell, String templateName, def tokens)
  {
    def processed = shell.processTemplate("/templates/${templateName}", '/out', tokens)
    assertEquals(expectedContent.trim(), shell.cat(processed).trim())
  }


  protected void checkPackageContent(def expectedResources, Resource pkgRoot, boolean bulkFailure = true)
  {
    // GString -> String conversion for proper map key handling
    expectedResources = expectedResources.collectEntries {k,v -> [k.toString(), v]}

    List<AssertionFailedError> errors = []

    GroovyIOUtils.eachChildRecurse(pkgRoot.chroot('.')) { Resource r ->
      try
      {
        def expectedValue = expectedResources.remove(r.path)
        if(expectedValue == null)
          fail("unexpected resource ${r}")

        if(expectedValue.is(DIRECTORY))
          assertTrue("${r} is directory", r.isDirectory())
        else
        {
          if(expectedValue instanceof BinaryResource)
            assertEquals("binary content differ for ${r}",
                         rootShell.sha1(expectedValue.resource), rootShell.sha1(r))
          else
            if(expectedValue instanceof Closure)
            {
              expectedValue(r)
            }
            else
            {
              if(expectedValue.is(FILE))
                assertTrue("${r} is file", !r.isDirectory())
              else
                assertEquals("mismatch content for ${r}", expectedValue, r.file.text)
            }

          if(r.path.endsWith('.sh'))
            assertTrue("${r} is executable", Files.isExecutable(r.file.toPath()))
        }
      }
      catch(AssertionFailedError ex)
      {
        if(bulkFailure)
          errors << ex
        else
          throw ex
      }
    }

    try
    {
      assertTrue("${expectedResources.keySet()} is not empty", expectedResources.isEmpty())
    }
    catch(AssertionFailedError ex)
    {
      if(bulkFailure)
        errors << ex
      else
        throw ex
    }

    if(errors.size() == 1)
      throw errors[0]

    if(errors.size() > 1)
    {
      def ex = new AssertionFailedError("${errors.size()} detected")
      errors.each { ex.addSuppressed(it) }
      throw ex
    }
  }

}