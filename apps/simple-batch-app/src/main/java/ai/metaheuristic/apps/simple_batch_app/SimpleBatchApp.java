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
package ai.metaheuristic.apps.simple_batch_app;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYaml;
import ai.metaheuristic.commons.yaml.task_file.TaskFileParamsYamlUtils;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@Slf4j
public class SimpleBatchApp implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(SimpleBatchApp.class, args);
    }

    @Override
    public void run(String... args) throws IOException, InterruptedException {
        if (args.length==0) {
            System.out.println("Parameter file wasn't specified");
            System.exit(-1);
        }
        System.out.println("args = " + Arrays.toString(args));

        // sleep for testing timeoutBeforeTerminate
        System.out.println("Start timeout...");
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
        System.out.println("Timeout ended.");

        if (args.length>1 ) {
            String message = "Just for test an error reporting. "+ CommonConsts.MULTI_LANG_STRING;
            log.error(message);
            throw new RuntimeException(message);
        }

        File yamlFile = new File(args[0]);
        String config = FileUtils.readFileToString(yamlFile, "utf-8");
        System.out.println("Yaml config file:\n"+config);

        TaskFileParamsYaml params = TaskFileParamsYamlUtils.BASE_YAML_UTILS.to(config);

        if (params.task.inputs.size()!=1) {
            throw new RuntimeException("Too many input variables");
        }

        TaskFileParamsYaml.InputVariable arrayVariable = params.task.inputs.get(0);

        File arrayVariableFile = Path.of(
                params.task.workingPath, arrayVariable.dataType.toString(), arrayVariable.id).toFile();

        String arrayVariableContent = FileUtils.readFileToString(arrayVariableFile, StandardCharsets.UTF_8);
        System.out.println("input array variable:\n" + arrayVariableContent+"\n");

        VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(
                arrayVariableContent);

        VariableArrayParamsYaml.Variable variable = vapy.array.get(0);
        File sourceFile = Path.of(params.task.workingPath, variable.dataType.toString(), variable.id).toFile();


        String processedFilename = getOutputFileForType(params, "batch-item-processed-file");
        String processingStatusFilename = getOutputFileForType(params, "batch-item-processing-status");
        String mappingFilename = getOutputFileForType(params, "batch-item-mapping");

        System.out.println("processedFilename: " + processedFilename);
        System.out.println("processingStatusFilename: " + processingStatusFilename);
        System.out.println("mappingFilename: " + mappingFilename);

        File artifactDir = Path.of(params.task.workingPath, ConstsApi.ARTIFACTS_DIR).toFile();

        File processedFile = new File(artifactDir, processedFilename);
        File processingStatusFile = new File(artifactDir, processingStatusFilename);
        File mappingFile = new File(artifactDir, mappingFilename);

        FileUtils.copyFile(sourceFile, processedFile);
        FileUtils.write(processingStatusFile, "File "+variable.realName+" was processed successfully", StandardCharsets.UTF_8);
    }

    public String getOutputFileForType(TaskFileParamsYaml params, String type) {
        return params.task.outputs
                    .stream()
                    .filter(o-> type.equals(o.type))
                    .map(o->o.id.toString())
                    .findFirst()
                    .orElseThrow();
    }

}