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

package ai.metaheuristic.ai.dispatcher.exec_context;

import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.ai.dispatcher.commons.DataHolder;
import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
import ai.metaheuristic.ai.dispatcher.event.CheckTaskCanBeFinishedTxEvent;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.dispatcher.ExecContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 1/18/2020
 * Time: 3:34 PM
 */
@SuppressWarnings("DuplicatedCode")
@Service
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExecContextFSM {

    private final ExecContextCache execContextCache;
    private final ExecContextSyncService execContextSyncService;
    private final TaskRepository taskRepository;
    private final ExecContextService execContextService;
    private final ExecContextReconciliationService execContextReconciliationService;
    private final ApplicationEventPublisher eventPublisher;

    public void toFinished(ExecContextImpl execContext) {
        execContextSyncService.checkWriteLockPresent(execContext.id);
        toStateWithCompletion(execContext, EnumsApi.ExecContextState.FINISHED);
    }

    public void toError(ExecContextImpl execContext) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContext.id);
        toStateWithCompletion(execContext, EnumsApi.ExecContextState.ERROR);
    }

    public void toState(Long execContextId, EnumsApi.ExecContextState state) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContextId);
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return;
        }
        if (execContext.state !=state.code) {
            execContext.setState(state.code);
            execContextService.save(execContext);
        }
    }

    @Transactional
    public OperationStatusRest changeExecContextStateWithTx(EnumsApi.ExecContextState execState, Long execContextId, Long companyUniqueId) {
        return changeExecContextState(execState, execContextId, companyUniqueId);
    }

    private OperationStatusRest changeExecContextState(EnumsApi.ExecContextState execState, Long execContextId, Long companyUniqueId) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        OperationStatusRest status = checkExecContext(execContextId);
        if (status != null) {
            return status;
        }
        status = execContextTargetState(execContextId, execState, companyUniqueId);
        return status;
    }

    public OperationStatusRest execContextTargetState(ExecContextImpl execContext, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        TxUtils.checkTxExists();
        execContextSyncService.checkWriteLockPresent(execContext.id);

        execContext.setState(execState.code);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private OperationStatusRest execContextTargetState(Long execContextId, EnumsApi.ExecContextState execState, Long companyUniqueId) {
        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#303.040 execContext wasn't found, execContextId: " + execContextId);
        }

        if (execContext.state !=execState.code) {
            toState(execContext.id, execState);
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Nullable
    private OperationStatusRest checkExecContext(Long execContextId) {
        ExecContext wb = execContextCache.findById(execContextId);
        if (wb==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#303.060 ExecContext wasn't found, execContextId: " + execContextId );
        }
        return null;
    }

    private void toStateWithCompletion(ExecContextImpl execContext, EnumsApi.ExecContextState state) {
        if (execContext.state != state.code) {
            execContext.setCompletedOn(System.currentTimeMillis());
            execContext.setState(state.code);
            execContextService.save(execContext);
        } else if (execContext.state!= EnumsApi.ExecContextState.FINISHED.code && execContext.completedOn != null) {
            log.error("#303.080 Integrity failed, current state: {}, new state: {}, but execContext.completedOn!=null",
                    execContext.state, state.code);
        }
    }

    @Transactional
    public Void storeExecResultWithTx(ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, DataHolder holder) {
        TxUtils.checkTxExists();
        TaskImpl task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("#303.100 Reporting about non-existed task #{}", result.taskId);
            return null;
        }

        return storeExecResult(task, result, holder);
    }

    public Void storeExecResult(TaskImpl task, ProcessorCommParamsYaml.ReportTaskProcessingResult.SimpleTaskExecResult result, DataHolder holder) {
        task.setFunctionExecResults(result.getResult());
        task.setResultReceived(true);

        eventPublisher.publishEvent(new CheckTaskCanBeFinishedTxEvent(task.execContextId, task.id, true));
        return null;
    }

    public Void updateExecContextStatus(Long execContextId, boolean needReconciliation) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            return null;
        }
        if (needReconciliation) {
            ExecContextData.ReconciliationStatus status = execContextReconciliationService.reconcileStates(execContext);
            execContextReconciliationService.finishReconciliation(status);
        }
        else {
            // TODO 2020-11-02 should this commented code be deleted?
            /*
            long countUnfinishedTasks = execContextGraphTopLevelService.getCountUnfinishedTasks(execContext);
            if (countUnfinishedTasks == 0) {
                // workaround for situation when states in graph and db are different
                ExecContextReconciliationService.ReconciliationStatus status = execContextReconciliationService.reconcileStates(execContext);
                execContextSyncService.getWithSync(execContext.id,
                        () -> execContextReconciliationService.finishReconciliation(status));
                execContext = execContextCache.findById(execContextId);
                if (execContext == null) {
                    return null;
                }
                countUnfinishedTasks = execContextGraphTopLevelService.getCountUnfinishedTasks(execContext);
                if (countUnfinishedTasks==0) {
                    log.info("ExecContext #{} was finished", execContextId);
                    toFinished(execContext);
                }
*/
        }
        return null;
    }

    public List<Long> getAllByProcessorIdIsNullAndExecContextIdAndIdIn(Long execContextId, List<ExecContextData.TaskVertex> vertices, int page) {
        final List<Long> idsForSearch = ExecContextService.getIdsForSearch(vertices, page, 20);
        if (idsForSearch.isEmpty()) {
            return List.of();
        }
        return taskRepository.findForAssigning(execContextId, idsForSearch);
    }

}
