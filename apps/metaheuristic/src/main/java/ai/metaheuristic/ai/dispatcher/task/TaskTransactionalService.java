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

package ai.metaheuristic.ai.dispatcher.task;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 9/23/2020
 * Time: 11:39 AM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class TaskTransactionalService {

    private final TaskRepository taskRepository;

    @Transactional
    public Void deleteOrphanTasks(Long execContextId) {
        List<Long> ids = taskRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId) ;
        if (ids.isEmpty()) {
            return null;
        }
        log.info("Found orphan task, execContextId: #{}, tasks #{}", execContextId, ids);
        taskRepository.deleteByIds(ids);
/*
        for (Long id : ids) {
            log.info("Found orphan task #{}, execContextId: #{}", id, execContextId);
            taskRepository.deleteById(id);
        }
*/
        return null;
    }


}
