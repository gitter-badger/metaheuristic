/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai.service;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_graph.ExecContextGraphSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateSyncService;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskProviderTopLevelService;
import ai.metaheuristic.ai.dispatcher.task.TaskQueue;
import ai.metaheuristic.ai.dispatcher.test.tx.TxSupportForTestingService;
import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.preparing.PreparingSourceCodeService;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestFeatureWithSomeOk extends FeatureMethods {

    @Autowired private PreparingSourceCodeService preparingSourceCodeService;
    @Autowired private TxSupportForTestingService txSupportForTestingService;
    @Autowired private ExecContextTaskStateTopLevelService execContextTaskStateTopLevelService;
    @Autowired private TaskProviderTopLevelService taskProviderTopLevelService;
    @Autowired private ExecContextService execContextService;

    @Test
    public void testFeatureCompletionWithPartialError() {
        createExperiment();

        long mills = System.currentTimeMillis();
        log.info("Start produceTasks()");
        produceTasks();
        log.info("produceTasks() was finished for {} milliseconds", System.currentTimeMillis() - mills);

        ExecContextSyncService.getWithSync(getExecContextForTest().id, () -> {
            txSupportForTestingService.toStarted(getExecContextForTest().id);
            setExecContextForTest(Objects.requireNonNull(execContextService.findById(getExecContextForTest().getId())));

            assertEquals(EnumsApi.ExecContextState.STARTED.code, getExecContextForTest().getState());

            return null;
        });

        String sessionId = preparingSourceCodeService.step_1_0_init_session_id(getProcessorIdAsStr());
        preparingSourceCodeService.step_1_1_register_function_statuses(sessionId, getProcessorIdAsStr(), preparingSourceCodeData, preparingCodeData);

        preparingSourceCodeService.findInternalTaskForRegisteringInQueue(getExecContextForTest().id);
        preparingSourceCodeService.findTaskForRegisteringInQueueAndWait(getExecContextForTest().id);
        TaskQueue.TaskGroup taskGroup =
                ExecContextGraphSyncService.getWithSync(getExecContextForTest().execContextGraphId, ()->
                        ExecContextTaskStateSyncService.getWithSync(getExecContextForTest().execContextTaskStateId, ()->
                                execContextTaskStateTopLevelService.transferStateFromTaskQueueToExecContext(
                                        getExecContextForTest().id, getExecContextForTest().execContextGraphId, getExecContextForTest().execContextTaskStateId)));

        DispatcherCommParamsYaml.AssignedTask assignedTask = getTaskAndAssignToProcessor_mustBeNewTask();

        // this processor already got task, so don't provide any new
        DispatcherCommParamsYaml.AssignedTask task = taskProviderTopLevelService.findTask(getProcessor().getId(), false);
        // we still didn't finish task
        // so we will get the same task
        assertNotNull(task);
        assertEquals(assignedTask.taskId, task.taskId);

        storeConsoleResultAsError();

        DispatcherCommParamsYaml.AssignedTask task1 = taskProviderTopLevelService.findTask(getProcessor().getId(), false);

        assertNull(task1);
    }

}
