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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextTaskState;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.TaskData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.SetTaskExecStateTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context_task_state.ExecContextTaskStateCache;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskFinishingService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.task.TaskStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.exec_context_task_state.ExecContextTaskStateParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 10/15/2020
 * Time: 9:02 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextTaskResettingService {

    private final ExecContextCache execContextCache;
    private final VariableService variableService;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final EventPublisherService eventPublisherService;
    private final ExecContextTaskStateCache execContextTaskStateCache;
    private final TaskFinishingService taskFinishingService;
    private final TaskStateService taskStateService;

    @Transactional
    public void resetTasksWithErrorForRecovery(Long execContextId, List<TaskData.TaskWithRecoveryStatus> statuses) {
        ExecContextSyncService.checkWriteLockPresent(execContextId);

        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            return;
        }

        ExecContextTaskState execContextTaskState = execContextTaskStateCache.findById(ec.execContextTaskStateId);
        if (execContextTaskState==null) {
            log.error("#155.030 ExecContextTaskState wasn't found for execContext #{}", execContextId);
            return;
        }
        ExecContextTaskStateParamsYaml ectspy = execContextTaskState.getExecContextTaskStateParamsYaml();

        for (TaskData.TaskWithRecoveryStatus status : statuses) {
            if (status.targetState== EnumsApi.TaskExecState.ERROR) {
                // console is null because an actual text of error was specified with TaskExecState.ERROR_WITH_RECOVERY
                TaskSyncService.getWithSyncVoid(status.taskId,
                        ()->taskFinishingService.finishWithError(status.taskId, null, EnumsApi.TaskExecState.ERROR));
            }
            else if (status.targetState==EnumsApi.TaskExecState.NONE) {
                TaskSyncService.getWithSyncVoid(status.taskId,
                        ()->resetTask(ec, status.taskId));
                ectspy.triesWasMade.put(status.taskId, status.triesWasMade);
            }
            else {
                throw new IllegalStateException("status.targetState==");
            }
        }
        execContextTaskState.updateParams(ectspy);
    }

    @Transactional
    public void resetTaskWithTx(Long execContextId, Long taskId) {
        TaskSyncService.checkWriteLockNotPresent(taskId);

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        TaskSyncService.getWithSyncVoid(taskId, ()->resetTask(execContext, taskId));
    }

    public void resetTask(ExecContextImpl execContext, Long taskId) {
        TxUtils.checkTxExists();
        ExecContextSyncService.checkWriteLockPresent(execContext.id);
        TaskSyncService.checkWriteLockPresent(taskId);

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        log.info("#305.025 Start re-setting task #{}", taskId);
        if (task == null) {
            String es = S.f("#320.020 Found a non-existed task, graph consistency for execContextId #%s is failed",
                    execContext.id);
            log.error(es);
            execContext.completedOn = System.currentTimeMillis();
            execContext.state = EnumsApi.ExecContextState.ERROR.code;
            return;
        }

        TaskParamsYaml taskParams = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
        final ExecContextParamsYaml.Process process = execContext.getExecContextParamsYaml().findProcess(taskParams.task.processCode);
        if (process==null) {
            throw new BreakFromLambdaException("#375.080 Process '" + taskParams.task.processCode + "' wasn't found");
        }

        task.setFunctionExecResults(null);
        task.setProcessorId(null);
        task.setAssignedOn(null);
        task.setCompleted(false);
        task.setCompletedOn(null);
        task.execState = process.cache!=null && process.cache.enabled ? EnumsApi.TaskExecState.CHECK_CACHE.value : EnumsApi.TaskExecState.NONE.value;
        task.setResultReceived(false);
        task.setResultResourceScheduledOn(0);
        taskService.save(task);
        for (TaskParamsYaml.OutputVariable output : taskParams.task.outputs) {
            if (output.context== EnumsApi.VariableContext.global) {
                throw new IllegalStateException("(output.context== EnumsApi.VariableContext.global)");
            }
            variableService.resetVariable(execContext.id, output.id);
        }

        eventPublisherService.publishSetTaskExecStateTxEvent(new SetTaskExecStateTxEvent(task.execContextId, task.id, EnumsApi.TaskExecState.from(task.execState)));
//        eventPublisherService.publishUnAssignTaskTxEventAfterCommit(new UnAssignTaskTxAfterCommitEvent(task.execContextId, task.id));

        log.info("#305.035 task #{} and its output variables were re-setted to initial state", taskId);
    }

}
