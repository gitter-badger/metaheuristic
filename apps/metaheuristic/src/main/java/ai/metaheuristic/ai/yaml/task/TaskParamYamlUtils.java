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
package ai.metaheuristic.ai.yaml.task;

import ai.metaheuristic.api.v1.data.TaskApiData;
import ai.metaheuristic.commons.yaml.YamlUtils;
import org.yaml.snakeyaml.Yaml;

public class TaskParamYamlUtils {

    private static Yaml yaml;

    static {
        yaml = YamlUtils.init(TaskApiData.TaskParamYaml.class);
    }

    // TODO 2018.09.12. so, snakeYaml isn't thread-safe or it was a side-effect?
    private static final Object syncObj = new Object();

    public static String toString(TaskApiData.TaskParamYaml taskParamYaml) {
        synchronized (syncObj) {
            return yaml.dump(taskParamYaml);
        }
    }

    public static TaskApiData.TaskParamYaml toTaskYaml(String s) {
        synchronized (syncObj) {
            return yaml.load(s);
        }
    }


}