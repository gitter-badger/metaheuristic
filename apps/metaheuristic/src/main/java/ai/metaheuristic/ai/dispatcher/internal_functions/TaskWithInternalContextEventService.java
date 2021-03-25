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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.event.TaskWithInternalContextEvent;
import ai.metaheuristic.ai.dispatcher.event.UpdateTaskExecStatesInGraphTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.task.TaskStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.InternalFunctionException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Serge
 * Date: 3/15/2020
 * Time: 10:58 PM
 */
@SuppressWarnings("unused")
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskWithInternalContextEventService {

    private final TaskWithInternalContextService taskWithInternalContextService;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;
    private final ExecContextFSM execContextFSM;
    private final TaskRepository taskRepository;
    private final ExecContextTaskFinishingService execContextTaskFinishingService;
    private final TaskStateService taskStateService;
    private final TaskSyncService taskSyncService;
    private final ApplicationEventPublisher applicationEventPublisher;

    private final InternalFunctionProcessor internalFunctionProcessor;
    private final TaskService taskService;
    private final ExecContextVariableService execContextVariableService;
    private final VariableService variableService;
    private final ApplicationEventPublisher eventPublisher;

    private final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);

    public void processInternalFunction(final TaskWithInternalContextEvent event) {
        executor.submit(() -> process(event));
    }

    private void process(final TaskWithInternalContextEvent event) {
        TxUtils.checkTxNotExists();

        ExecContextImpl execContext = execContextCache.findById(event.execContextId);
        if (execContext==null) {
            return;
        }
        final ExecContextData.SimpleExecContext simpleExecContext = execContext.asSimple();
        try {
            taskSyncService.getWithSyncNullable(event.taskId,
                    () -> {
                        taskWithInternalContextService.preProcessing(simpleExecContext, event.taskId);
                        processInternalFunction(simpleExecContext, event.taskId);
                        return null;
                    });
        }
        catch(InternalFunctionException e) {
            if (e.result.processing != Enums.InternalFunctionProcessing.ok) {
                log.error("#707.160 error type: {}, message: {}\n\tsourceCodeId: {}, execContextId: {}",
                        e.result.processing, e.result.error, event.sourceCodeId, event.execContextId);
                final String console = "#707.180 Task #" + event.taskId + " was finished with status '" + e.result.processing + "', text of error: " + e.result.error;
                execContextSyncService.getWithSyncNullable(event.execContextId,
                        () -> taskSyncService.getWithSyncNullable(event.taskId,
                                () -> taskStateService.finishWithErrorWithTx(event.taskId, console)));
            }
        }
        catch (Throwable th) {
            final String es = "#989.020 Error while processing the task #"+event.taskId+" with internal function. Error: " + th.getMessage() +
                    ". Cause error: " + (th.getCause()!=null ? th.getCause().getMessage() : " is null.");

            log.error(es, th);
            execContextSyncService.getWithSyncNullable(event.execContextId,
                    () -> taskSyncService.getWithSyncNullable(event.taskId,
                            () -> taskStateService.finishWithErrorWithTx(event.taskId, es)));
        }
    }

    private Void processInternalFunction(ExecContextData.SimpleExecContext simpleExecContext, Long taskId) {
        TaskLastProcessingHelper.lastTaskId = null;
        try {
            TaskImpl task = taskRepository.findById(taskId).orElse(null);
            if (task==null) {
                log.warn("#707.040 Task #{} with internal context doesn't exist", taskId);
                return null;
            }

            if (task.execState != EnumsApi.TaskExecState.IN_PROGRESS.value) {
                log.error("#707.080 Task #"+task.id+" already in progress.");
                return null;
            }

            TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
            ExecContextParamsYaml.Process p = simpleExecContext.paramsYaml.findProcess(taskParamsYaml.task.processCode);
            if (p == null) {
                if (Consts.MH_FINISH_FUNCTION.equals(taskParamsYaml.task.processCode)) {
                    ExecContextParamsYaml.FunctionDefinition function = new ExecContextParamsYaml.FunctionDefinition(Consts.MH_FINISH_FUNCTION, "", EnumsApi.FunctionExecContext.internal);
                    p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, Consts.TOP_LEVEL_CONTEXT_ID, function);
                }
                else {
                    final String msg = "#707.140 can't find process '" + taskParamsYaml.task.processCode + "' in execContext with Id #" + simpleExecContext.execContextId;
                    log.warn(msg);
                    throw new InternalFunctionException(new InternalFunctionData.InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.process_not_found, msg));
                }
            }

            internalFunctionProcessor.process(simpleExecContext, taskId, p.internalContextId, taskParamsYaml);

            taskWithInternalContextService.storeResult(taskId, taskParamsYaml);
        }
        finally {
            eventPublisher.publishEvent(new UpdateTaskExecStatesInGraphTxEvent(simpleExecContext.execContextId, taskId));

            TaskLastProcessingHelper.lastTaskId = taskId;
        }
        return null;
    }

}
