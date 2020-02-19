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

package ai.metaheuristic.ai.yaml.source_code;

import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV1;
import ai.metaheuristic.commons.exceptions.DowngradeNotSupportedException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * @author Serge
 */
public class SourceCodeParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<SourceCodeParamsYamlV1, SourceCodeParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV1.class);
    }

    @Override
    public SourceCodeParamsYaml upgradeTo(SourceCodeParamsYamlV1 v1, Long ... vars) {
        v1.checkIntegrity();
        SourceCodeParamsYaml p = new SourceCodeParamsYaml();
        p.source = new SourceCodeParamsYaml.SourceCodeYaml();
        if (v1.source.metas!=null){
            p.source.metas = new ArrayList<>(v1.source.metas);
        }
        if (v1.source.variables!=null) {
            p.source.variables = new SourceCodeParamsYaml.VariableDefinition(v1.source.variables.global, v1.source.variables.runtime);
            v1.source.variables.inline.forEach(p.source.variables.inline::put);
        }
        p.source.clean = v1.source.clean;
        p.source.processes = v1.source.processes.stream().map(SourceCodeParamsYamlUtilsV1::toProcess).collect(Collectors.toList());

        p.source.uid = v1.source.uid;
        if (v1.source.ac!=null) {
            p.source.ac = new SourceCodeParamsYaml.AccessControl(v1.source.ac.groups);
        }
        p.checkIntegrity();
        return p;
    }

    @NonNull
    private static SourceCodeParamsYaml.Process toProcess(SourceCodeParamsYamlV1.ProcessV1 o) {
        SourceCodeParamsYaml.Process pr = new SourceCodeParamsYaml.Process();
        pr.name = o.name;
        pr.code = o.code;
        pr.timeoutBeforeTerminate = o.timeoutBeforeTerminate;
        o.input.stream().map(v->new SourceCodeParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name)).forEach(pr.input::add);
        o.output.stream().map(v->new SourceCodeParamsYaml.Variable(v.sourcing, v.git, v.disk, v.name)).forEach(pr.output::add);
        pr.snippet = o.snippet!=null ? new SourceCodeParamsYaml.SnippetDefForSourceCode(o.snippet.code, o.snippet.params, o.snippet.context) : null;
        pr.preSnippets = o.preSnippets!=null ? o.preSnippets.stream().map(d->new SourceCodeParamsYaml.SnippetDefForSourceCode(d.code, d.params, d.context)).collect(Collectors.toList()) : null;
        pr.postSnippets = o.postSnippets!=null ? o.postSnippets.stream().map(d->new SourceCodeParamsYaml.SnippetDefForSourceCode(d.code, d.params, d.context)).collect(Collectors.toList()) : null;
        pr.metas = o.metas;

        pr.subProcesses = o.subProcesses!=null
                ?  new SourceCodeParamsYaml.SubProcesses(
                    o.subProcesses.logic, o.subProcesses.processes.stream().map(SourceCodeParamsYamlUtilsV1::toProcess).collect(Collectors.toList()) )
                : null;

        return pr;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // not supported
        throw new DowngradeNotSupportedException();
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(SourceCodeParamsYamlV1 sourceCodeParamsYaml) {
        return getYaml().dump(sourceCodeParamsYaml);
    }

    @Override
    public SourceCodeParamsYamlV1 to(String s) {
        final SourceCodeParamsYamlV1 p = getYaml().load(s);
        if (p.source ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }
        return p;
    }


}