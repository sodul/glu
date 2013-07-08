%{--
  - Copyright (c) 2010-2010 LinkedIn, Inc
  - Portions Copyright (c) 2011-2013 Yan Pujante
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

<%@ page import="org.linkedin.glu.agent.api.Agent" contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title>Agent: ${params.id}</title>
  <meta name="layout" content="main"/>
  <style type="text/css">
    table td {
      padding: 0.2em;
    }
  </style>
</head>
<body>
<g:if test="${params.pid}">
  <ul class="nav nav-tabs">
    <li><g:link controller="agents" action="list">List</g:link></li>
    <cl:whenFeatureEnabled feature="commands"><li><g:link controller="commands" action="list">All Commands</g:link></li></cl:whenFeatureEnabled>
    <li><g:link action="view" id="${params.id}">agent [${params.id}]</g:link></li>
    <li><g:link action="plans" id="${params.id}">Plans</g:link></li>
    <cl:whenFeatureEnabled feature="commands"><li><g:link action="commands" id="${params.id}">Commands</g:link></li></cl:whenFeatureEnabled>
    <li><g:link action="ps" id="${params.id}">All Processes</g:link></li>
    <li class="active"><a href="#">process[${params.pid}]</a></li>
  </ul>
  <g:if test="${ps[params.pid]}">
    <g:form action="kill" id="${params.id}" params="[pid: params.pid]">
      <g:textField name="signal" value="3"/> <g:submitButton class="btn btn-primary" name="kill" value="Send Signal"/>
    </g:form>
    <cl:mapToUL map="${ps[params.pid]}" specialKeys="${['args']}" var="specialEntry">
      <li>
        ${specialEntry.key}
        <ul>
        <g:each in="${specialEntry.value}" var="arg" status="i">
          <li>argv[${i}]: ${arg.encodeAsHTML()}</li>
        </g:each>
      </ul>
      </li>
    </cl:mapToUL>
  </g:if>
  <g:else>
    No such process.
  </g:else>
</g:if>
<g:else>
  <ul class="nav nav-tabs">
    <li><g:link controller="agents" action="list">List</g:link></li>
    <cl:whenFeatureEnabled feature="commands"><li><g:link controller="commands" action="list">All Commands</g:link></li></cl:whenFeatureEnabled>
    <li><g:link action="view" id="${params.id}">agent [${params.id}]</g:link></li>
    <li><g:link action="plans" id="${params.id}">Plans</g:link></li>
    <cl:whenFeatureEnabled feature="commands"><li><g:link action="commands" id="${params.id}">Commands</g:link></li></cl:whenFeatureEnabled>
    <li class="active"><a href="#">All Processes</a></li>
  </ul>
  <table class="table table-bordered tight-table zebra-striped">
    <tr>
      <th>PID</th>
      <th>COMMAND</th>
      <th>%CPU</th>
      <th>org.linkedin.app.name</th>
    </tr>
  <g:each in="${ps.keySet().collect { it.toInteger() }.sort()}" var="pid">
    <g:set var="process" value="${ps[pid.toString()]}"/>
    <g:if test="${process.exe?.Name}">
      <tr>
        <td><g:link action="ps" id="${params.id}" params="[pid: pid]">${pid}</g:link></td>
        <td>${process.exe.Name.split('/')[-1]}</td>
        <td>${process.cpu?.Percent}</td>
        <td>
          <g:set var="applicationArg" value="${process.args?.find { it.startsWith('-Dorg.linkedin.app.name=') } }"/>
          <g:if test="${applicationArg}">
            ${applicationArg - '-Dorg.linkedin.app.name='}
          </g:if>
        </td>
      </tr>
    </g:if>
  </g:each>
  </table>
</g:else>
</body>
</html>