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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV4;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV5;
import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.api.launchpad.process.ProcessV4;
import ai.metaheuristic.api.launchpad.process.ProcessV5;
import ai.metaheuristic.api.launchpad.process.SnippetDefForPlanV5;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV4
        extends AbstractParamsYamlUtils<SourceCodeParamsYamlV4, SourceCodeParamsYamlV5, PlanParamsYamlUtilsV5, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 4;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(SourceCodeParamsYamlV4.class);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public SourceCodeParamsYamlV5 upgradeTo(SourceCodeParamsYamlV4 pV4, Long ... vars) {
        SourceCodeParamsYamlV5 p = new SourceCodeParamsYamlV5();
        p.internalParams = pV4.internalParams;
        p.planYaml = new SourceCodeParamsYamlV5.SourceCodeYamlV5();
        if (pV4.planYaml.metas!=null){
            p.planYaml.metas = new ArrayList<>(pV4.planYaml.metas);
        }
        p.planYaml.clean = pV4.planYaml.clean;
        p.planYaml.processes = pV4.planYaml.processes.stream().map( o-> {
            ProcessV5 pr = new ProcessV5();
            BeanUtils.copyProperties(o, pr, "snippets", "preSnippets", "postSnippets");
            pr.snippets = o.snippets!=null ? o.snippets.stream().map(d->new SnippetDefForPlanV5(d.code, d.params,d.paramsAsFile)).collect(Collectors.toList()) : null;
            pr.preSnippets = o.preSnippets!=null ? o.preSnippets.stream().map(d->new SnippetDefForPlanV5(d.code, d.params,d.paramsAsFile)).collect(Collectors.toList()) : null;
            pr.postSnippets = o.postSnippets!=null ? o.postSnippets.stream().map(d->new SnippetDefForPlanV5(d.code, d.params,d.paramsAsFile)).collect(Collectors.toList()) : null;
            if (pr.outputParams==null) {
                pr.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
            pr.outputParams.storageType = o.outputType;
            return pr;
        }).collect(Collectors.toList());
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
        String dateAsStr = date.format(formatter);

        String planCode = "p" + dateAsStr;
        if (!p.planYaml.processes.isEmpty()) {
            planCode += ("-" + p.planYaml.processes.get(0).code);
        }
        else {
            planCode += UUID.randomUUID().toString();
        }

        p.planYaml.planCode = StringUtils.truncate(planCode, 50);
        return p;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        // not supported
        return null;
    }

    @Override
    public PlanParamsYamlUtilsV5 nextUtil() {
        return (PlanParamsYamlUtilsV5) SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(5);
    }

    @Override
    public Void prevUtil() {
        // not supported
        return null;
    }

    @Override
    public String toString(SourceCodeParamsYamlV4 planYaml) {
        return getYaml().dump(planYaml);
    }

    @Override
    public SourceCodeParamsYamlV4 to(String s) {
        final SourceCodeParamsYamlV4 p = getYaml().load(s);
        if (p.planYaml ==null) {
            throw new IllegalStateException("#635.010 SourceCode Yaml is null");
        }

        // fix default values
        for (ProcessV4 process : p.planYaml.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        if (p.internalParams==null) {
            p.internalParams = new SourceCodeApiData.PlanInternalParamsYaml();
        }
        return p;
    }


}