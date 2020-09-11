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

import ai.metaheuristic.ai.dispatcher.beans.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Profile("dispatcher")
public interface FunctionRepository extends CrudRepository<Function, Long> {

    @Nullable
    Function findByCode(String code);

    @Nullable
    @Query(value="select b.id from Function b where b.code=:code")
    Long findIdByCode(String code);

    @NonNull
    List<Function> findAll();

    @Nullable
    @Query(value="select b from Function b where b.code=:code")
    Function findByCodeForUpdate(String code);

    @Query(value="select b.id from Function b")
    List<Long> findAllIds();

    @Query(value="select b.code from Function b")
    List<String> findAllFunctionCodes();
}
