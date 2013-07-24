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

package org.pongasoft.glu.provisioner.core.metamodel.impl

import org.pongasoft.glu.provisioner.core.metamodel.ZooKeeperClusterMetaModel
import org.pongasoft.glu.provisioner.core.metamodel.ZooKeeperMetaModel

/**
 * @author yan@pongasoft.com  */
public class ZooKeeperMetaModelImpl extends ServerMetaModelImpl implements ZooKeeperMetaModel
{
  ZooKeeperClusterMetaModel zooKeeperCluster

  @Override
  int getDefaultPort()
  {
    return DEFAULT_CLIENT_PORT;
  }

  @Override
  int getClientPort()
  {
    getMainPort()
  }

  @Override
  int getQuorumPort()
  {
    getPort('quorumPort', DEFAULT_QUORUM_PORT)
  }

  @Override
  int getLeaderElectionPort()
  {
    getPort('leaderElectionPort', DEFAULT_LEADER_ELECTION_PORT)
  }

  @Override
  int getServerIdx()
  {
    zooKeeperCluster.zooKeepers.findIndexOf { it.is(this) } + 1
  }

  @Override
  Object toExternalRepresentation()
  {
    def ext = super.toExternalRepresentation()

    ext.ports = [
      leaderElectionPort: getLeaderElectionPort(),
      quorumPort: getQuorumPort()
    ]

    return ext
  }

}