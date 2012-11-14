%{--
  - Copyright (c) 2010-2010 LinkedIn, Inc
  - Portions Copyright (c) 2011 Yan Pujante
  -
  - Licensed under the Apache License, Version 2.0 (the "License"); you may not
  - use this file except in compliance with the License. You may obtain a copy of
  - the License at
  -
  - http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  - WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  - License for the specific language governing permissions and limitations under
  - the License.
  --}%
<g:each in="['warning', 'success', 'error', 'info']" var="ft">
  <g:if test="${flash[ft]}">
    <div id="flash" class="alert-message ${ft} fade in" data-alert="alert" >
      <a class="close" href="#">&times;</a>
      <g:if test="${flash[ft] instanceof Collection}">
        <ul>
          <g:each in="${flash[ft]}" var="msg">
            <li>${msg.encodeAsHTML()}</li>
          </g:each>
        </ul>
      </g:if>
      <g:else>
        <p>${flash[ft].encodeAsHTML()}</p>
      </g:else>
      <g:if test="${flash.exception}">
        <div class="alert-actions">
          <a href="#" class="btn" onclick="toggleShowHide('#flash-exception');return false;">View Full Stack Trace</a>
          <div id="flash-exception" class="hidden">
            <cl:renderJsonException exception="${flash.exception}"/>
          </div>
        </div>
      </g:if>
    </div>
  </g:if>
</g:each>
<cl:clearFlash/>
