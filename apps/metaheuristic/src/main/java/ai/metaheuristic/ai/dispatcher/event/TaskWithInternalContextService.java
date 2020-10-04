/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.event;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.InternalFunctionData;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.exec_context.*;
import ai.metaheuristic.ai.dispatcher.internal_functions.InternalFunctionProcessor;
import ai.metaheuristic.ai.dispatcher.task.TaskExecStateService;
import ai.metaheuristic.ai.dispatcher.task.TaskService;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.exec_context.ExecContextParamsYamlUtils;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Serge
 * Date: 9/28/2020
 * Time: 10:18 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("dispatcher")
public class TaskWithInternalContextService {

    private final InternalFunctionProcessor internalFunctionProcessor;
    private final ExecContextCache execContextCache;
    private final TaskTransactionalService taskTransactionalService;
    private final TaskExecStateService taskExecStateService;
    private final ExecContextSyncService execContextSyncService;
    private final ExecContextFSM execContextFSM;
    private final VariableService variableService;
    private final TaskService taskService;
    private final ExecContextTaskStateService execContextTaskStateService;
    private final ExecContextVariableService execContextVariableService;

    private static Long lastTaskId=null;
    // this code is only for testing
    public static boolean taskFinished(Long id) {
        return id.equals(lastTaskId);
    }

    @Transactional
    public void processInternalFunction(TaskImpl task, VariableData.DataStreamHolder holder) {
        execContextSyncService.checkWriteLockPresent(task.execContextId);

        try {
            task.setAssignedOn(System.currentTimeMillis());
            task.setResultResourceScheduledOn(0);
            task = taskService.save(task);

            ExecContextImpl execContext = execContextCache.findById(task.execContextId);
            if (execContext == null) {
                taskExecStateService.finishTaskAsError(task.id, EnumsApi.TaskExecState.ERROR, -10000,
                        "#707.030 Task #" + task.id + " is broken, execContext #" + task.execContextId + " wasn't found.");
                return;
            }
            try {
                if (task.execState == EnumsApi.TaskExecState.IN_PROGRESS.value) {
                    log.error("#707.012 Task #"+task.id+" already in progress. mustn't happened. it's, actually, illegal state");
                    return;
                }
                if (task.execState!= EnumsApi.TaskExecState.NONE.value) {
                    log.info("#707.011 Task #"+task.id+" was already processed with state " + EnumsApi.TaskExecState.from(task.execState));
                    return;
                }
                if (EnumsApi.TaskExecState.isFinishedState(task.execState)) {
                    log.error("#707.015 Task #"+task.id+" already was finished");
                    return;
                }
                execContextTaskStateService.updateTaskExecStates(
                        execContextCache.findById(task.execContextId), task.id, EnumsApi.TaskExecState.IN_PROGRESS.value, null);

                TaskParamsYaml taskParamsYaml = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);
                ExecContextParamsYaml execContextParamsYaml = ExecContextParamsYamlUtils.BASE_YAML_UTILS.to(execContext.params);
                ExecContextParamsYaml.Process p = execContextParamsYaml.findProcess(taskParamsYaml.task.processCode);
                if (p == null) {
                    if (Consts.MH_FINISH_FUNCTION.equals(taskParamsYaml.task.processCode)) {
                        ExecContextParamsYaml.FunctionDefinition function = new ExecContextParamsYaml.FunctionDefinition(Consts.MH_FINISH_FUNCTION, "", EnumsApi.FunctionExecContext.internal);
                        p = new ExecContextParamsYaml.Process(Consts.MH_FINISH_FUNCTION, Consts.MH_FINISH_FUNCTION, Consts.TOP_LEVEL_CONTEXT_ID, function);
                    }
                    else {
                        log.warn("#707.040 can't find process '" + taskParamsYaml.task.processCode + "' in execContext with Id #" + execContext.id);
                        return;
                    }
                }

                // ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService.prepareVariables
                // won't be called for initializing output variables in internal function.
                // the code which skips initializing is here - ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService.getTaskAndAssignToProcessor
                variableService.initOutputVariables(taskParamsYaml, execContext, p);
                taskTransactionalService.setParams(task.id, taskParamsYaml);

                InternalFunctionData.InternalFunctionProcessingResult result = internalFunctionProcessor.process(
                        execContext.id, task.id, p.internalContextId, taskParamsYaml, holder);

                if (result.processing != Enums.InternalFunctionProcessing.ok) {
                    execContextFSM.markAsFinishedWithError(task.id, execContext.sourceCodeId, execContext.id, taskParamsYaml, result);
                    return;
                }
                execContextVariableService.setResultReceivedForInternalFunction(task.id);

                ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult r = new ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult();
                r.taskId = task.id;
                FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
                functionExec.exec = new FunctionApiData.SystemExecResult(taskParamsYaml.task.function.code, true, 0, "");
                r.result = FunctionExecUtils.toString(functionExec);

                execContextFSM.storeExecResult(r);

            } catch (CommonErrorWithDataException th) {
                String es = "#707.067 Task #" + task.id + " and "+th.getAdditionalInfo()+" was processed with error: " + th.getMessage();
                taskExecStateService.finishTaskAsError(task.id, EnumsApi.TaskExecState.ERROR, -10002, es);
                log.error(es);
            } catch (Throwable th) {
                String es = "#707.070 Task #" + task.id + " was processed with error: " + th.getMessage();
                taskExecStateService.finishTaskAsError(task.id, EnumsApi.TaskExecState.ERROR, -10003, es);
                log.error(es, th);
            }
        } catch (Throwable th) {
            String es = "#707.080 Task #" + task.id + " was processed with error: " + th.getMessage();
            taskExecStateService.finishTaskAsError(task.id, EnumsApi.TaskExecState.ERROR, -10004, es);
            log.error(es, th);
        }
        finally {
            lastTaskId = task.id;
        }
    }

}
