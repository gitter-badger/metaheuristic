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

import ai.metaheuristic.ai.launchpad.beans.BinaryDataImpl;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.launchpad.BinaryData;
import ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl;
import ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleResource;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static ai.metaheuristic.api.v1.EnumsApi.*;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:41
 */
@Repository
@Profile("launchpad")
public interface BinaryDataRepository extends CrudRepository<BinaryDataImpl, Long> {
    List<BinaryData> findAllByDataType(int dataType);

    @Query(value="select new ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl(" +
            "b.code, b.poolCode, b.params ) " +
            "from BinaryDataImpl b where b.poolCode in :poolCodes and " +
            "b.refId=:refId and b.refType='workbook'")
    List<SimpleCodeAndStorageUrl> getCodeAndStorageUrlInPool(List<String> poolCodes, long refId);

    @Query(value="select new ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl(" +
            "b.code, b.poolCode, b.params ) " +
            "from BinaryDataImpl b where b.poolCode in :poolCodes")
    List<SimpleCodeAndStorageUrl> getCodeAndStorageUrlInPool(List<String> poolCodes);

    @Query(value="select new ai.metaheuristic.ai.launchpad.binary_data.SimpleCodeAndStorageUrl(" +
            "b.code, b.poolCode, b.params ) " +
            "from BinaryDataImpl b where b.code in :codes ")
    List<SimpleCodeAndStorageUrl> getCodeAndStorageUrl(List<String> codes);

    List<BinaryDataImpl> findAllByPoolCode(String poolCode);

    @Query(value="select b.id from BinaryDataImpl b where b.poolCode=:poolCode and b.dataType=:dataType ")
    List<Long> findIdsByPoolCodeAndDataType(String poolCode, int dataType);

    @Query(value="select b.filename from BinaryDataImpl b where b.poolCode=:poolCode and b.dataType=:dataType ")
    String findFilenameByPoolCodeAndDataType(String poolCode, int dataType);

    BinaryDataImpl findByCode(String code);

    @Transactional(readOnly = true)
    Slice<BinaryData> findAll(Pageable pageable);

    @Transactional
    void deleteAllByDataType(int dataType);

    @Transactional
    void deleteByCodeAndDataType(String code, int dataType);

    @Transactional
    void deleteByRefIdAndRefType(long refId, String refType);

    @Transactional
    void deleteByPoolCodeAndDataType(String poolCode, int dataType);

    @Query(value="select new ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleResource(" +
            "b.id, b.version, b.code, b.poolCode, b.dataType, b.uploadTs, b.checksum, b.valid, b.manual, b.filename, " +
            "b.params ) " +
            "from BinaryDataImpl b where b.manual=true ")
    Slice<SimpleResource> getAllAsSimpleResources(Pageable pageable);


    @Query(value="select b.code from BinaryDataImpl b")
    List<String> getAllCodes();
}
