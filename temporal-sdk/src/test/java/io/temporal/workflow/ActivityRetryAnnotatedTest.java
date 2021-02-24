/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowException;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowRule;
import java.io.IOException;
import java.time.Duration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class ActivityRetryAnnotatedTest {

  private final WorkflowTest.TestActivitiesImpl activitiesImpl =
      new WorkflowTest.TestActivitiesImpl(null);

  @Rule
  public TestWorkflowRule testWorkflowRule =
      TestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestActivityRetryAnnotated.class)
          .setActivityImplementations(activitiesImpl)
          .setUseExternalService(Boolean.parseBoolean(System.getenv("USE_DOCKER_SERVICE")))
          .setTarget(System.getenv("TEMPORAL_SERVICE_ADDRESS"))
          .build();

  @Test
  public void testActivityRetryAnnotated() {
    WorkflowTest.TestWorkflow1 workflowStub =
        testWorkflowRule
            .getWorkflowClient()
            .newWorkflowStub(
                WorkflowTest.TestWorkflow1.class,
                WorkflowTest.newWorkflowOptionsBuilder(testWorkflowRule.getTaskQueue()).build());
    try {
      workflowStub.execute(testWorkflowRule.getTaskQueue());
      Assert.fail("unreachable");
    } catch (WorkflowException e) {
      Assert.assertTrue(e.getCause() instanceof ActivityFailure);
      Assert.assertTrue(e.getCause().getCause() instanceof ApplicationFailure);
      Assert.assertEquals(
          IOException.class.getName(), ((ApplicationFailure) e.getCause().getCause()).getType());
    }
    Assert.assertEquals(activitiesImpl.toString(), 3, activitiesImpl.invocations.size());
  }

  public static class TestActivityRetryAnnotated implements WorkflowTest.TestWorkflow1 {

    private final WorkflowTest.TestActivities activities;

    public TestActivityRetryAnnotated() {
      this.activities =
          Workflow.newActivityStub(
              WorkflowTest.TestActivities.class,
              ActivityOptions.newBuilder()
                  .setScheduleToCloseTimeout(Duration.ofSeconds(5))
                  .setScheduleToStartTimeout(Duration.ofSeconds(5))
                  .setHeartbeatTimeout(Duration.ofSeconds(5))
                  .setStartToCloseTimeout(Duration.ofSeconds(10))
                  .build());
    }

    @Override
    public String execute(String taskQueue) {
      activities.throwIOAnnotated();
      return "ignored";
    }
  }
}