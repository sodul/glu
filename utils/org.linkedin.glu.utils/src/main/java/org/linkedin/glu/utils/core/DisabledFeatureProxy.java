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

package org.linkedin.glu.utils.core;

import org.linkedin.glu.utils.exceptions.DisabledFeatureException;
import org.linkedin.glu.utils.reflect.ObjectAwareInvocationHandler;
import org.linkedin.util.lang.LangUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author yan@pongasoft.com
 */
public class DisabledFeatureProxy extends ObjectAwareInvocationHandler
{
  private final String _feature;

  /**
   * Constructor
   */
  public DisabledFeatureProxy(String feature)
  {
    _feature = feature;
  }

  @Override
  public Object doInvoke(Object o, Method method, Object[] objects) throws Throwable
  {
    throw new DisabledFeatureException(_feature);
  }
}
