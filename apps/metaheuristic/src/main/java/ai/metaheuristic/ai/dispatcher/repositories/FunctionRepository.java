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

import ai.metaheuristic.ai.dispatcher.beans.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
@Profile("dispatcher")
public interface FunctionRepository extends CrudRepository<Function, Long> {

    @Override
    @Modifying
    @Query(value="delete from Function t where t.id=:id")
    void deleteById(Long id);

    @Transactional(readOnly = true)
    @Nullable
    Function findByCode(String code);

    @Transactional(readOnly = true)
    @Nullable
    @Query(value="select b.id from Function b where b.code=:code")
    Long findIdByCode(String code);

    @Transactional(readOnly = true)
    @NonNull
    List<Function> findAll();

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    @Query(value="select b.id from Function b")
    List<Long> findAllIds();

    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    @Query(value="select b.code from Function b")
    List<String> findAllFunctionCodes();
}
