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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.event.ProcessDeletedExecContextEvent;
import ai.metaheuristic.ai.dispatcher.event.SetVariableReceivedTxEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskFinishWithErrorEvent;
import ai.metaheuristic.ai.dispatcher.event.TaskQueueCleanByExecContextIdEvent;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 7:22 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskTopLevelService {

    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TaskTransactionalService taskTransactionalService;

    public void deleteOrphanTasks(List<Long> orphanExecContextIds) {
        TxUtils.checkTxNotExists();
        for (Long execContextId : orphanExecContextIds) {
            if (execContextCache.findById(execContextId)!=null) {
                log.warn("execContextId #{} wasn't deleted, actually", execContextId);
                continue;
            }
            applicationEventPublisher.publishEvent(new ProcessDeletedExecContextEvent(execContextId));
            applicationEventPublisher.publishEvent(new TaskQueueCleanByExecContextIdEvent(execContextId));

            List<Long> ids;
            while (!(ids = taskRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    log.info("Found orphan task, execContextId: #{}, tasks #{}", execContextId, page);
                    execContextSyncService.getWithSyncNullable(execContextId, () -> taskTransactionalService.deleteOrphanTasks(page));
                }
            }
        }
    }

    public void processResendTaskOutputResourceResult(@Nullable String processorId, Enums.ResendTaskOutputResourceStatus status, Long taskId, Long variableId) {
        switch (status) {
            case SEND_SCHEDULED:
                log.info("#303.380 Processor #{} scheduled sending of output variables of task #{} for sending. This is normal operation of Processor", processorId, taskId);
                break;
            case TASK_NOT_FOUND:
            case VARIABLE_NOT_FOUND:
            case TASK_IS_BROKEN:
            case TASK_PARAM_FILE_NOT_FOUND:

                applicationEventPublisher.publishEvent(new TaskFinishWithErrorEvent(taskId, "#303.390 Task was finished while resending variable with status " + status));

                break;
            case OUTPUT_RESOURCE_ON_EXTERNAL_STORAGE:

                applicationEventPublisher.publishEvent(new SetVariableReceivedTxEvent(taskId, variableId, false));

/*

                taskSyncService.getWithSyncNullable(taskId, ()-> {
                    UploadResult statusResult = taskVariableTopLevelService.updateStatusOfVariable(taskId, variableId);
                    if (statusResult.status == Enums.UploadVariableStatus.OK) {
                        log.info("#303.400 the output resource of task #{} is stored on external storage which was defined by disk://. This is normal operation of sourceCode", taskId);
                    } else {
                        log.info("#303.420 can't update isCompleted field for task #{}", taskId);
                    }
                    return null;
                });
*/
                break;
        }
    }


}
