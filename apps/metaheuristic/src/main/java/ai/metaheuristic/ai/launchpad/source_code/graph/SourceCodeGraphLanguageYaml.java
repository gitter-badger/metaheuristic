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

package ai.metaheuristic.ai.launchpad.source_code.graph;

import ai.metaheuristic.ai.exceptions.SourceCodeGraphException;
import ai.metaheuristic.ai.launchpad.data.SourceCodeData;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 2/14/2020
 * Time: 10:49 PM
 */
public class SourceCodeGraphLanguageYaml implements SourceCodeGraphLanguage {

    @Override
    public SourceCodeData.SourceCodeGraph parse(String sourceCode, Supplier<String> contextIdSupplier) {

        SourceCodeParamsYaml sourceCodeParams = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode);

        SourceCodeData.SourceCodeGraph scg = new SourceCodeData.SourceCodeGraph();
        scg.clean = sourceCodeParams.source.clean;


        AtomicLong taskId = new AtomicLong();
        long internalContextId = 1L;
        List<Long> parentIds =  List.of();
        List<Long> prevParentIds =  new ArrayList<>();

        String currentInternalContextId = "" + internalContextId;
        for (SourceCodeParamsYaml.Process p : sourceCodeParams.source.getProcesses()) {
            SourceCodeData.SimpleTaskVertex v = toVertex(contextIdSupplier, taskId, prevParentIds, currentInternalContextId, p);

            SourceCodeGraphUtils.addNewTasksToGraph(scg, v, parentIds);

            SourceCodeParamsYaml.SubProcesses subProcesses = p.subProcesses;
            if (subProcesses !=null) {
                if (CollectionUtils.isEmpty(subProcesses.processes)) {
                    throw new SourceCodeGraphException();
                }
                List<Long> prevIds = new ArrayList<>();
                prevIds.add(v.taskId);
                String subInternalContextId = null;
                if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                    subInternalContextId = currentInternalContextId + ',' + contextIdSupplier.get();
                }
                for (SourceCodeParamsYaml.Process subP : subProcesses.processes) {
                    if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.and || subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                        subInternalContextId = currentInternalContextId + ',' + contextIdSupplier.get();
                    }
                    if (subInternalContextId==null) {
                        throw new IllegalStateException("(subInternalContextId==null)");
                    }
                    SourceCodeData.SimpleTaskVertex subV = toVertex(contextIdSupplier, taskId, prevIds, subInternalContextId, subP);
                    if (subProcesses.logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                        prevIds.clear();
                        prevIds.add(subV.taskId);
                    }
                }
            }
        }
        return scg;
    }

    private SourceCodeData.SimpleTaskVertex toVertex(Supplier<String> contextIdSupplier, AtomicLong taskId, List<Long> prevParentIds, String currentInternalContextId, SourceCodeParamsYaml.Process p) {
        //            public String name;
//            public String code;
//            public SourceCodeParamsYaml.SnippetDefForSourceCode snippet;
//            public List<SourceCodeParamsYaml.SnippetDefForSourceCode> preSnippets;
//            public List<SourceCodeParamsYaml.SnippetDefForSourceCode> postSnippets;
//
//            /**
//             * Timeout before terminating a process with snippet
//             * value in seconds
//             * null or 0 mean the infinite execution
//             */
//            public Long timeoutBeforeTerminate;
//            public final List<SourceCodeParamsYaml.Variable> input = new ArrayList<>();
//            public final List<SourceCodeParamsYaml.Variable> output = new ArrayList<>();
//            public List<Meta> metas = new ArrayList<>();
//            public SourceCodeParamsYaml.SubProcesses subProcesses;

        SourceCodeData.SimpleTaskVertex v = new SourceCodeData.SimpleTaskVertex();
        v.snippet = p.snippet;
        v.preSnippets = p.preSnippets;
        v.postSnippets = p.postSnippets;
        v.taskId = taskId.incrementAndGet();
        prevParentIds.add(v.taskId);

        v.execContextId = contextIdSupplier.get();
        v.internalContextId = currentInternalContextId;

        v.processName = p.name;
        v.processCode = p.code;
        v.timeoutBeforeTerminate = p.timeoutBeforeTerminate;
        p.input.stream().map(o->o.name).forEach(v.input::add);
        p.output.stream().map(o->o.name).forEach(v.output::add);
        v.metas = p.metas;
        return v;
    }

}
