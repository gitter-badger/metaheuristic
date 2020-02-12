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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.launchpad.atlas.AtlasService;
import ai.metaheuristic.ai.launchpad.beans.ExecContextImpl;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Serge
 * Date: 7/16/2019
 * Time: 12:23 AM
 */
@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class WorkbookSchedulerService {

    private final WorkbookService workbookService;
    private final WorkbookRepository workbookRepository;
    private final TaskRepository taskRepository;
    private final AtlasService atlasService;
    private final WorkbookFSM workbookFSM;
    private final WorkbookGraphTopLevelService workbookGraphTopLevelService;

    public void updateWorkbookStatuses(boolean needReconciliation) {
        List<ExecContextImpl> workbooks = workbookRepository.findByExecState(EnumsApi.WorkbookExecState.STARTED.code);
        for (ExecContextImpl workbook : workbooks) {
            updateWorkbookStatus(workbook.id, needReconciliation);
        }

        List<Long> workbooksIds = workbookRepository.findIdsByExecState(EnumsApi.WorkbookExecState.EXPORTING_TO_ATLAS.code);
        for (Long workbookId : workbooksIds) {
            log.info("Start exporting execContext #{} to atlas", workbookId);
            OperationStatusRest status;
            try {
                status = atlasService.storeExperimentToAtlas(workbookId);
            } catch (Exception e) {
                workbookFSM.toError(workbookId);
                continue;
            }

            if (status.status==EnumsApi.OperationStatus.OK) {
                log.info("Exporting of execContext #{} was finished", workbookId);
            } else {
                workbookFSM.toError(workbookId);
                log.error("Error exporting experiment to atlas, workbookID #{}\n{}", workbookId, status.getErrorMessagesAsStr());
            }
        }
    }

    /**
     *
     * @param workbookId ExecContext Id
     * @param needReconciliation
     * @return ExecContextImpl updated execContext
     */
    public void updateWorkbookStatus(Long workbookId, boolean needReconciliation) {

        long countUnfinishedTasks = workbookService.getCountUnfinishedTasks(workbookId);
        if (countUnfinishedTasks==0) {
            // workaround for situation when states in graph and db are different
            reconcileStates(workbookId);
            countUnfinishedTasks = workbookService.getCountUnfinishedTasks(workbookId);
            if (countUnfinishedTasks==0) {
                log.info("ExecContext #{} was finished", workbookId);
                workbookFSM.toFinished(workbookId);
            }
        }
        else {
            if (needReconciliation) {
                reconcileStates(workbookId);
            }
        }
    }

    public void reconcileStates(Long workbookId) {
        List<Object[]> list = taskRepository.findAllExecStateByWorkbookId(workbookId);

        // Reconcile states in db and in graph
        Map<Long, Integer> states = new HashMap<>(list.size()+1);
        for (Object[] o : list) {
            Long taskId = (Long) o[0];
            Integer execState = (Integer) o[1];
            states.put(taskId, execState);
        }

        ConcurrentHashMap<Long, Integer> taskStates = new ConcurrentHashMap<>();
        AtomicBoolean isNullState = new AtomicBoolean(false);

        List<WorkbookParamsYaml.TaskVertex> vertices = workbookService.findAllVertices(workbookId);
        vertices.stream().parallel().forEach(tv -> {
            Integer state = states.get(tv.taskId);
            if (state==null) {
                isNullState.set(true);
            }
            else if (tv.execState.value!=state) {
                log.info("#705.054 Found different states for task #"+tv.taskId+", " +
                        "db: "+ EnumsApi.TaskExecState.from(state)+", " +
                        "graph: "+tv.execState);
                taskStates.put(tv.taskId, state);
            }
        });

        if (isNullState.get()) {
            log.info("#705.052 Found non-created task, graph consistency is failed");
            workbookFSM.toError(workbookId);
        }
        else {
            workbookGraphTopLevelService.updateTaskExecStates(workbookId, taskStates);
        }

        // fix actual state of tasks (can be as a result of OptimisticLockingException)
        // fix IN_PROCESSING state
        // find and reset all hanging up tasks
        states.entrySet().stream()
                .filter(e-> EnumsApi.TaskExecState.IN_PROGRESS.value==e.getValue())
                .forEach(e->{
                    Long taskId = e.getKey();
                    TaskImpl task = taskRepository.findById(taskId).orElse(null);
                    if (task != null) {
                        TaskParamsYaml tpy = TaskParamsYamlUtils.BASE_YAML_UTILS.to(task.params);

                        // did this task hang up at station?
                        if (task.assignedOn!=null && tpy.taskYaml.timeoutBeforeTerminate != null && tpy.taskYaml.timeoutBeforeTerminate!=0L) {
                            final long multiplyBy2 = tpy.taskYaml.timeoutBeforeTerminate * 2 * 1000;
                            final long oneHourToMills = TimeUnit.HOURS.toMillis(1);
                            long timeout = Math.min(multiplyBy2, oneHourToMills);
                            if ((System.currentTimeMillis() - task.assignedOn) > timeout) {
                                log.info("Reset task #{}, multiplyBy2: {}, timeout: {}", task.id, multiplyBy2, timeout);
                                workbookService.resetTask(task.id);
                            }
                        }
                        else if (task.resultReceived && task.isCompleted) {
                            workbookGraphTopLevelService.updateTaskExecStateByWorkbookId(workbookId, task.id, EnumsApi.TaskExecState.OK.value);
                        }
                    }
                });
    }
}
