/*
 * Copyright (c) 2010-2010 LinkedIn, Inc
 * Portions Copyright (c) 2014 Yan Pujante
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

package org.linkedin.glu.provisioner.plan.api;

import java.util.Collection;

/**
 * @author ypujante@linkedin.com
 */
public class SequentialStepBuilder<T> extends CompositeStepBuilder<T>
{
  /**
   * Constructor
   */
  public SequentialStepBuilder(IPlanBuilder.Config config)
  {
    super(config);
  }

  @Override
  protected IStep<T> createStep(Collection<IStep<T>> steps)
  {
    return new SequentialStep<T>(getId(), getMetadata(), steps);
  }
}
