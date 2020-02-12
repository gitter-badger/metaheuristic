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

package ai.metaheuristic.ai.launchpad.repositories;

import ai.metaheuristic.ai.launchpad.beans.ExecContextImpl;
import ai.metaheuristic.api.launchpad.ExecContext;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
@Profile("launchpad")
public interface WorkbookRepository extends CrudRepository<ExecContextImpl, Long> {

    @Query(value="select e from ExecContextImpl e where e.id=:id")
    ExecContextImpl findByIdForUpdate(Long id);

    @Transactional(readOnly = true)
    @Query(value="select w.id, w.execState from ExecContextImpl w ")
    List<Object[]> findAllExecStates();

    @Transactional(readOnly = true)
    @Query(value="select w.id from ExecContextImpl w")
    List<Long> findAllIds();

    @Transactional(readOnly = true)
    @Query(value="select e.id from ExecContextImpl e where e.execState=:execState order by e.createdOn asc ")
    List<Long> findByExecStateOrderByCreatedOnAsc(int execState);

    @Transactional
    List<ExecContextImpl> findByExecState(int execState);

    @Transactional(readOnly = true)
    @Query(value="select e.id from ExecContextImpl e where e.execState=:execState")
    List<Long> findIdsByExecState(int execState);

    @Transactional(readOnly = true)
    @Query(value="select e.id from ExecContextImpl e where e.sourceCodeId=:planId")
    List<Long> findIdsByPlanId(Long planId);

    @Transactional(readOnly = true)
    Slice<ExecContext> findByPlanIdOrderByCreatedOnDesc(Pageable pageable, Long planId);

}

