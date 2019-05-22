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

package ai.metaheuristic.ai.launchpad.repositories;

import ai.metaheuristic.ai.launchpad.beans.Experiment;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Component
@Transactional
@Profile("launchpad")
public interface ExperimentRepository extends CrudRepository<Experiment, Long> {

    @Transactional(readOnly = true)
    Slice<Experiment> findAll(Pageable pageable);

    @Transactional(readOnly = true)
    Slice<Experiment> findAllByOrderByIdDesc(Pageable pageable);

    Experiment findByWorkbookId(long workbookId);

    @Override
    @Transactional(readOnly = true)
    List<Experiment> findAll();

    Experiment findByCode(String code);
}