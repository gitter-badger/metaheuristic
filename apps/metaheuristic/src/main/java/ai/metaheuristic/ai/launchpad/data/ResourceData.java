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

package ai.metaheuristic.ai.launchpad.data;

import ai.metaheuristic.api.data.BaseDataClass;
import ai.metaheuristic.ai.launchpad.launchpad_resource.SimpleVariable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Slice;

public class ResourceData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ResourcesResult extends BaseDataClass {
        public Slice<SimpleVariable> items;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @NoArgsConstructor
    public static class ResourceResult extends BaseDataClass {
        public SimpleVariable data;

        public ResourceResult(String errorMessage) {
            addErrorMessage(errorMessage);
        }

        public ResourceResult(SimpleVariable data) {
            this.data = data;
        }
    }

}
