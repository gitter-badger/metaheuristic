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

package ai.metaheuristic.ai.launchpad.workbook;

import ai.metaheuristic.ai.launchpad.atlas.AtlasService;
import ai.metaheuristic.ai.launchpad.beans.TaskImpl;
import ai.metaheuristic.ai.launchpad.beans.WorkbookImpl;
import ai.metaheuristic.ai.launchpad.experiment.ExperimentService;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
import ai.metaheuristic.ai.launchpad.repositories.TaskRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.workbook.WorkbookParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    private final WorkbookGraphService workbookGraphService;
    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final AtlasService atlasService;
    private final WorkbookCache workbookCache;
    private final WorkbookService workbookService;
    private final WorkbookRepository workbookRepository;
    private final TaskRepository taskRepository;

    public void updateWorkbookStatuses(boolean needReconciliation) {
        List<WorkbookImpl> workbooks = workbookRepository.findByExecState(EnumsApi.WorkbookExecState.STARTED.code);
        for (WorkbookImpl workbook : workbooks) {
            updateWorkbookStatus(workbook, needReconciliation);
        }
    }

    /**
     *
     * @param workbook WorkbookImpl much be real object from db, not from cache
     * @param needReconciliation
     * @return WorkbookImpl updated workbook
     */
    public void updateWorkbookStatus(WorkbookImpl workbook, boolean needReconciliation) {

        final long countUnfinishedTasks = workbookGraphService.getCountUnfinishedTasks(workbook);
        if (countUnfinishedTasks==0) {
            log.info("Workbook #{} was finished", workbook.getId());
            experimentService.updateMaxValueForExperimentFeatures(workbook.getId());
            workbookService.toFinished(workbook.getId());
            WorkbookImpl instance = workbookCache.findById(workbook.id);

            Long experimentId = experimentRepository.findIdByWorkbookId(instance.getId());
            if (experimentId==null) {
                log.info("#705.050 Can't store an experiment to atlas, the workbook "+instance.getId()+" doesn't contain an experiment" );
                return;
            }
            atlasService.toAtlas(instance.getId(), experimentId);
            //noinspection UnnecessaryReturnStatement
            return;
        }
        else {
            if (needReconciliation) {
                List<Object[]> list = taskRepository.findAllExecStateByWorkbookId(workbook.getId());
                final Long workbookId = workbook.id;

                // Reconcile states in db and in graph
                List<WorkbookParamsYaml.TaskVertex> vertices = workbookGraphService.findAll(workbook);
                Map<Long, Integer> states = new HashMap<>(list.size()+1);
                for (Object[] o : list) {
                    Long taskId = (Long) o[0];
                    Integer execState = (Integer) o[1];
                    states.put(taskId, execState);
                }
//                final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
//                readWriteLock.writeLock().lock();
                ConcurrentHashMap<Long, Integer> taskStates = new ConcurrentHashMap<>();
                AtomicBoolean isNullState = new AtomicBoolean(false);
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
                    workbookService.toError(workbook.getId());
                }
                else {
                    workbookService.updateTaskExecStates(workbook, taskStates);
                }

                // fix actual state of tasks (can be as a result of OptimisticLockingException) {
                // fix IN_PROCESSING stae
                states.entrySet().stream()
                        .filter(e-> EnumsApi.TaskExecState.IN_PROGRESS.value==e.getValue())
                        .forEach(e->{
                            Long taskId = e.getKey();
                            TaskImpl task = taskRepository.findById(taskId).orElse(null);
                            if (task!=null && task.resultReceived && task.isCompleted ) {
                                WorkbookImpl wb = workbookRepository.findByIdForUpdate(workbookId);
                                workbookService.updateTaskExecState(wb, task.id, EnumsApi.TaskExecState.OK.value);
                            }
                        });
            }
        }
    }
}
