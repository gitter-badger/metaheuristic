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

package ai.metaheuristic.ai.launchpad.company;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.launchpad.beans.Account;
import ai.metaheuristic.ai.launchpad.beans.Batch;
import ai.metaheuristic.ai.launchpad.beans.Company;
import ai.metaheuristic.ai.launchpad.repositories.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class CompanyCache {

    private final CompanyRepository companyRepository;

    @CacheEvict(cacheNames = Consts.COMPANIES_CACHE, key = "#result.id")
    public Company save(Company account) {
        return companyRepository.save(account);
    }

    @Cacheable(cacheNames = {Consts.COMPANIES_CACHE}, unless="#result==null")
    public Company findById(Long id) {
        return companyRepository.findById(id).orElse(null);
    }


}