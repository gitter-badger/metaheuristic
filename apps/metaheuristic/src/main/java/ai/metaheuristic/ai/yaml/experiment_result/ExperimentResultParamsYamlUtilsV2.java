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

package ai.metaheuristic.ai.yaml.experiment_result;

import ai.metaheuristic.api.data.experiment_result.ExperimentResultParams;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsV2;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
public class ExperimentResultParamsYamlUtilsV2
        extends AbstractParamsYamlUtils<ExperimentResultParamsV2, ExperimentResultParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 2;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ExperimentResultParamsV2.class);
    }

    @NonNull
    @Override
    public ExperimentResultParams upgradeTo(@NonNull ExperimentResultParamsV2 src) {
        src.checkIntegrity();
        ExperimentResultParams trg = new ExperimentResultParams();
        trg.createdOn = src.createdOn;
        trg.code = src.code;
        trg.name = src.name;
        trg.description = src.description;
        trg.maxValueCalculated = src.maxValueCalculated;
        trg.numberOfTask = src.numberOfTask;

        trg.execContext = new ExperimentResultParams.ExecContextWithParams(src.execContext.execContextId, src.execContext.execContextParams);
        trg.hyperParams.addAll(src.hyperParams);
        src.features.stream().map(this::toFeature).collect(Collectors.toCollection(()->trg.features));
        src.taskFeatures.stream().map(this::toTaskFeature).collect(Collectors.toCollection(()->trg.taskFeatures));
        src.parts.stream().map(this::toParts).collect(Collectors.toCollection(()->trg.parts));

        trg.checkIntegrity();
        return trg;
    }

    private ExperimentResultParams.ExperimentPart toParts(ExperimentResultParamsV2.ExperimentPartV2 src) {
        ExperimentResultParams.ExperimentPart trg = new ExperimentResultParams.ExperimentPart();
        trg.taskContextId = src.taskContextId;
        trg.fitting = src.fitting;
        if (src.metrics!=null) {
            trg.metrics = new ExperimentResultParams.MetricValues(src.metrics.getValues());
        }
        trg.hyperParams = src.hyperParams;
        trg.featureVariables = src.featureVariables;
        return trg;
    }

    private ExperimentResultParams.ExperimentTaskFeature toTaskFeature(ExperimentResultParamsV2.ExperimentTaskFeatureV2 src) {
        ExperimentResultParams.ExperimentTaskFeature etf = new ExperimentResultParams.ExperimentTaskFeature();

        etf.id = src.id;
        etf.execContextId = src.execContextId;
        etf.taskId = src.taskId;
        etf.featureId = src.featureId;
        etf.taskType = src.taskType;
        etf.metrics = new ExperimentResultParams.MetricValues(src.metrics.values);

        return etf;
    }

    private ExperimentResultParams.ExperimentFeature toFeature(ExperimentResultParamsV2.ExperimentFeatureV2 src) {
        ExperimentResultParams.ExperimentFeature ef = new ExperimentResultParams.ExperimentFeature();
        ef.id = src.id;
        ef.variables = src.variables;
        ef.execStatus = src.execStatus;
        ef.experimentId = src.experimentId;
        ef.maxValues.putAll(src.maxValues);

        return ef;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
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
    public String toString(@NonNull ExperimentResultParamsV2 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public ExperimentResultParamsV2 to(@NonNull String s) {
        final ExperimentResultParamsV2 p = getYaml().load(s);
        return p;
    }

}
