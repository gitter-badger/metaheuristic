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

package ai.metaheuristic.ai.yaml.versioning;

import ai.metaheuristic.api.v1.data.BaseParams;
import ai.metaheuristic.api.v1.data.YamlVersion;

import java.util.Map;

/**
 * @author Serge
 * Date: 6/17/2019
 * Time: 9:58 PM
 */
public class BaseYamlUtils<T > {

    private final ParamsYamlUtilsFactory FACTORY = new ParamsYamlUtilsFactory();

    public BaseYamlUtils(Map<Integer, AbstractParamsYamlUtils> map, AbstractParamsYamlUtils defYamlUtils) {
        FACTORY.map = map;
        FACTORY.defYamlUtils = defYamlUtils;
    }

    public AbstractParamsYamlUtils getForVersion(int version) {
        return FACTORY.getForVersion(version);
    }

    public AbstractParamsYamlUtils getDefault() {
        return FACTORY.getDefault();
    }

    public String toString(BaseParams planYaml) {
//        return getDefault().getYaml().dumpAs(planYaml, Tag.MAP, DumperOptions.FlowStyle.BLOCK);
        return getDefault().getYaml().dumpAsMap(planYaml);
    }

    public T to(String s) {
        YamlVersion v = YamlForVersioning.getYamlForVersion().load(s);
        AbstractParamsYamlUtils yamlUtils;
        if (v.version==null) {
            yamlUtils = getForVersion(1);
        }
        else {
            yamlUtils = getForVersion(v.version);
        }
        Object currPlanParamsYaml = yamlUtils.to(s);
        do {
            //noinspection unchecked
            currPlanParamsYaml = yamlUtils.upgradeTo(currPlanParamsYaml);
        } while ((yamlUtils=(AbstractParamsYamlUtils)yamlUtils.nextUtil())!=null);

        T p = (T)currPlanParamsYaml;

        return p;
    }


}