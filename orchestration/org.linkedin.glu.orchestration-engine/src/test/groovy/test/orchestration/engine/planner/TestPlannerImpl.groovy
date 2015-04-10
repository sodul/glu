/*
 * Copyright (c) 2011-2014 Yan Pujante
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

package test.orchestration.engine.planner

import org.linkedin.glu.orchestration.engine.planner.impl.PlannerImpl
import org.linkedin.glu.provisioner.plan.api.IPlanBuilder
import org.linkedin.glu.provisioner.plan.api.IStep.Type
import org.linkedin.glu.provisioner.core.model.SystemModel
import org.linkedin.glu.provisioner.core.model.SystemEntry
import org.linkedin.glu.provisioner.plan.api.Plan
import org.linkedin.glu.orchestration.engine.action.descriptor.ActionDescriptor
import org.linkedin.glu.orchestration.engine.delta.SystemModelDelta
import org.linkedin.glu.orchestration.engine.delta.DeltaMgr
import org.linkedin.glu.orchestration.engine.delta.impl.DeltaMgrImpl
import org.linkedin.groovy.util.json.JsonUtils
import org.linkedin.glu.orchestration.engine.planner.impl.SingleDeltaTransitionPlan
import org.linkedin.glu.orchestration.engine.planner.impl.Transition
import org.linkedin.glu.orchestration.engine.action.descriptor.AgentURIProvider
import org.linkedin.glu.orchestration.engine.agents.NoSuchAgentException
import org.linkedin.glu.orchestration.engine.action.descriptor.ActionDescriptorAdjuster

/**
 * @author yan@pongasoft.com */
public class TestPlannerImpl extends GroovyTestCase
{
  // setting a noop action descriptor adjuster to not have to deal with namesdelt
  ActionDescriptorAdjuster actionDescriptorAdjuster = { smd, ad ->
    return ad
  } as ActionDescriptorAdjuster
  PlannerImpl planner = new PlannerImpl(actionDescriptorAdjuster: actionDescriptorAdjuster)
  DeltaMgr deltaMgr = new DeltaMgrImpl()

  public void testDeploymentPlanNoDelta()
  {
    assertNull(planner.computeDeploymentPlan(Type.SEQUENTIAL, null))

    [Type.SEQUENTIAL, Type.PARALLEL].each { Type type ->
      Plan<ActionDescriptor> p = plan(type,
                                      delta(m([agent: 'a1', mountPoint: 'm1', script: 's1']),
                                            m([agent: 'a1', mountPoint: 'm1', script: 's1'])))

      assertEquals(type, p.step.type)
      assertEquals(0, p.leafStepsCount)
    }
  }

  /**
   * When delta means deploy (not in current)
   */
  public void testDeploymentPlanDeploy()
  {
    Plan<ActionDescriptor> p = plan(Type.SEQUENTIAL,
                                    delta(m([agent: 'a1', mountPoint: 'm1', script: 's1']),
                                          m()))

    assertEquals(Type.SEQUENTIAL, p.step.type)
    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <sequential agent="a1" mountPoint="m1">
      <leaf agent="a1" fabric="f1" mountPoint="m1" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="start" toState="running" />
    </sequential>
  </sequential>
</plan>
""", p.toXml())
    assertEquals(4, p.leafStepsCount)
  }

  /**
   * When delta means undeploy (not in expected)
   */
  public void testDeploymentPlanUnDeploy()
  {
    Plan<ActionDescriptor> p = plan(Type.SEQUENTIAL,
                                    delta(m(),
                                          m([agent: 'a1', mountPoint: 'm1', script: 's1'])))

    assertEquals(Type.SEQUENTIAL, p.step.type)
    assertEquals(4, p.leafStepsCount)
    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <sequential agent="a1" mountPoint="m1">
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="stop" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="unconfigure" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="uninstall" toState="NONE" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptLifecycle="uninstallScript" />
    </sequential>
  </sequential>
</plan>
""", p.toXml())
  }

  /**
   * When delta means to fully undeploy and redeploy
   */
  public void testDeploymentPlanDelta()
  {
    Plan<ActionDescriptor> p = plan(Type.SEQUENTIAL,
                                    delta(m([agent: 'a1', mountPoint: 'm1', script: 's1']),
                                          m([agent: 'a1', mountPoint: 'm1', script: 's2'])))

    assertEquals(Type.SEQUENTIAL, p.step.type)
    assertEquals(8, p.leafStepsCount)
    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <sequential agent="a1" mountPoint="m1">
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="stop" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="unconfigure" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="uninstall" toState="NONE" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptLifecycle="uninstallScript" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="start" toState="running" />
    </sequential>
  </sequential>
</plan>
""", p.toXml())
  }

  /**
   * When delta means to fully undeploy and redeploy (starting from installed with an expected
   * final state of stopped)
   */
  public void testDeploymentPlanDeltaWithEntryState()
  {
    Plan<ActionDescriptor> p = plan(Type.SEQUENTIAL,
                                    delta(m([agent: 'a1', mountPoint: 'm1', script: 's1', entryState: 'stopped']),
                                          m([agent: 'a1', mountPoint: 'm1', script: 's2', entryState: 'installed'])))

    assertEquals(Type.SEQUENTIAL, p.step.type)
    assertEquals(5, p.leafStepsCount)
    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <sequential agent="a1" mountPoint="m1">
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="uninstall" toState="NONE" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptLifecycle="uninstallScript" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="configure" toState="stopped" />
    </sequential>
  </sequential>
</plan>
""", p.toXml())
  }

  /**
   * When delta means to simply bring the entry to its expected state
   */
  public void testDeploymentPlanUnexpectedState()
  {
    Plan<ActionDescriptor> p = plan(Type.SEQUENTIAL,
                                    delta(m([agent: 'a1', mountPoint: 'm1', script: 's1']),
                                          m([agent: 'a1', mountPoint: 'm1', script: 's1', entryState: 'installed'])))

    assertEquals(Type.SEQUENTIAL, p.step.type)
    assertEquals(2, p.leafStepsCount)
    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <sequential agent="a1" mountPoint="m1">
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="m1" scriptAction="start" toState="running" />
    </sequential>
  </sequential>
</plan>
""", p.toXml())
  }

  /**
   * Test that when the parent is in delta it triggers a plan which redeploys the child as well
   * (note how the steps are intermingled)
   */
  public void testParentChildDeltaParentDelta()
  {
    Plan<ActionDescriptor> p = plan(Type.PARALLEL,
                                    delta(m([agent: 'a1', mountPoint: 'p1', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1']),

                                          m([agent: 'a1', mountPoint: 'p1', script: 's2'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1'])))

    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <parallel depth="0">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="stop" toState="stopped" />
    </parallel>
    <parallel depth="1">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="unconfigure" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="stop" toState="stopped" />
    </parallel>
    <parallel depth="2">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="uninstall" toState="NONE" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="unconfigure" toState="installed" />
    </parallel>
    <parallel depth="3">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptLifecycle="uninstallScript" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="uninstall" toState="NONE" />
    </parallel>
    <parallel depth="4">
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptLifecycle="uninstallScript" />
    </parallel>
    <parallel depth="5">
      <leaf agent="a1" fabric="f1" mountPoint="p1" script="s1" scriptLifecycle="installScript" />
    </parallel>
    <parallel depth="6">
      <leaf agent="a1" fabric="f1" mountPoint="c1" parent="p1" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="install" toState="installed" />
    </parallel>
    <parallel depth="7">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="configure" toState="stopped" />
    </parallel>
    <parallel depth="8">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="start" toState="running" />
    </parallel>
    <parallel depth="9">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="start" toState="running" />
    </parallel>
  </sequential>
</plan>
""", p.toXml())
    assertEquals(16, p.leafStepsCount)
  }

  /**
   * Complex case when the child changes parent and parent changes state...
   */
  public void testParentChildDeltaReparent()
  {
    Plan<ActionDescriptor> p = plan(Type.SEQUENTIAL,
                                    delta(m([agent: 'a1', mountPoint: 'p1', script: 's2'],
                                            [agent: 'a1', mountPoint: 'p2', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p2', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c2', parent: 'p1', script: 's1']),

                                          m([agent: 'a1', mountPoint: 'p1', script: 's1'],
                                            [agent: 'a1', mountPoint: 'p2', script: 's1', entryState: 'stopped'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c2', parent: 'p1', script: 's1'])))

    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <sequential depth="0">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="stop" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="c2" scriptAction="stop" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="p2" scriptAction="start" toState="running" />
    </sequential>
    <sequential depth="1">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="unconfigure" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="c2" scriptAction="unconfigure" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="stop" toState="stopped" />
    </sequential>
    <sequential depth="2">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="uninstall" toState="NONE" />
      <leaf agent="a1" fabric="f1" mountPoint="c2" scriptAction="uninstall" toState="NONE" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="unconfigure" toState="installed" />
    </sequential>
    <sequential depth="3">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptLifecycle="uninstallScript" />
      <leaf agent="a1" fabric="f1" mountPoint="c2" scriptLifecycle="uninstallScript" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="uninstall" toState="NONE" />
    </sequential>
    <sequential depth="4">
      <leaf agent="a1" fabric="f1" mountPoint="c1" parent="p2" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptLifecycle="uninstallScript" />
    </sequential>
    <sequential depth="5">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" script="s2" scriptLifecycle="installScript" />
    </sequential>
    <sequential depth="6">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="c2" parent="p1" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="install" toState="installed" />
    </sequential>
    <sequential depth="7">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="start" toState="running" />
      <leaf agent="a1" fabric="f1" mountPoint="c2" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="configure" toState="stopped" />
    </sequential>
    <sequential depth="8">
      <leaf agent="a1" fabric="f1" mountPoint="c2" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="start" toState="running" />
    </sequential>
    <sequential depth="9">
      <leaf agent="a1" fabric="f1" mountPoint="c2" scriptAction="start" toState="running" />
    </sequential>
  </sequential>
</plan>
""", p.toXml())
    assertEquals(25, p.leafStepsCount)
  }

  /**
   * Test that when the parent is in delta it triggers a plan which redeploys the child as well
   * (note how the steps are intermingled) even when there is a filter!
   */
  public void testParentChildDeltaWithFilter()
  {
    String filter = "mountPoint='p1'"

    Plan<ActionDescriptor> p = plan(Type.PARALLEL,
                                    delta(m([agent: 'a1', mountPoint: 'p1', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1']).filterBy(filter),

                                          m([agent: 'a1', mountPoint: 'p1', script: 's2'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1'])))

    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <parallel depth="0">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="stop" toState="stopped" />
    </parallel>
    <parallel depth="1">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="unconfigure" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="stop" toState="stopped" />
    </parallel>
    <parallel depth="2">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="uninstall" toState="NONE" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="unconfigure" toState="installed" />
    </parallel>
    <parallel depth="3">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptLifecycle="uninstallScript" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="uninstall" toState="NONE" />
    </parallel>
    <parallel depth="4">
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptLifecycle="uninstallScript" />
    </parallel>
    <parallel depth="5">
      <leaf agent="a1" fabric="f1" mountPoint="p1" script="s1" scriptLifecycle="installScript" />
    </parallel>
    <parallel depth="6">
      <leaf agent="a1" fabric="f1" mountPoint="c1" parent="p1" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="install" toState="installed" />
    </parallel>
    <parallel depth="7">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="configure" toState="stopped" />
    </parallel>
    <parallel depth="8">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="start" toState="running" />
    </parallel>
    <parallel depth="9">
      <leaf agent="a1" fabric="f1" mountPoint="c1" scriptAction="start" toState="running" />
    </parallel>
  </sequential>
</plan>
""", p.toXml())
    assertEquals(16, p.leafStepsCount)
  }

  public void testPlanWithNoOp()
  {
    Plan<ActionDescriptor> p = plan(Type.SEQUENTIAL,
                                    delta(m([agent: 'a1', mountPoint: 'm1', script: 's1']),
                                          m([agent: 'a1', mountPoint: 'm1', script: 's1',
                                             entryState: 'installed', metadata: [transitionState: 'installed->stopped']])))

    assertEquals(Type.SEQUENTIAL, p.step.type)
    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <sequential agent="a1" mountPoint="m1">
      <leaf action="noop" agent="a1" fabric="f1" mountPoint="m1" reason="alreadyInTransition" transitionState="installed-&gt;stopped" />
    </sequential>
  </sequential>
</plan>
""", p.toXml())
    assertEquals(1, p.leafStepsCount)
  }

  /**
   * child in transition => cannot redeploy the parent
   */
  public void testParentChildDeltaParentDeltaWithNoOp()
  {
    Plan<ActionDescriptor> p = plan(Type.PARALLEL,
                                    delta(m([agent: 'a1', mountPoint: 'p1', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c2', parent: 'p1', script: 's1']),

                                          m([agent: 'a1', mountPoint: 'p1', script: 's2'],
                                            [agent: 'a1', mountPoint: 'c2', parent: 'p1', script: 's1', entryState: 'stopped'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1', metadata: [transitionState: 'running->stopped']])))

    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <parallel depth="0">
      <leaf action="noop" agent="a1" fabric="f1" mountPoint="c1" reason="alreadyInTransition" transitionState="running-&gt;stopped" />
      <leaf action="noop" agent="a1" fabric="f1" mountPoint="c2" mountPointRootCause="c1" reason="alreadyInTransition" transitionState="running-&gt;stopped" />
    </parallel>
  </sequential>
</plan>
""", p.toXml())
    assertEquals(2, p.leafStepsCount)

  }

  /**
   * missing agent
   */
  public void testParentChildMissingAgentWithNoOp()
  {
    planner.agentURIProvider = new ThrowExceptionAgentURIProvider()

    Plan<ActionDescriptor> p = plan(Type.PARALLEL,
                                    delta(m([agent: 'a1', mountPoint: 'p1', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1'],
                                            [agent: 'a1', mountPoint: 'c2', parent: 'p1', script: 's1']),

                                          m([agent: 'a1', mountPoint: 'p1', script: 's2'],
                                            [agent: 'a1', mountPoint: 'c2', parent: 'p1', script: 's1', entryState: 'stopped'],
                                            [agent: 'a1', mountPoint: 'c1', parent: 'p1', script: 's1'])))

    assertEquals("""<?xml version="1.0"?>
<plan>
  <sequential>
    <parallel depth="0">
      <leaf action="noop" agent="a1" fabric="f1" mountPoint="c1" reason="missingAgent" />
      <leaf action="noop" agent="a1" fabric="f1" mountPoint="c2" reason="missingAgent" />
    </parallel>
  </sequential>
</plan>
""", p.toXml())
    assertEquals(2, p.leafStepsCount)
  }

  /**
   * Make sure that the plan generated also contains metadata and tags!
   */
  public void testMetadataAndTags()
  {
    Plan<ActionDescriptor> p = plan(Type.PARALLEL,
                                    delta(m([agent: 'a1', mountPoint: '/m1', script: 's1', metadata: [m1: 'mv1'], tags: ['t1']],
                                            [agent: 'a1', mountPoint: '/m2', script: 's1', initParameters: [i1: 'iv1'], metadata: [m1: 'mv1'], tags: ['t1']]),

                                          m()))

    assertEquals("""<?xml version="1.0"?>
<plan>
  <parallel>
    <sequential agent="a1" mountPoint="/m1">
      <leaf agent="a1" fabric="f1" initParameters="{metadata={m1=mv1}, tags=[t1]}" mountPoint="/m1" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="/m1" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="/m1" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="/m1" scriptAction="start" toState="running" />
    </sequential>
    <sequential agent="a1" mountPoint="/m2">
      <leaf agent="a1" fabric="f1" initParameters="{i1=iv1, metadata={m1=mv1}, tags=[t1]}" mountPoint="/m2" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="/m2" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="/m2" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="/m2" scriptAction="start" toState="running" />
    </sequential>
  </parallel>
</plan>
""", p.toXml())
    assertEquals(8, p.leafStepsCount)

  }

  /**
   * Test for when the agent is missing (optional feature): glu-182
   */
  public void testMissingAgent()
  {
    planner.agentURIProvider = new ThrowExceptionAgentURIProvider()
    Plan<ActionDescriptor> p = plan(Type.PARALLEL,
                                    delta(m([agent: 'a1', mountPoint: 'p1', script: 's1']),

                                          m()))

    // by default a missing agent is simply skipped
    assertEquals("""<?xml version="1.0"?>
<plan>
  <parallel>
    <sequential agent="a1" mountPoint="p1">
      <leaf action="noop" agent="a1" fabric="f1" mountPoint="p1" reason="missingAgent" />
    </sequential>
  </parallel>
</plan>
""", p.toXml())
    assertEquals(1, p.leafStepsCount)

    // but you can change the default to actually execute the actions anyway
    planner.skipMissingAgents = false
    p = plan(Type.PARALLEL,
             delta(m([agent: 'a1', mountPoint: 'p1', script: 's1']),

                   m()))

    assertEquals("""<?xml version="1.0"?>
<plan>
  <parallel>
    <sequential agent="a1" mountPoint="p1">
      <leaf agent="a1" fabric="f1" mountPoint="p1" script="s1" scriptLifecycle="installScript" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="install" toState="installed" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="configure" toState="stopped" />
      <leaf agent="a1" fabric="f1" mountPoint="p1" scriptAction="start" toState="running" />
    </sequential>
  </parallel>
</plan>
""", p.toXml())
    assertEquals(4, p.leafStepsCount)
  }


  private SystemModel m(Map... entries)
  {
    SystemModel model = new SystemModel(fabric: "f1")


    entries.each {
      model.addEntry(SystemEntry.fromExternalRepresentation(it))
    }

    return model
  }

  private SystemModelDelta delta(SystemModel expected, SystemModel current)
  {
    deltaMgr.computeDelta(expected, current, null)
  }

  private Plan<ActionDescriptor> plan(Type type, SystemModelDelta delta)
  {
    planner.computeTransitionPlan(delta).buildPlan(type, new IPlanBuilder.Config())
  }

  private Plan<ActionDescriptor> plan(Type type, Collection<SystemModelDelta> deltas)
  {
    planner.computeTransitionPlan(deltas).buildPlan(type, new IPlanBuilder.Config())
  }

  /**
   * Computes the digraph of the transitions
   * (to render with <code>dot -Tpdf < out of this method</code>)
   */
  private static String digraph(SingleDeltaTransitionPlan transitions)
  {
    String graph = new TreeMap(transitions.transitions).values().collect { Transition t ->
      t.executeBefore.sort().collect { String key ->
        "\"${t.key}\" -> \"${key}\""
      }.join('\n')
    }.join('\n')

    "digraph delta {\n${graph}\n}"
  }

  private static String toStringAfter(SingleDeltaTransitionPlan transitions)
  {
    JsonUtils.prettyPrint(new TreeMap(transitions.transitions).values().collect { Transition t ->
      "${t.key} -> ${t.executeAfter.sort()}"
    })
  }

  private static String toStringBefore(SingleDeltaTransitionPlan transitions)
  {
    JsonUtils.prettyPrint(new TreeMap(transitions.transitions).values().collect { Transition t ->
      "${t.key} -> ${t.executeBefore.sort()}"
    })
  }
}

class ThrowExceptionAgentURIProvider implements AgentURIProvider
{
  @Override
  URI getAgentURI(String fabric, String agent)
  {
    throw new NoSuchAgentException(agent)
  }

  @Override
  URI findAgentURI(String fabric, String agent)
  {
    return null
  }
}