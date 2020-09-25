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

package ai.metaheuristic.ai.dispatcher.internal_functions;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.ExecContextImpl;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextSyncService;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.dispatcher.data.InternalFunctionData.InternalFunctionProcessingResult;

/**
 * @author Serge
 * Date: 1/17/2020
 * Time: 9:47 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class InternalFunctionProcessor {

    private final ExecContextSyncService execContextSyncService;
    private final ExecContextCache execContextCache;
    public final List<InternalFunction> internalFunctions;

    private final Map<String, InternalFunction> internalFunctionMap = new HashMap<>();

    @PostConstruct
    public void postConstruct() {
        internalFunctions.forEach(o->internalFunctionMap.put(o.getCode(), o));
    }

    public boolean isRegistered(String functionCode) {
        return internalFunctionMap.containsKey(functionCode);
    }

    public InternalFunctionProcessingResult process(Long execContextId, Long taskId, String internalContextId, TaskParamsYaml taskParamsYaml) {
        execContextSyncService.checkWriteLockPresent(execContextId);

        InternalFunction internalFunction = internalFunctionMap.get(taskParamsYaml.task.function.code);
        if (internalFunction==null) {
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.function_not_found);
        }

        ExecContextImpl execContext = execContextCache.findById(execContextId);
        if (execContext==null) {
            String es = "#977.040 ExecContext #" + execContextId + " wasn't found.";
            log.error(es);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.exec_context_not_found, es);
        }
        ExecContextParamsYaml expy = execContext.getExecContextParamsYaml();
        try {
            // ! all output variables must be already created at this point
            return internalFunction.process(execContext.sourceCodeId, execContext.id, taskId, internalContextId, expy.variables, taskParamsYaml);
        } catch (Throwable th) {
            String es = "#977.060 system error while processing internal function '" + internalFunction.getCode() + "', error: " + th.getMessage();
            log.error(es, th);
            return new InternalFunctionProcessingResult(Enums.InternalFunctionProcessing.system_error, es);
        }
    }
}
