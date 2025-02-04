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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.dispatcher.beans.CacheProcess;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.EventPublisherService;
import ai.metaheuristic.ai.dispatcher.event.UpdateTaskExecStatesInGraphTxEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.repositories.CacheProcessRepository;
import ai.metaheuristic.ai.dispatcher.repositories.CacheVariableRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.variable.VariableDatabaseSpecificService;
import ai.metaheuristic.ai.exceptions.InvalidateCacheProcessException;
import ai.metaheuristic.ai.yaml.function_exec.FunctionExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Serge
 * Date: 10/30/2020
 * Time: 7:25 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskCheckCachingService {

    private final ExecContextService execContextService;
    private final TaskRepository taskRepository;
    private final TaskStateService taskStateService;
    private final CacheProcessRepository cacheProcessRepository;
    private final CacheVariableRepository cacheVariableRepository;
    private final TaskVariableService taskVariableService;
    private final VariableDatabaseSpecificService variableDatabaseSpecificService;
    private final EventPublisherService eventPublisherService;

    @Transactional
    public void invalidateCacheItemAndSetTaskToNone(Long execContextId, Long taskId, Long cacheProcessId) {
        ExecContextImpl execContext = execContextService.findById(execContextId);
        if (execContext==null) {
            log.info("#609.020 ExecContext #{} doesn't exists", execContextId);
            return;
        }
        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            return;
        }

        cacheVariableRepository.deleteByCacheProcessId(cacheProcessId);
        cacheProcessRepository.deleteById(cacheProcessId);
        taskStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.NONE);
    }

    @Transactional
    public void checkCaching(Long execContextId, Long taskId, @Nullable CacheProcess cacheProcess) {

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task==null) {
            log.debug("#609.009 task #{} wasn't found", taskId);
            return;
        }
        if (task.execState!=EnumsApi.TaskExecState.CHECK_CACHE.value) {
            log.info("#609.010 task #{} was already checked for cached variables", taskId);
            return;
        }

        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

        if (cacheProcess!=null) {
            log.info("#609.060 cached data was found for task #{}, variables will be copied and will task be set as OK", taskId);
            // finish task with cached data

            List<Object[]> vars = cacheVariableRepository.getVarsByCacheProcessId(cacheProcess.id);
            if (vars.size()!=tpy.task.outputs.size()) {
                log.warn("#609.080 cashProcess #{} is broken. Number of stored variable is {} but expected {}. CacheProcess will be invalidated", cacheProcess.id, vars.size(), tpy.task.outputs.size());
                throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
            }
            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                boolean found = false;
                for (Object[] var : vars) {
                    if (var[1].equals(output.name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    log.warn("#609.100 cashProcess #{} is broken. output variable {} wasn't found. CacheProcess will be invalidated.\n" +
                                    "vars[0]: {}\nvars[1]: {}\nvars[2]: {}\n",
                            cacheProcess.id, output.name,
                            vars.get(0)[0]!=null?vars.get(0)[0].getClass().getName():null,
                            vars.get(0)[1]!=null?vars.get(0)[1].getClass().getName():null,
                            vars.get(0)[2]!=null?vars.get(0)[2].getClass().getName():null
                    );
                    log.warn("tpy.task.outputs:");
                    tpy.task.outputs.forEach(o->log.warn("'\t{}", o.toString()));
                    log.warn("vars:");
                    vars.forEach(o->log.warn("'\t{}", Arrays.toString(o)));
                    throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
                }
            }

            for (TaskParamsYaml.OutputVariable output : tpy.task.outputs) {
                Object[] obj = vars.stream().filter(o->o[1].equals(output.name)).findFirst().orElseThrow(()->new IllegalStateException("#609.120 ???? How???"));
                try {
                    VariableData.StoredVariable storedVariable = new VariableData.StoredVariable( ((Number)obj[0]).longValue(), (String)obj[1], Boolean.TRUE.equals(obj[2]));
                    if (storedVariable.nullified) {
                        taskVariableService.setVariableAsNull(taskId, output.id);
                    }
                    else {
                        variableDatabaseSpecificService.copyData(storedVariable, output);
                    }

                    output.uploaded = true;

                } catch (IOException e) {
                    log.warn("#609.160 error", e);
                    throw new InvalidateCacheProcessException(execContextId, taskId, cacheProcess.id);
                }
            }
            task.params = TaskParamsYamlUtils.BASE_YAML_UTILS.toString(tpy);

            FunctionApiData.FunctionExec functionExec = new FunctionApiData.FunctionExec();
            functionExec.exec = new FunctionApiData.SystemExecResult(tpy.task.function.code, true, 0,
                    "Process was finished with cached data, cacheProcessId: "+ cacheProcess.id);

            task.setFunctionExecResults(FunctionExecUtils.toString(functionExec));
            task.setResultReceived(true);

            task.execState = EnumsApi.TaskExecState.OK.value;

            task.setCompleted(true);
            task.setCompletedOn(System.currentTimeMillis());

            eventPublisherService.publishUpdateTaskExecStatesInGraphTxEvent(new UpdateTaskExecStatesInGraphTxEvent(task.execContextId, task.id));
        }
        else {
            log.info("#609.080 cached data wasn't found for task #{}", taskId);
            taskStateService.updateTaskExecStates(task, EnumsApi.TaskExecState.NONE);
        }
    }

}
