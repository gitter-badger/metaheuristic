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

package ai.metaheuristic.ai.launchpad.variable;

import ai.metaheuristic.api.data_storage.DataStorageParams;
import ai.metaheuristic.ai.yaml.data_storage.DataStorageParamsUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleVariableAndStorageUrl {
    public String id;
    public String variable;
    public String storageUrl;
    public String originalFilename;

    public SimpleVariableAndStorageUrl(Long id, String variable, String storageUrl, String originalFilename) {
        this.id = id.toString();
        this.variable = variable;
        this.storageUrl = storageUrl;
        this.originalFilename = originalFilename;
    }

    public DataStorageParams getParams() {
        return DataStorageParamsUtils.to(storageUrl);
    }
}