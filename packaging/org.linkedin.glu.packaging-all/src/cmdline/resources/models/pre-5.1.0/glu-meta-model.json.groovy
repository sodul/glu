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

/**
 * The purpose of this model is to generate the same distributions that were bundled with glu
 * prior to 5.1.0
 */

metaModelVersion = '1.0.0'
gluVersion = '@glu.version@'

def fabric = 'glu-dev-1'

def zooKeeperVersion = '@zookeeper.version@'

def keys = [
  agentKeyStore: [
    uri: 'agent.keystore',
    checksum: 'JSHZAn5IQfBVp1sy0PgA36fT_fD',
    storePassword: 'nacEn92x8-1',
    keyPassword: 'nWVxpMg6Tkv'
  ],

  agentTrustStore: [
    uri: 'agent.truststore',
    checksum: 'CvFUauURMt-gxbOkkInZ4CIV50y',
    storePassword: 'nacEn92x8-1',
    keyPassword: 'nWVxpMg6Tkv'
  ],

  consoleKeyStore: [
    uri: 'console.keystore',
    checksum: 'wxiKSyNAHN2sOatUG2qqIpuVYxb',
    storePassword: 'nacEn92x8-1',
    keyPassword: 'nWVxpMg6Tkv'
  ],

  consoleTrustStore: [
    uri: 'console.truststore',
    checksum: 'qUFMIePiJhz8i7Ow9lZmN5pyZjl',
    storePassword: 'nacEn92x8-1',
  ],
]

fabrics[fabric] = [
  keys: keys,
  console: 'default',
  zooKeeperCluster: 'default'
]

agents << [
  host: 'localhost'
]

consoles << [
  host: 'localhost',
  plugins: [
    [
      fqcn: 'org.linkedin.glu.orchestration.engine.plugins.builtin.StreamFileContentPlugin'
    ]
  ],
  configTokens: [
    'console.bootstrap.fabrics': 'console.bootstrap.fabrics = []'
  ]
]

zooKeeperClusters << [
  name: 'default',
  zooKeepers: [
    [
      version: zooKeeperVersion,
      host: '127.0.0.1'
    ]
  ],
]
