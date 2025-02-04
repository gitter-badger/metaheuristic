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

import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.dispatcher_params.DispatcherParamsTopLevelService;
import ai.metaheuristic.ai.dispatcher.event.DeleteExecContextEvent;
import ai.metaheuristic.ai.dispatcher.repositories.ExecContextRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.dispatcher.task.TaskQueueService;
import ai.metaheuristic.ai.dispatcher.task.TaskSyncService;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * @author Serge
 * Date: 7/4/2019
 * Time: 3:56 PM
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ExecContextTopLevelService {

    private final ExecContextService execContextService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextFSM execContextFSM;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final ExecContextReconciliationTopLevelService execContextReconciliationTopLevelService;
    private final DispatcherParamsTopLevelService dispatcherParamsTopLevelService;

    private static boolean isManagerRole(String role) {
        return switch (role) {
            case "ROLE_ADMIN", "ROLE_DATA", "MANAGER", "OPERATOR" -> true;
            default -> false;
        };
    }

    public ExecContextApiData.ExecContextStateResult getExecContextState(Long sourceCodeId, Long execContextId, DispatcherContext context, Authentication authentication) {
        ExecContextApiData.RawExecContextStateResult raw = execContextService.getRawExecContextState(sourceCodeId, execContextId, context);
        if (raw.isErrorMessages()) {
            return new ExecContextApiData.ExecContextStateResult(raw.getErrorMessagesAsList());
        }
        boolean managerRole = authentication.getAuthorities().stream().anyMatch(o -> isManagerRole(o.getAuthority()));
        ExecContextApiData.ExecContextStateResult r = ExecContextUtils.getExecContextStateResult(execContextId, raw, managerRole);

        // we'll calculate an info only for rootExecContext
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec!=null && ec.rootExecContextId==null) {
            r.taskStateInfos = getTaskStateInfos(execContextId);
        }
        return r;
    }

    private ExecContextApiData.TaskStateInfos getTaskStateInfos(Long execContextId) {
        ExecContextApiData.TaskStateInfos stateInfos = new ExecContextApiData.TaskStateInfos();
        List<Object[]> states = taskRepository.getTaskExecStates(execContextId);
        for (Object[] obj : states) {
            int execState = ((Number) obj[0]).intValue();
            int count = ((Number) obj[1]).intValue();
            stateInfos.taskInfos.add(new ExecContextApiData.TaskStateInfo(EnumsApi.TaskExecState.from(execState), count));
        }
        stateInfos.taskInfos.sort(Comparator.comparingInt(o -> o.execState.value));
        stateInfos.totalTasks = stateInfos.taskInfos.stream().mapToInt(o->o.count).sum();

        List<Object[]> simpleTaskInfos = taskRepository.getSimpleTaskInfos(execContextId);
        List<Long> longRunningIds = dispatcherParamsTopLevelService.getLongRunningTaskIds();

        // https://stackoverflow.com/questions/31657036/getting-object-with-max-date-property-from-list-of-objects-java-8/31657274#31657274
        Object[] obj = simpleTaskInfos.stream()
                .filter(o-> o[2]!=null && EnumsApi.TaskExecState.isFinishedState(((Number) o[1]).intValue()) && !longRunningIds.contains(((Number) o[0]).longValue()))
                .max(Comparator.comparing(o-> ((Number) o[2]).longValue()))
                .orElse(null);

        long countRunning = simpleTaskInfos.stream()
                .filter(o-> EnumsApi.TaskExecState.IN_PROGRESS.value==((Number) o[1]).intValue() && !longRunningIds.contains(((Number) o[0]).longValue()))
                .count();

        //noinspection UnnecessaryLocalVariable
        ExecContextApiData.NonLongRunning nonLongRunning = new ExecContextApiData.NonLongRunning( obj!=null ? ((Number)obj[2]).longValue() : null, (int) countRunning);
        stateInfos.nonLongRunning = nonLongRunning;

        return stateInfos;
    }

    public List<Long> storeAllConsoleResults(List<ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult> results) {
        List<Long> ids = new ArrayList<>();
        for (ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result : results) {
            Long taskId = storeExecResult(result);
            if (taskId != null) {
                ids.add(taskId);
            }
        }
        return ids;
    }

    public SourceCodeApiData.ExecContextResult getExecContextExtended(Long execContextId) {
        SourceCodeApiData.ExecContextResult result = execContextService.getExecContextExtended(execContextId);

        if (result.isErrorMessages()) {
            return result;
        }

        if (!result.sourceCode.getId().equals(result.execContext.getSourceCodeId())) {
            ExecContextSyncService.getWithSyncNullable(execContextId,
                    () -> execContextService.changeValidStatus(execContextId, false));
            return new SourceCodeApiData.ExecContextResult("#210.030 sourceCodeId doesn't match to execContext.sourceCodeId, " +
                    "sourceCodeId: " + result.execContext.getSourceCodeId() + ", execContext.sourceCodeId: " + result.execContext.getSourceCodeId());
        }
        return result;
    }

    public OperationStatusRest changeExecContextState(String state, Long execContextId, DispatcherContext context) {
        EnumsApi.ExecContextState execState = EnumsApi.ExecContextState.from(state.toUpperCase());
        if (execState == EnumsApi.ExecContextState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#210.060 Unknown exec state, state: " + state);
        }
        if (execState!= EnumsApi.ExecContextState.STARTED && execState!= EnumsApi.ExecContextState.STOPPED) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#210.090 execCpntext state can be only STARTED or STOPPED, requested state: " + state);
        }
        ExecContextImpl ec = execContextCache.findById(execContextId);
        if (ec==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#210.120 ExecContext #" + execContextId +" wasn't found");
        }
        List<Long> execContextIds = execContextRepository.findAllRelatedExecContextIds(execContextId);
        execContextIds.add(execContextId);
        for (Long contextId : execContextIds) {
            ExecContextImpl ecLoop = execContextCache.findById(contextId);
            if (ecLoop==null) {
                continue;
            }
            EnumsApi.ExecContextState execContextState = EnumsApi.ExecContextState.fromCode(ecLoop.state);
            if (execContextState!= EnumsApi.ExecContextState.STARTED && execContextState!= EnumsApi.ExecContextState.STOPPED) {
                continue;
            }
            OperationStatusRest status = ExecContextSyncService.getWithSync(contextId,
                    () -> execContextFSM.changeExecContextStateWithTx(execState, contextId, context.getCompanyId()));
            if (status.status== EnumsApi.OperationStatus.ERROR) {
                return status;
            }
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        return ExecContextSyncService.getWithSync(execContextId,
                () -> execContextFSM.changeExecContextStateWithTx(execState, execContextId, companyUniqueId));
    }

    public void updateExecContextStatus(Long execContextId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        final ExecContextData.ReconciliationStatus status =
                execContextReconciliationTopLevelService.reconcileStates(execContext);

        ExecContextSyncService.getWithSyncVoid(execContextId, () -> execContextFSM.updateExecContextStatus(execContextId, status));
    }

    @Nullable
    private Long storeExecResult(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task == null) {
            log.warn("#210.150 Reporting about non-existed task #{}", result.taskId);
            return null;
        }

        ExecContextImpl execContext = execContextCache.findById(task.execContextId);
        if (execContext == null) {
            log.warn("#210.180 Reporting about non-existed execContext #{}", task.execContextId);
            return null;
        }
        if (task.execState == EnumsApi.TaskExecState.ERROR.value ||
                task.execState == EnumsApi.TaskExecState.ERROR_WITH_RECOVERY.value||
                task.execState == EnumsApi.TaskExecState.OK.value) {
            return task.id;
        }
        // TODO 2021-11-22 it's not clear - do we have to check TaskExecState.SKIPPED state as well
        if (task.execState == EnumsApi.TaskExecState.SKIPPED.value) {
            log.warn("#210.210 storeExecResult() was called, taskId: #{}, taskExecState: SKIPPED", task.id);
        }

        try {
            TaskSyncService.getWithSyncVoid(task.id, () -> storeExecResultInternal(result));
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("#210.240 ObjectOptimisticLockingFailureException as caught, let try to store exec result one more time");
            TaskSyncService.getWithSyncVoid(task.id, () -> storeExecResultInternal(result));
        }
        return task.id;
    }

    // this methd here because there was a strange behaviour
    // when execContextFSM.storeExecResultWithTx() was called as lambda directly
    private void storeExecResultInternal(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result) {
        execContextFSM.storeExecResultWithTx(result);
    }

    public void deleteOrphanExecContexts(Collection<Long> execContextIds) {
        for (Long execContextId : execContextIds) {
            log.info("#210.270 Found orphan execContext #{}", execContextId);
            try {
                ExecContextSyncService.getWithSyncVoid(execContextId, ()-> execContextService.deleteExecContext(execContextId));
            }
            catch (Throwable th) {
                log.error("#210.300 execContextService.deleteExecContext("+execContextId+")", th);
            }
        }
    }

    @Async
    @EventListener
    public void deleteExecContextById(DeleteExecContextEvent event) {
        ExecContextSyncService.getWithSyncVoid(event.execContextId, ()-> execContextService.deleteExecContext(event.execContextId));
    }

    public OperationStatusRest deleteExecContextById(Long execContextId, DispatcherContext context) {
        return ExecContextSyncService.getWithSync(execContextId, ()-> execContextService.deleteExecContextById(execContextId, context));
    }

    public ExecContextApiData.TaskExecInfo getTaskExecInfo(Long sourceCodeId, Long execContextId, Long taskId) {

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            log.warn("#210.330 Reporting about non-existed execContext #{}", execContextId);
            return new ExecContextApiData.TaskExecInfo(sourceCodeId, execContextId, taskId, EnumsApi.TaskExecState.ERROR, S.f("ExecContext #%s wasn't found", execContextId));
        }

        TaskImpl task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("#210.360 Reporting about non-existed task #{}", taskId);
            return new ExecContextApiData.TaskExecInfo(sourceCodeId, execContextId, taskId, EnumsApi.TaskExecState.ERROR, S.f("Task #%s wasn't found", taskId));
        }
        if (!execContextId.equals(task.execContextId)) {
            log.warn("#210.390 Reporting about non-existed task #{}", taskId);
            return new ExecContextApiData.TaskExecInfo(sourceCodeId, execContextId, taskId, EnumsApi.TaskExecState.ERROR, S.f("Task #%s doesn't belong to execContext #%s", taskId, execContextId));
        }
        return new ExecContextApiData.TaskExecInfo(sourceCodeId, execContextId, taskId, EnumsApi.TaskExecState.from(task.execState),
                S.b(task.functionExecResults) ? S.f("Task #%s doesn't have functionExecResults", taskId) : task.functionExecResults);
    }
}
