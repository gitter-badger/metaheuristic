package aiai.ai.flow;

import aiai.ai.Enums;
import aiai.ai.comm.Protocol;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.SimpleTaskExecResult;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.preparing.PreparingExperiment;
import aiai.ai.preparing.PreparingFlow;
import aiai.ai.yaml.flow.FlowYaml;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestFlowService extends PreparingFlow {

    @Autowired
    public TaskService taskService;

    @Override
    public String getFlowParamsAsYaml() {
        flowYaml = new FlowYaml();
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.inputType = "raw-part-data";
            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = true;
            p.outputType = "assembled-raw";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = Collections.singletonList("snippet-02:1.1");
            p.collectResources = true;
            p.outputType = "dataset-processing";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippetCodes = Arrays.asList("snippet-03:1.1", "snippet-04:1.1", "snippet-05:1.1");
            p.parallelExec = true;
            p.collectResources = true;
            p.outputType = "feature";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = PreparingExperiment.TEST_EXPERIMENT_CODE_01;

            p.metas.addAll(
                    Arrays.asList(
                            new Process.Meta("assembled-raw", "assembled-raw", null),
                            new Process.Meta("dataset", "dataset-processing", null),
                            new Process.Meta("feature", "feature", null)
                    )
            );

            flowYaml.processes.add(p);
        }

        String yaml = flowYamlUtils.toString(flowYaml);
        System.out.println(yaml);
        return yaml;
    }

    @After
    public void afterTestFlowService() {
        if (flowInstance!=null) {
            try {
                taskRepository.deleteByFlowInstanceId(flowInstance.getId());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Finished TestFlowService.afterTestFlowService()");
    }

    @Test
    public void testCreateTasks() {
        assertFalse(flowYaml.processes.isEmpty());
        assertEquals(Enums.ProcessType.EXPERIMENT, flowYaml.processes.get(flowYaml.processes.size()-1).type);

        Enums.FlowVerifyStatus status = flowService.verify(flow);
        assertEquals(Enums.FlowVerifyStatus.OK, status);

        FlowService.TaskProducingResult result = flowService.createTasks(flow, PreparingFlow.INPUT_POOL_CODE);
        experiment = experimentCache.findById(experiment.getId());

        flowInstance = result.flowInstance;
        List<Task> tasks = taskRepository.findByFlowInstanceId(result.flowInstance.getId());
        assertNotNull(result);
        assertNotNull(result.flowInstance);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());

        int taskNumber = 0;
        for (Process process : flowYaml.processes) {
            if (process.type== Enums.ProcessType.EXPERIMENT) {
                continue;
            }
            taskNumber += process.snippetCodes.size();
        }

        assertEquals( 1+1+3+ 2*12*7, taskNumber +  experiment.getNumberOfTask());


        ExperimentService.TasksAndAssignToStationResult assignToStation =
                experimentService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());

        Protocol.AssignedTask.Task simpleTask = assignToStation.getSimpleTask();
        assertNotNull(simpleTask);
        assertNotNull(simpleTask.getTaskId());
        Task task = taskRepository.findById(simpleTask.getTaskId()). orElse(null);
        assertNotNull(task);
        assertEquals(1, task.getOrder());

        ExperimentService.TasksAndAssignToStationResult assignToStation2 =
                experimentService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());
        assertNull(assignToStation2.getSimpleTask());

        SimpleTaskExecResult r = new SimpleTaskExecResult();
        r.setTaskId(simpleTask.getTaskId());
        r.setMetrics(null);
        r.setResult("Everything is Ok.");

        taskService.markAsCompleted(r);

        Protocol.AssignedTask.Task simpleTask3 = assignToStation.getSimpleTask();
        assertNotNull(simpleTask3);
        assertNotNull(simpleTask3.getTaskId());
        Task task3 = taskRepository.findById(simpleTask3.getTaskId()). orElse(null);
        assertNotNull(task3);
        assertEquals(1, task3.getOrder());
        assertNotEquals(task.getId(), task3.getId());

        ExperimentService.TasksAndAssignToStationResult assignToStation4 =
                experimentService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());
        assertNull(assignToStation4.getSimpleTask());

        maskAsCompletedOrder(flowInstance.getId(), 1);

        Protocol.AssignedTask.Task simpleTask5 = assignToStation.getSimpleTask();
        assertNotNull(simpleTask5);
        assertNotNull(simpleTask5.getTaskId());
        Task task5 = taskRepository.findById(simpleTask5.getTaskId()). orElse(null);
        assertNotNull(task5);
        assertEquals(2, task5.getOrder());

        ExperimentService.TasksAndAssignToStationResult assignToStation6 =
                experimentService.getTaskAndAssignToStation(station.getId(), false, flowInstance.getId());
        assertNull(assignToStation6.getSimpleTask());

        int i=0;
    }

    private void maskAsCompletedOrder(long flowInstanceId, int order) {
        List<Task> tasks = taskRepository.findForAssigning(flowInstanceId, order);
        List<SimpleTaskExecResult> results = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getOrder()==order) {
                SimpleTaskExecResult r = new SimpleTaskExecResult();
                r.setTaskId(task.getId());
                r.setMetrics(null);
                r.setResult("Everything is Ok.");

                results.add(r);
            }
        }
        taskService.storeAllResults(results);
    }
}
