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

import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryRequest;
import io.temporal.api.workflowservice.v1.GetWorkflowExecutionHistoryResponse;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.CanceledFailure;
import io.temporal.testing.TestWorkflowRule;
import java.time.Duration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class AbandonOnCancelActivityTest {

  public static final String NAMESPACE = "UnitTest";
  private final WorkflowTest.TestActivitiesImpl activitiesImpl =
      new WorkflowTest.TestActivitiesImpl(null);

  @Rule
  public TestWorkflowRule testWorkflowRule =
      TestWorkflowRule.newBuilder()
          .setWorkflowTypes(TestAbandonOnCancelActivity.class)
          .setActivityImplementations(activitiesImpl)
          .setUseExternalService(Boolean.parseBoolean(System.getenv("USE_DOCKER_SERVICE")))
          .setTarget(System.getenv("TEMPORAL_SERVICE_ADDRESS"))
          .build();

  @Test
  public void testAbandonOnCancelActivity() {
    WorkflowTest.TestWorkflow1 client =
        testWorkflowRule
            .getWorkflowClient()
            .newWorkflowStub(
                WorkflowTest.TestWorkflow1.class,
                WorkflowTest.newWorkflowOptionsBuilder(testWorkflowRule.getTaskQueue()).build());
    WorkflowExecution execution =
        WorkflowClient.start(client::execute, testWorkflowRule.getTaskQueue());
    testWorkflowRule
        .getTestEnvironment()
        .sleep(Duration.ofMillis(500)); // To let activityWithDelay start.
    WorkflowStub stub = WorkflowStub.fromTyped(client);
    WorkflowTest.waitForOKQuery(stub);
    stub.cancel();
    long start = testWorkflowRule.getTestEnvironment().currentTimeMillis();
    try {
      stub.getResult(String.class);
      Assert.fail("unreachable");
    } catch (WorkflowFailedException e) {
      Assert.assertTrue(e.getCause() instanceof CanceledFailure);
    }
    long elapsed = testWorkflowRule.getTestEnvironment().currentTimeMillis() - start;
    Assert.assertTrue(String.valueOf(elapsed), elapsed < 500);
    activitiesImpl.assertInvocations("activityWithDelay");
    GetWorkflowExecutionHistoryRequest request =
        GetWorkflowExecutionHistoryRequest.newBuilder()
            .setNamespace(NAMESPACE)
            .setExecution(execution)
            .build();
    GetWorkflowExecutionHistoryResponse response =
        testWorkflowRule
            .getWorkflowClient()
            .getWorkflowServiceStubs()
            .blockingStub()
            .getWorkflowExecutionHistory(request);

    for (HistoryEvent event : response.getHistory().getEventsList()) {
      Assert.assertNotEquals(
          EventType.EVENT_TYPE_ACTIVITY_TASK_CANCEL_REQUESTED, event.getEventType());
    }
  }

  public static class TestAbandonOnCancelActivity implements WorkflowTest.TestWorkflow1 {
    @Override
    public String execute(String taskQueue) {
      WorkflowTest.TestActivities testActivities =
          Workflow.newActivityStub(
              WorkflowTest.TestActivities.class,
              ActivityOptions.newBuilder(WorkflowTest.newActivityOptions1(taskQueue))
                  .setHeartbeatTimeout(Duration.ofSeconds(10))
                  .setCancellationType(ActivityCancellationType.ABANDON)
                  .build());
      testActivities.activityWithDelay(100000, true);
      return "foo";
    }
  }
}