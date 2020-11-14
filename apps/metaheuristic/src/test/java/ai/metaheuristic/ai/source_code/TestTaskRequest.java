/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSchedulerService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTaskFinishingTopLevelService;
import ai.metaheuristic.ai.dispatcher.southbridge.SouthbridgeService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYamlUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYamlUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@Slf4j
@ActiveProfiles("dispatcher")
public class TestTaskRequest extends FeatureMethods {

    @Autowired
    public SouthbridgeService serverService;

    @Autowired
    public TaskService taskService;

    @Autowired
    public ExecContextSchedulerService execContextSchedulerService;

    @Autowired
    public ExecContextTaskFinishingTopLevelService execContextTaskFinishingTopLevelService;

    @Override
    public String getSourceCodeYamlAsString() {
        return getSourceParamsYamlAsString_Simple();
    }

    @Test
    public void testTaskRequest() {
        produceTasks();
        toStarted();
        String sessionId = step_1();
        step_2(sessionId);
        step_3(sessionId);
        step_4(sessionId);
    }

    private String step_1() {
        String sessionId;
        final ProcessorCommParamsYaml processorComm = new ProcessorCommParamsYaml();
        processorComm.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, null);

        final String processorYaml = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm);
        String dispatcherResponse = serverService.processRequest(processorYaml, "127.0.0.1");

        DispatcherCommParamsYaml d0 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse);

        assertNotNull(d0);
        assertNotNull(d0.getReAssignedProcessorId());
        assertNotNull(d0.getReAssignedProcessorId().sessionId);
        assertEquals(processorIdAsStr, d0.getReAssignedProcessorId().reAssignedProcessorId);

        sessionId = d0.getReAssignedProcessorId().sessionId;
        return sessionId;
    }

    private void step_2(String sessionId) {
        findTaskForRegisteringInQueueAndWait(execContextForTest.id);

        // get a task for processing
        DispatcherCommParamsYaml.AssignedTask t = taskProviderService.findTask(new ProcessorCommParamsYaml.ReportProcessorTaskStatus(), processor.getId(), false);
        assertNotNull(t);

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        processorComm0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        processorComm0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = serverService.processRequest(processorYaml0, "127.0.0.1");

        // get a task for processing one more time
        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);
        assertNotNull(d1);
        // there isn't any new task for processing
        assertNull(d1.getAssignedTask());

        storeConsoleResultAsOk();
        final TaskImpl task = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task);

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
        execContextSyncService.getWithSyncNullable(execContextForTest.id, () -> {
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                Enums.UploadVariableStatus status = txSupportForTestingService.setVariableReceivedWithTx(task.id, output.id);
                assertEquals(Enums.UploadVariableStatus.OK, status);
            }
            return null;
        });
        execContextTaskFinishingTopLevelService.checkTaskCanBeFinished(task.id, false);

        final TaskImpl task2 = taskRepository.findById(t.taskId).orElse(null);
        assertNotNull(task2);
        assertTrue(task2.isCompleted);

        execContextTopLevelService.updateExecContextStatus(execContextForTest.id,true);
        execContextForTest = Objects.requireNonNull(execContextService.findById(execContextForTest.id));
    }

    private void step_3(String sessionId) {
        findTaskForRegisteringInQueueAndWait(execContextForTest.id);

        final ProcessorCommParamsYaml processorComm0 = new ProcessorCommParamsYaml();
        processorComm0.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        processorComm0.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml0 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm0);
        String dispatcherResponse0 = serverService.processRequest(processorYaml0, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse0);

        assertNotNull(d);
        assertNotNull(d.getAssignedTask());
    }

    private void step_4(String sessionId) {
        final ProcessorCommParamsYaml processorComm1 = new ProcessorCommParamsYaml();
        processorComm1.processorCommContext = new ProcessorCommParamsYaml.ProcessorCommContext(processorIdAsStr, sessionId);
        processorComm1.requestTask = new ProcessorCommParamsYaml.RequestTask(true, false);

        final String processorYaml1 = ProcessorCommParamsYamlUtils.BASE_YAML_UTILS.toString(processorComm1);
        String dispatcherResponse1 = serverService.processRequest(processorYaml1, Consts.LOCALHOST_IP);

        DispatcherCommParamsYaml d1 = DispatcherCommParamsYamlUtils.BASE_YAML_UTILS.to(dispatcherResponse1);

        assertNotNull(d1);
        assertNull(d1.getAssignedTask());
    }

}
