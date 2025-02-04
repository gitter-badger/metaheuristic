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

package ai.metaheuristic.ai.dispatcher.variable;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.cache.CacheVariableService;
import ai.metaheuristic.ai.dispatcher.data.VariableData;
import ai.metaheuristic.ai.dispatcher.event.ResourceCloseTxEvent;
import ai.metaheuristic.ai.exceptions.CommonErrorWithDataException;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Serge
 * Date: 12/22/2021
 * Time: 10:45 PM
 */

// https://stackoverflow.com/questions/35252262/spring-boot-configuration-skip-registration-on-multiple-profile/59631429#59631429
@Service
@Slf4j
@Profile({"dispatcher & !mysql & !postgresql"})
@RequiredArgsConstructor
public class VariableDefaultDatabaseService implements VariableDatabaseSpecificService {

    private final Globals globals;
    private final VariableService variableService;
    private final CacheVariableService cacheVariableService;
    private final ApplicationEventPublisher eventPublisher;

    public void copyData(VariableData.StoredVariable srcVariable, TaskParamsYaml.OutputVariable targetVariable) throws IOException {
        TxUtils.checkTxExists();

        final File tempFile = File.createTempFile("var-" +srcVariable.id + "-", ".bin", globals.dispatcherTempDir);
        InputStream is;
        try {
            // TODO 2021-10-14 right now, an array variable isn't supported
            cacheVariableService.storeToFile(srcVariable.id, tempFile);
            is = new FileInputStream(tempFile);
        } catch (CommonErrorWithDataException e) {
            eventPublisher.publishEvent(new ResourceCloseTxEvent(tempFile));
            throw e;
        } catch (Exception e) {
            eventPublisher.publishEvent(new ResourceCloseTxEvent(tempFile));
            String es = "#173.040 Error while storing data to file";
            log.error(es, e);
            throw new IllegalStateException(es, e);
        }
        eventPublisher.publishEvent(new ResourceCloseTxEvent(is, tempFile));
        variableService.storeData(is, tempFile.length(), targetVariable.id, targetVariable.filename);
    }

}
