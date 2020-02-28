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

package ai.metaheuristic.ai.yaml.atlas;

import ai.metaheuristic.api.data.atlas.AtlasParamsYaml;
import ai.metaheuristic.api.data.atlas.AtlasParamsYamlV1;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
@Service
@Profile("dispatcher")
@RequiredArgsConstructor
public class AtlasParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<AtlasParamsYamlV1, AtlasParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(AtlasParamsYamlV1.class);
    }

    @Override
    public AtlasParamsYaml upgradeTo(AtlasParamsYamlV1 src, Long ... vars) {
        src.checkIntegrity();
        AtlasParamsYaml trg = new AtlasParamsYaml();
        trg.createdOn = src.createdOn;
        trg.sourceCode = new AtlasParamsYaml.SourceCodeWithParams(src.sourceCode.sourceCodeId, src.sourceCode.sourceCodeParams);
        trg.execContext = new AtlasParamsYaml.ExecContextWithParams(src.execContext.execContextId, src.execContext.execContextParams, src.execContext.execState);
        trg.experiment = new AtlasParamsYaml.ExperimentWithParams(src.experiment.experimentId, src.experiment.experimentParams);
        trg.taskIds = src.taskIds;

        trg.checkIntegrity();
        return trg;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(AtlasParamsYamlV1 yaml) {
        return getYaml().dump(yaml);
    }

    @Override
    public AtlasParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final AtlasParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}
