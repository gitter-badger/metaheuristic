/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.cache.CacheService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.event.SetTaskExecStateTxEvent;
import ai.metaheuristic.ai.dispatcher.event.UpdateTaskExecStatesInGraphTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * @author Serge
 * Date: 12/18/2020
 * Time: 4:18 AM
 */
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class TaskStateService {


    private final DispatcherEventService dispatcherEventService;
    private final TaskService taskService;
    private final CacheService cacheService;
    private final TaskRepository taskRepository;
    private final TaskSyncService taskSyncService;
    private final TaskProviderTopLevelService taskProviderTopLevelService;
    private final TaskExecStateService taskExecStateService;
    private final ApplicationEventPublisher eventPublisher;

    public void updateTaskExecStates(TaskImpl task, EnumsApi.TaskExecState execState, @Nullable String taskContextId) {
        updateTaskExecStates(task, execState, taskContextId, false);
    }

    public void updateTaskExecStates(TaskImpl task, EnumsApi.TaskExecState execState, @Nullable String taskContextId, boolean markAsCompleted) {
        TxUtils.checkTxExists();
        taskSyncService.checkWriteLockPresent(task.id);
        TaskImpl t = taskExecStateService.changeTaskState(task, execState);
        if (markAsCompleted) {
            t.setCompleted(true);
            t.setCompletedOn(System.currentTimeMillis());
        }
    }

    @Transactional
    public Void finishAndStoreVariable(Long taskId, ExecContextParamsYaml ecpy) {
        taskSyncService.checkWriteLockPresent(taskId);

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#319.100 Reporting about non-existed task #{}", taskId);
            return null;
        }

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());

        updateTaskExecStates(
                task, EnumsApi.TaskExecState.OK, tpy.task.taskContextId, true);

        if (tpy.task.cache!=null && tpy.task.cache.enabled) {
            ExecContextParamsYaml.Process p = ecpy.findProcess(tpy.task.processCode);
            if (p==null) {
                log.warn("#319.120 Process {} wasn't found", tpy.task.processCode);
                return null;
            }
            cacheService.storeVariables(tpy, p.function);
        }
        return null;
    }

    @Transactional
    public Void finishWithErrorWithTx(Long taskId, String console) {
        taskSyncService.checkWriteLockPresent(taskId);
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.warn("#319.140 task #{} wasn't found", taskId);
            return null;
        }
        String taskContextId = null;
        try {
            final TaskParamsYaml taskParamYaml;
            taskParamYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.getParams());
            taskContextId = taskParamYaml.task.taskContextId;
        } catch (YAMLException e) {
            String es = S.f("#319.160 Task #%s has broken params yaml, error: %s, params:\n%s", task.getId(), e.toString(), task.getParams());
            log.error(es, e.getMessage());
        }

        return finishWithError(task, console, taskContextId, -10001);
    }

    public void finishWithError(TaskImpl task, @Nullable String taskContextId) {
        finishWithError(task, "#319.180 Task was finished with an unknown error, can't process it", taskContextId);
    }

    public Void finishWithError(TaskImpl task, String console, @Nullable String taskContextId) {
        return finishWithError(task, console, taskContextId, -10002);
    }

    public Void finishWithError(TaskImpl task, String console, @Nullable String taskContextId, int exitCode) {
        TxUtils.checkTxExists();

        taskSyncService.checkWriteLockPresent(task.id);

        finishTaskAsError(task, exitCode, console);
        dispatcherEventService.publishTaskEvent(EnumsApi.DispatcherEventType.TASK_ERROR,null, task.id, task.execContextId);
        return null;
    }


    private void finishTaskAsError(TaskImpl task, int exitCode, String console) {
        if (task.execState==EnumsApi.TaskExecState.ERROR.value && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)) {
            log.info("#319.200 (task.execState==state.value && task.isCompleted && task.resultReceived && !S.b(task.functionExecResults)), task: {}", task.id);
            return;
        }
        task.setExecState(EnumsApi.TaskExecState.ERROR.value);
        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());

        if (S.b(task.functionExecResults)) {
            TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
            functionExec.exec = new FunctionApiData.SystemExecResult(tpy.task.function.code, false, exitCode, console);
            task.setFunctionExecResults(FunctionExecUtils.toString(functionExec));
        }
        task.setResultReceived(true);

        task = taskService.save(task);

        taskProviderTopLevelService.setTaskExecState(task.execContextId, task.id, EnumsApi.TaskExecState.ERROR);
    }



}
