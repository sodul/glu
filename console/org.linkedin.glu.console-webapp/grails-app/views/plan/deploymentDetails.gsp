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

<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title>Deployment ${deployment.description.encodeAsHTML()}</title>
  <meta name="layout" content="main"/>
  <link rel="stylesheet" href="${resource(dir:'css',file:'status-colors.css')}"/>
  <style type="text/css">
    div.stackTraceBody {
      display: none;
    }
    span.stackTraceExceptionClass {
      display: none;
    }
    div.stackTraceMessage {
      color: #880000;
      font-weight: bold;
      font-family: monospace;
      overflow-y: auto;
    }
    .progress {
      width: 150px;
      border: solid black 1px;
      display: inline-block;
    }
    a.step-link {
      color: black;
    }
    a.step-link:hover {
      color: #0069D6;
    }
  </style>
  <g:javascript>
function shouldRefresh()
{
  var ar = document.getElementById('autoRefresh').checked;
  if(ar)
  {
    var progressValue = document.getElementById('progress').innerHTML;
    return progressValue != '100';
  }
  else
    return false;
}
function showErrorsOnly()
{
  var show = document.getElementById('showErrorsOnly').checked;
  toggleClass('#deployment-details .COMPLETED', show, 'hidden');
}
function autoRefresh()
{
  if(shouldRefresh())
  {
    setTimeout('refresh()', 1000);
    show('#autoRefreshSpinner');
  }
  else
  {
    hide('#autoRefreshSpinner');
  }
}
function onRefreshComplete()
{
  if(!shouldRefresh())
  {
    document.getElementById('showErrorsOnly').checked = false;
  }
  showErrorsOnly();
  autoRefresh()
}
function refresh()
{
  if(shouldRefresh())
  {
    ${g.remoteFunction(controller: 'plan', action: 'renderDeploymentDetails', id: deployment.id, update:[success: 'asyncDetails'], onComplete: 'onRefreshComplete();')}
  }
  else
  {
    hide('#autoRefreshSpinner');
  }
}
  </g:javascript>
</head>
<body onload="showErrorsOnly();autoRefresh();">
<ul class="tabs">
  <li><g:link action="deployments">Recent</g:link></li>
  <li class="active"><a href="#">Recent [${deployment.description.encodeAsHTML()}]</a></li>
  <li><g:link action="archived">Archived</g:link></li>
</ul>

<h3>
  Show Errors Only: <cl:checkBoxInitFromParams name="showErrorsOnly" id="showErrorsOnly" onclick="showErrorsOnly();"/> |
  Auto Refresh: <cl:checkBoxInitFromParams name="autoRefresh" id="autoRefresh" onclick="autoRefresh();"/>
  <img src="${resource(dir:'images',file:'spinner.gif')}" alt="Spinner" id="autoRefreshSpinner"/>
</h3>

<div id="asyncDetails">
  <g:render template="deploymentDetails" model="[deployment: deployment]"/>
</div>

</body>
</html>
