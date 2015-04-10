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

<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <meta name="layout" content="main"/>
  <link rel="stylesheet" href="${resource(dir:'css',file:'fabric-select.css')}"/>
  <title>Select your fabric</title>
</head>
<body>
<div class="body">
  <h2>Select your fabric</h2>
  <table class="table table-bordered noFullWidth">
    <tr>
      <th>Fabric</th>
    </tr>
    <g:each in="${fabrics}" var="fabric">
      <tr>
        <td class="${request.fabric?.name == fabric.name ? 'current' : 'not-current'}"><cl:link controller="dashboard" action="redelta" params="[fabric: fabric.name]">${fabric.name.encodeAsHTML()}</cl:link> <span style="background-color: ${fabric.color}">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
</td>
      </tr>
    </g:each>
  </table>
</div>
</body>
</html>