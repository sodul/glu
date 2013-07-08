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

package org.linkedin.glu.console.controllers

import org.linkedin.glu.console.domain.User
import org.apache.shiro.SecurityUtils
import org.linkedin.glu.grails.utils.ConsoleConfig

/**
 * Home controller
 *
 * @author ypujante@linkedin.com */
class HomeController extends ControllerBase
{
  ConsoleConfig consoleConfig

  /**
   * Handle "/" URL
   */
  def slash = {
    redirect(controller: 'dashboard', action: 'delta')
  }

  def index = {
    def subject = SecurityUtils.getSubject()
    def user = User.findByUsername(subject.principal)
    return [roles: user.roles]
  }
}
