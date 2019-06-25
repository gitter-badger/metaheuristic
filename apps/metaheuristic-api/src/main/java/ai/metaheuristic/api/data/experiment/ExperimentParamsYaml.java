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

package ai.metaheuristic.api.data.experiment;

import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 10:03 PM
 */
@Data
@NoArgsConstructor
public class ExperimentParamsYaml implements BaseParams {

    @Data
    @NoArgsConstructor
    public static class HyperParam {
        public String key;
        public String values;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentYaml {
        public String name;
        public String description;
        public String code;

        public int seed = 42;
        public List<HyperParam> hyperParams = new ArrayList<>();

        public String fitSnippet;
        public String predictSnippet;
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentFeature {

        public Long id;
        public String resourceCodes;
        public String checksumIdCodes;
        public int execStatus;
        public Long experimentId;
        public Double maxValue;

        public String execStatusAsString() {
            switch(execStatus) {
                case 0:
                    return "Unknown";
                case 1:
                    return "Ok";
                case 2:
                    return "All are errors";
                case 3:
                    return "No sequenses";
                default:
                    return "Status is wrong";
            }
        }
    }

    @Data
    @NoArgsConstructor
    public static class ExperimentTaskFeature {
        public Long id;
        public Long workbookId;
        public Long taskId;
        public Long featureId;
        public int taskType;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperimentProcessing {
        public boolean isAllTaskProduced = false;
        public boolean isFeatureProduced = false;

        public long createdOn;
        public int numberOfTask = 0;

        public List<ExperimentFeature> features = new ArrayList<>();
        public List<ExperimentTaskFeature> taskFeatures = new ArrayList<>();
    }

    public ExperimentYaml yaml;
    public ExperimentProcessing processing;


}
