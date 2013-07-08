/*
 * Copyright (c) 2010-2010 LinkedIn, Inc
 * Portions Copyright (c) 2013 Yan Pujante
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

package org.linkedin.glu.agent.rest.resources

import org.linkedin.util.lifecycle.Configurable
import org.restlet.data.Status
import org.restlet.representation.Representation
import org.restlet.resource.Put

/**
 * @author ypujante@linkedin.com */
class AgentConfigResource extends BaseResource
{
  public static final String MODULE = AgentConfigResource.class.getName();
  public static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MODULE);

  Configurable configurable
  def codec

  @Override
  protected void doInit() throws org.restlet.resource.ResourceException
  {
    super.doInit()
    configurable = context.attributes['configurable']
    codec = context.attributes['codec']
  }

  /**
   * PUT: configuration (key/value pairs)
   */
  @Put
  public Representation configure(Representation representation)
  {
    noException {
      Map map = toArgs(representation)

      if(validate(map))
      {
        configurable.configure(map)
        return null
      }
      else
      {
        log.warn("Received data with wrong checksum <${map.checksum}>")
        response.setStatus(Status.CLIENT_ERROR_FORBIDDEN)
        return null
      }
    }
  }

  private boolean validate(Map map)
  {
    // first we sort the map by keys
    map = new TreeMap(map)

    def expectedChecksum = map.remove('checksum')

    StringBuilder sb = new StringBuilder()

    map.each { k, v ->
      sb.append(k).append('\n')
      sb.append(v).append('\n')
    }

    def computedChecksum = codec.encode(sb.toString().getBytes('UTF-8'))

    return computedChecksum == expectedChecksum
  }
}
