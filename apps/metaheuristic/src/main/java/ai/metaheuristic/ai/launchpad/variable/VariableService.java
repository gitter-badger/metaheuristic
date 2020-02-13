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

package ai.metaheuristic.ai.launchpad.variable;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.BinaryDataNotFoundException;
import ai.metaheuristic.ai.exceptions.VariableSavingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileException;
import ai.metaheuristic.ai.launchpad.beans.Variable;
import ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleVariable;
import ai.metaheuristic.ai.launchpad.repositories.VariableRepository;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;

@Service
@Transactional
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class VariableService {

    private final EntityManager em;
    private final VariableRepository variableRepository;
    private final Globals globals;

    @Transactional(readOnly = true)
    public Variable getBinaryData(Long id) {
        if (!globals.isUnitTesting) {
            throw new IllegalStateException("this method intended to be only for test cases");
        }
        return getBinaryData(id, true);
    }

    private Variable getBinaryData(Long id, @SuppressWarnings("SameParameterValue") boolean isInitBytes) {
        try {
            Variable data = variableRepository.findById(id).orElse(null);
            if (data==null) {
                return null;
            }
            if (isInitBytes) {
                data.bytes = data.getData().getBytes(1, (int) data.getData().length());
            }
            return data;
        } catch (SQLException e) {
            throw new IllegalStateException("SQL error", e);
        }
    }

    public void storeToFile(String id, File trgFile) {
        try {
            Blob blob = variableRepository.getDataAsStreamByCode(Long.valueOf(id));
            if (blob==null) {
                log.warn("#087.010 Binary data for id {} wasn't found", id);
                throw new BinaryDataNotFoundException("#087.010 Binary data wasn't found, code: " + id);
            }
            try (InputStream is = blob.getBinaryStream()) {
                FileUtils.copyInputStreamToFile(is, trgFile);
            }
        } catch (BinaryDataNotFoundException e) {
            throw e;
        } catch (Exception e) {
            String es = "#087.020 Error while storing binary data";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
    }

    public void deleteByExecContextId(Long execContextId) {
        variableRepository.deleteByExecContextId(execContextId);
    }

    @Transactional(readOnly = true)
    public List<SimpleVariableAndStorageUrl> getIdInVariables(List<String> variables, Long workbookId) {
        if (variables.isEmpty()) {
            return List.of();
        }
        return variableRepository.getIdAndStorageUrlInVarsForWorkbook(variables, workbookId);
    }

    @Transactional(readOnly = true)
    public List<SimpleVariableAndStorageUrl> getIdInVariables(List<String> variables) {
        if (variables.isEmpty()) {
            return List.of();
        }
        return variableRepository.getIdAndStorageUrlInVars(variables);
    }

    public void deleteByVariable(String variable) {
        variableRepository.deleteByName(variable);
    }

    public Variable save(InputStream is, long size, String variable, String filename, Long workbookId, String contextId) {
        try {
            Variable data = new Variable();
            data.inited = true;
            data.setName(variable);
            data.setFilename(filename);
            data.setWorkbookId(workbookId);
            data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.launchpad)));
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setContextId(contextId);

            Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
            data.setData(blob);

            variableRepository.save(data);

            return data;
        }
        catch(VariableSavingException | PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            throw new VariableSavingException("#087.070 error storing data to db - " + th.getMessage(), th);
        }
    }

    public Variable createUninitialized(String variable, Long workbookId, String contextId) {
        try {
            Variable data = new Variable();
            data.inited = false;
            data.setName(variable);
            data.setWorkbookId(workbookId);
            // TODO right now only DataSourcing.launchpad is supporting as internal variable.
            //  the code has to be added for another type of sourcing
            data.setParams(DataStorageParamsUtils.toString(new DataStorageParams(DataSourcing.launchpad)));
            data.setUploadTs(new Timestamp(System.currentTimeMillis()));
            data.setContextId(contextId);
            variableRepository.save(data);
            return data;
        }
        catch(PessimisticLockingFailureException e) {
            throw e;
        } catch(Throwable th) {
            throw new VariableSavingException("#087.070 error storing data to db - " + th.getMessage(), th);
        }
    }

    public void update(InputStream is, long size, Variable data) {
        data.setUploadTs(new Timestamp(System.currentTimeMillis()));

        Blob blob = Hibernate.getLobCreator(em.unwrap(Session.class)).createBlob(is, size);
        data.setData(blob);
        data.inited = true;

        variableRepository.save(data);
    }

    public Page<Variable> findAll(Pageable pageable) {
        return variableRepository.findAll(pageable);
    }

    public void deleteById(Long id) {
        variableRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Variable> findById(Long id) {
        return variableRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Slice<SimpleVariable> getAllAsSimpleResources(Pageable pageable) {
        return variableRepository.getAllAsSimpleResources(pageable);
    }

    @Transactional(readOnly = true)
    public List<String> getFilenameByVariableAndExecContextId(String variable, Long execContextId) {
        return variableRepository.findFilenameByVariableAndWorkbookId(variable, execContextId);
    }

    @Transactional(readOnly = true)
    public List<String> findFilenameByBatchId(Long batchId, Long workbookId) {
        if (true) {
            throw new NotImplementedException("Need to re-write");
        }
        return variableRepository.findFilenameByVariableAndWorkbookId(batchId.toString(), workbookId);
    }

    @Transactional(readOnly = true)
    public List<Object[]> getFilenamesForBatchIds(List<Long> batchIds) {
        if (batchIds.isEmpty()) {
            return List.of();
        }
        if (true) {
            throw new NotImplementedException("Need to re-write");
        }
        return variableRepository.getFilenamesForBatchIds(batchIds);
    }

    public Variable storeInitialResource(File tempFile, String variable, String filename, Long workbookId, String contextId) {
        try {
            try (InputStream is = new FileInputStream(tempFile)) {
                return save(is, tempFile.length(), variable, filename, workbookId, contextId);
            }
        } catch (IOException e) {
            log.error("Error", e);
            throw new StoreNewFileException("Error while storing", e, tempFile.getPath(), filename);
        }
    }}
