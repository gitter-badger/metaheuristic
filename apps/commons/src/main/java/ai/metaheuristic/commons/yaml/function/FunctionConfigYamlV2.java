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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.sourcing.GitInfo;
import lombok.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/3/2019
 * Time: 4:53 PM
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "code")
public class FunctionConfigYamlV2 implements Cloneable, BaseParams {

    public final int version=2;

    @Override
    public boolean checkIntegrity() {
        return true;
    }

    @SneakyThrows
    public FunctionConfigYamlV2 clone() {
        final FunctionConfigYamlV2 clone = (FunctionConfigYamlV2) super.clone();
        if (this.checksumMap != null) {
            clone.checksumMap = new HashMap<>(this.checksumMap);
        }
        if (this.metas != null) {
            clone.metas = new ArrayList<>();
            for (Meta meta : this.metas) {
                clone.metas.add(new Meta(meta.key, meta.value, meta.ext));
            }
        }
        return clone;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FunctionInfoV2 {
        public boolean signed;
        /**
         * function's binary length
         */
        public long length;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MachineLearningV2 {
        public boolean metrics = false;
        public boolean fitting = false;
    }

    /**
     * code of function, i.e. simple-app:1.0
     */
    public String code;
    public String type;
    public String file;
    /**
     * params for command line for invoking function
     * <p>
     * this isn't a holder for yaml-based config
     */
    public String params;
    public String env;
    public EnumsApi.FunctionSourcing sourcing;
    public Map<EnumsApi.Type, String> checksumMap;
    public FunctionInfoV2 info = new FunctionInfoV2();
    public String checksum;
    public GitInfo git;
    public boolean skipParams = false;
    public List<Meta> metas = new ArrayList<>();
    public MachineLearningV2 ml;

}