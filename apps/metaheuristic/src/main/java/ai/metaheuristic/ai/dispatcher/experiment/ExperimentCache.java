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
package ai.metaheuristic.ai.dispatcher.experiment;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.beans.Experiment;
import ai.metaheuristic.ai.dispatcher.repositories.ExperimentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dispatcher")
@Slf4j
@RequiredArgsConstructor
public class ExperimentCache {

    private final ExperimentRepository experimentRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    @CacheEvict(value = {Consts.EXPERIMENTS_CACHE}, key = "#result.id")
    public Experiment save(Experiment experiment) {
        // noinspection UnusedAssignment
        Experiment save=null;
        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            save = experimentRepository.save(experiment);
            return save;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw e;
        }
    }

    @Nullable
    @Transactional(propagation = Propagation.MANDATORY)
    @Cacheable(cacheNames = {Consts.EXPERIMENTS_CACHE}, unless="#result==null")
    public Experiment findById(Long id) {
        return experimentRepository.findById(id).orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @CacheEvict(cacheNames = {Consts.EXPERIMENTS_CACHE}, key = "#experiment.id")
    public void delete(@NonNull Experiment experiment) {
        try {
            experimentRepository.delete(experiment);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @CacheEvict(cacheNames = {Consts.EXPERIMENTS_CACHE}, key = "#id")
    public void invalidate(Long id) {
        //
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @CacheEvict(cacheNames = {Consts.EXPERIMENTS_CACHE}, key = "#id")
    public void deleteById(@NonNull Long id) {
        try {
            experimentRepository.deleteById(id);
        } catch (EmptyResultDataAccessException e) {
            // it's ok
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Error", e);
        }
    }
}
