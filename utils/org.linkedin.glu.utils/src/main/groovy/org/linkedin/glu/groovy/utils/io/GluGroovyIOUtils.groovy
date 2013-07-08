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

package org.linkedin.glu.groovy.utils.io

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.linkedin.groovy.util.ant.AntUtils
import org.linkedin.groovy.util.io.GroovyIOUtils
import org.linkedin.util.io.resource.Resource
import org.linkedin.groovy.util.io.fs.FileSystem

import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.SecretKeySpec

/**
 * @author yan@pongasoft.com */
public class GluGroovyIOUtils extends GroovyIOUtils
{
  static InputStream decryptStream(String password, InputStream inputStream)
  {
    new CipherInputStream(inputStream, computeCipher(password, Cipher.DECRYPT_MODE))
  }

  static def withStreamToDecrypt(String password, InputStream inputStream, Closure closure)
  {
    decryptStream(password, inputStream).withStream { closure(it) }
  }

  static OutputStream encryptStream(String password, OutputStream outputStream)
  {
    new CipherOutputStream(outputStream, computeCipher(password, Cipher.ENCRYPT_MODE))
  }

  static def withStreamToEncrypt(String password, OutputStream outputStream, Closure closure)
  {
    encryptStream(password, outputStream).withStream { closure(it) }
  }

  /**
   * Compiles a set of sources (using an optional classpath) and jar it into the destination jar
   *
   * @param fs where the jar file is relative to (as well as temp space)
   * @param sources list of sources (use {@link #toFile(Object)} to convert into a file)
   * @param jar destination jar file
   * @param classpath optional classpath (list/set of other jar files) (use
   *                  {@link #toFile(Object)} to convert into a file)
   * @return
   */
  static Resource compileAndJar(FileSystem fs, def sources, def jar, def classpath = null)
  {
    def cc = new CompilerConfiguration()
    cc.targetDirectory = fs.createTempDir().file
    if(classpath)
      cc.classpathList = classpath.collect { toFile(it).canonicalPath }
    CompilationUnit cu = new CompilationUnit(cc)
    sources.each {
      cu.addSource(toFile(it))
    }
    cu.compile()

    Resource jarFile = fs.toResource(jar)

    AntUtils.withBuilder { ant ->
      ant.jar(destfile: jarFile.file, basedir: cc.targetDirectory)
    }

    fs.rmdirs(cc.targetDirectory)

    return jarFile
  }


  private static Cipher computeCipher(String password, int mode)
  {
    SecretKeySpec key = new SecretKeySpec(password.getBytes("UTF-8"), "Blowfish")
    Cipher cipher = Cipher.getInstance("Blowfish")
    cipher.init(mode, key)
    return cipher
  }
}