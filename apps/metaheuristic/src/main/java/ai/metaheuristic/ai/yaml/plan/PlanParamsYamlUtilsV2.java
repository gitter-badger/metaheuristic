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

package ai.metaheuristic.ai.yaml.plan;

import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.data_storage.DataStorageParams;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV2;
import ai.metaheuristic.api.v1.launchpad.process.ProcessV3;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.api.v1.data.PlanApiData.PlanParamsYamlV2;
import static ai.metaheuristic.api.v1.data.PlanApiData.PlanParamsYamlV3;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 12:10 AM
 */
public class PlanParamsYamlUtilsV2
        extends AbstractPlanParamsYamlUtils<PlanParamsYamlV2, PlanParamsYamlV3, PlanParamsYamlUtilsV3> {

    @Override
    public int getVersion() {
        return 2;
    }

    public Yaml getYaml() {
        return YamlUtils.init(PlanParamsYamlV2.class);
    }

    @Override
    public PlanParamsYamlV3 upgradeTo(PlanParamsYamlV2 yaml) {
        PlanApiData.PlanParamsYamlV3 p = new PlanApiData.PlanParamsYamlV3();
        p.version = 3;
        p.internalParams = yaml.internalParams;
        p.planYaml.clean = yaml.planYaml.clean;
        p.planYaml = new PlanApiData.PlanYamlV3();
        p.planYaml.processes = yaml.planYaml.processes
                .stream()
                .map(o->{
                    ProcessV3 pV3 = new ProcessV3();
                    BeanUtils.copyProperties(o, pV3, "preSnippetCode", "postSnippetCode");
                    if (StringUtils.isNotBlank(o.preSnippetCode)) {
                        pV3.preSnippetCode = List.of(o.preSnippetCode);
                    }
                    if (StringUtils.isNotBlank(o.postSnippetCode)) {
                        pV3.postSnippetCode = List.of(o.postSnippetCode);
                    }
                    return pV3;
                })
                .collect(Collectors.toList());
        return p;
    }

    @Override
    public PlanParamsYamlUtilsV3 nextUtil() {
        return null;
    }

    public String toString(PlanParamsYamlV2 planYaml) {
        return getYaml().dump(planYaml);
    }

    public PlanParamsYamlV2 to(String s) {
        final PlanParamsYamlV2 p = getYaml().load(s);
        for (ProcessV2 process : p.planYaml.processes) {
            if (process.outputParams==null) {
                process.outputParams = new DataStorageParams(EnumsApi.DataSourcing.launchpad);
            }
        }
        return p;
    }


}
