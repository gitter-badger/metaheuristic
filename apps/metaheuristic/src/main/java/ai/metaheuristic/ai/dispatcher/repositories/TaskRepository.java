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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.beans.TaskImpl;
import ai.metaheuristic.api.dispatcher.Task;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("dispatcher")
public interface TaskRepository extends CrudRepository<TaskImpl, Long> {

    @Override
    @Modifying
    @Query(value="delete from TaskImpl t where t.id=:id")
    void deleteById(Long id);

    @Query(value="select distinct t.execContextId from TaskImpl t")
    List<Long> getAllExecContextIds();

    @Nullable
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
    @Query(value="select t.execContextId from TaskImpl t where t.id=:taskId")
    Long getExecContextId(Long taskId);

    @Query(value="select t.id from TaskImpl t where t.execContextId=:execContextId")
//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<Long> findAllTaskIdsByExecContextId(Long execContextId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    List<TaskImpl> findByProcessorIdAndResultReceivedIsFalse(Long processorId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t.id, t.assignedOn, t.execContextId from TaskImpl t " +
            "where t.processorId=:processorId and t.resultReceived=false and t.isCompleted=false")
    List<Object[]> findAllByProcessorIdAndResultReceivedIsFalseAndCompletedIsFalse(Long processorId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t.id, t.execState, t.updatedOn, t.params from TaskImpl t where t.execContextId=:execContextId")
    List<Object[]> findAllExecStateAndParamsByExecContextId(Long execContextId);

    @Query(value="select t.id, t.execState, t.updatedOn from TaskImpl t where t.execContextId=:execContextId")
    List<Object[]> findExecStateByExecContextId(Long execContextId);

    @Transactional(readOnly = true, propagation = Propagation.REQUIRED)
    void deleteByExecContextId(Long execContextId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t.id, t.params from TaskImpl t where t.execContextId=:execContextId")
    List<Object[]> findByExecContextId(Long execContextId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query(value="select t from TaskImpl t where t.execContextId=:execContextId")
    List<TaskImpl> findByExecContextIdAsList(Long execContextId);

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    @Query("SELECT t.id FROM TaskImpl t where t.processorId is null and t.execContextId=:execContextId and t.id in :ids ")
    List<Long> findForAssigning(Long execContextId, List<Long> ids);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t.id FROM TaskImpl t where t.processorId=:processorId and t.isCompleted=false")
    List<Long> findAnyActiveForProcessorId(Pageable limit, Long processorId);

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    @Query("SELECT t.id FROM TaskImpl t where t.processorId=:processorId and t.isCompleted=false")
    List<Long> findActiveForProcessorId(Long processorId);

    @Transactional(readOnly = true)
    @Query("SELECT t FROM TaskImpl t where t.processorId=:processorId")
    List<TaskImpl> findForProcessorId(Long processorId);

//    @Transactional(readOnly = true, propagation = Propagation.NOT_SUPPORTED)
    @Query("SELECT t FROM TaskImpl t where t.processorId=:processorId and t.resultReceived=false and " +
            " t.execState =:execState and (:mills - t.resultResourceScheduledOn > 15000) ")
    List<Task> findForMissingResultVariables(Long processorId, long mills, int execState);

    @Query(value="select v.id from TaskImpl v where v.execContextId=:execContextId")
    List<Long> findAllByExecContextId(Pageable pageable, Long execContextId);

    @Modifying
    @Query(value="delete from TaskImpl t where t.id in (:ids)")
    void deleteByIds(List<Long> ids);
}

