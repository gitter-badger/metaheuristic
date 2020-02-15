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
import ai.metaheuristic.commons.yaml.versioning.BaseYamlUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * @author Serge
 * Date: 6/22/2019
 * Time: 11:36 PM
 */
@Service
@Profile("launchpad")
@RequiredArgsConstructor
public class AtlasParamsYamlUtils {

    private static final AtlasParamsYamlUtilsV1 YAML_UTILS_V_1 = new AtlasParamsYamlUtilsV1();
    private static final AtlasParamsYamlUtilsV1 DEFAULT_UTILS = YAML_UTILS_V_1;

    public BaseYamlUtils<AtlasParamsYaml> BASE_YAML_UTILS;

    @PostConstruct
    private void postConstruct() {
        BASE_YAML_UTILS = new BaseYamlUtils<>(
                Map.of(
                        1, YAML_UTILS_V_1
                ),
                DEFAULT_UTILS
        );
    }
}
