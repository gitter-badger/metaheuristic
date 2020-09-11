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

package ai.metaheuristic.ai.dispatcher.repositories;

import ai.metaheuristic.ai.dispatcher.beans.DispatcherEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Serge
 * Date: 10/14/2019
 * Time: 8:20 PM
 */
@Repository
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Profile("dispatcher")
public interface DispatcherEventRepository extends CrudRepository<DispatcherEvent, Long> {

    @Query(value="select e.id from DispatcherEvent e where e.period in :periods")
    List<Long> findIdByPeriod(List<Integer> periods);

    @Query(value="select e from DispatcherEvent e where e.id in :ids ")
    List<DispatcherEvent> findByIds(List<Long> ids);
}
