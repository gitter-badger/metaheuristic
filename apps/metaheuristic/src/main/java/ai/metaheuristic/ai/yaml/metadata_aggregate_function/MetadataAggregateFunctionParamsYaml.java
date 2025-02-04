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

package ai.metaheuristic.ai.yaml.metadata_aggregate_function;

import ai.metaheuristic.api.data.BaseParams;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Serge
 * Date: 11/13/2021
 * Time: 5:09 PM
 */
@Data
@NoArgsConstructor
public class MetadataAggregateFunctionParamsYaml implements BaseParams {

    public final int version=1;

    /**
     * Map: key - fileName, value - varName
     */
    public final List<Map<String, String>> mapping = new ArrayList<>();

}
