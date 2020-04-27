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

package ai.metaheuristic.ai.dispatcher.data;

import ai.metaheuristic.ai.dispatcher.experiment_result.ExperimentResultSimple;
import ai.metaheuristic.ai.dispatcher.beans.ExperimentResult;
import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.api.data.SimpleSelectOption;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultTaskParamsYaml;
import ai.metaheuristic.api.data.experiment.BaseMetricElement;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ExperimentResultData {

    public static final PlotData EMPTY_PLOT_DATA = new PlotData();

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class PlotData extends BaseDataClass {
        public List<String> x = new ArrayList<>();
        public List<String> y = new ArrayList<>();
        public BigDecimal[][] z;

        public PlotData(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    public static class ExperimentInfo {
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentApiData.ExperimentFeatureData> features;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentInfoExtended extends BaseDataClass {
        public ExperimentResult experimentResult;
        public ExperimentApiData.ExperimentData experiment;
        public ExperimentInfo experimentInfo;

        public ExperimentInfoExtended(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentDataOnly extends BaseDataClass {
        public Long experimentResultId;
        public ExperimentApiData.ExperimentData experiment;

        public ExperimentDataOnly(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentResultExperiments extends BaseDataClass {
        public Slice<ExperimentResult> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ExperimentResultSimpleList extends BaseDataClass {
        public Slice<ExperimentResultSimple> items;
    }

    @Data
    public static class MetricsResult {
        public final LinkedHashSet<String> metricNames = new LinkedHashSet<>();
        public final List<MetricElement> metrics = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricElement implements BaseMetricElement {
        public final List<BigDecimal> values = new ArrayList<>();
        public String params;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamElement {
        String param;
        boolean isSelected;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyperParamList {
        String key;
        public final List<HyperParamElement> list = new ArrayList<>();
        public boolean isSelectable() {
            return list.size()>1;
        }
    }

    @Data
    public static class HyperParamResult {
        public final List<HyperParamList> elements = new ArrayList<>();

        /**
         * for plotting we need at least 2 InlineVariable to be selected.
         * in case when there is only one list ov values of params
         * we will use all InlineVariable for axises
         */
        public boolean useAllHyperParamsInPlot() {
            int count=0;
            for (HyperParamList element : elements) {
                if (element.list.size()>1) {
                    count++;
                }
            }
            return count<2;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ConsoleResult extends BaseDataClass {
        public int exitCode;
        public boolean isOk;
        public String console;

        public ConsoleResult(int exitCode, boolean isOk, String console) {
            this.exitCode = exitCode;
            this.isOk = isOk;
            this.console = console;
        }

        public ConsoleResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class TasksResult extends BaseDataClass {
        public Slice<ExperimentResultTaskParamsYaml> items;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ExperimentFeatureExtendedResult extends BaseDataClass {
        public MetricsResult metricsResult;
        public HyperParamResult hyperParamResult;
        public Slice<ExperimentResultTaskParamsYaml> tasks;
        public ExperimentApiData.ExperimentFeatureData experimentFeature;
        public ConsoleResult consoleResult;

        public ExperimentFeatureExtendedResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }
    }

}