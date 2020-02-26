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

package ai.metaheuristic.commons.yaml.task_file;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.beans.BeanUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 8/08/2019
 * Time: 12:10 AM
 */
public class TaskFileParamsYamlUtilsV1
        extends AbstractParamsYamlUtils<TaskFileParamsYamlV1, TaskFileParamsYaml, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(TaskFileParamsYamlV1.class);
    }

    @Override
    public TaskFileParamsYaml upgradeTo(TaskFileParamsYamlV1 v1, Long ... vars) {
        v1.checkIntegrity();
        TaskFileParamsYaml t = new TaskFileParamsYaml();
        t.task = new TaskFileParamsYaml.Task();
        BeanUtils.copyProperties(v1.task, t.task, "inline", "input", "output");
        t.task.inline = v1.task.inline;
        t.task.input = v1.task.input!=null ? v1.task.input.stream().map(TaskFileParamsYamlUtilsV1::upInputVariable).collect(Collectors.toList()) : null;
        t.task.output = v1.task.output!=null ? v1.task.output.stream().map(TaskFileParamsYamlUtilsV1::upOutputVariable).collect(Collectors.toList()) : null;

        t.checkIntegrity();
        return t;
    }

    private static TaskFileParamsYaml.InputVariable upInputVariable(TaskFileParamsYamlV1.InputVariableV1 v1) {
        TaskFileParamsYaml.InputVariable v = new TaskFileParamsYaml.InputVariable();
        v.disk = v1.disk;
        v.git = v1.git;
        v.name = v1.name;
        v.sourcing = v1.sourcing;
        v.resources = v1.resources!=null ? v1.resources.stream().map(r->new TaskFileParamsYaml.Resource(r.id, r.context, r.realName)).collect(Collectors.toList()) : null;
        return v;
    }

    private static TaskFileParamsYaml.OutputVariable upOutputVariable(TaskFileParamsYamlV1.OutputVariableV1 v1) {
        TaskFileParamsYaml.OutputVariable v = new TaskFileParamsYaml.OutputVariable();
        v.disk = v1.disk;
        v.git = v1.git;
        v.name = v1.name;
        v.sourcing = v1.sourcing;
        v.resources = v1.resources!=null ? new TaskFileParamsYaml.Resource(v1.resources.id, v1.resources.context, v1.resources.realName) : null;
        return v;
    }

    @Override
    public Void downgradeTo(Void yaml) {
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
    public String toString(TaskFileParamsYamlV1 params) {
        return getYaml().dump(params);
    }

    @Override
    public TaskFileParamsYamlV1 to(String s) {
        //noinspection UnnecessaryLocalVariable
        final TaskFileParamsYamlV1 p = getYaml().load(s);
        return p;
    }

}