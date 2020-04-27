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

package ai.metaheuristic.ai.processor.variable_providers;

import ai.metaheuristic.ai.exceptions.BreakFromLambdaException;
import ai.metaheuristic.ai.processor.DispatcherLookupExtendedService;
import ai.metaheuristic.ai.processor.actors.DownloadVariableService;
import ai.metaheuristic.ai.processor.actors.UploadVariableService;
import ai.metaheuristic.ai.processor.tasks.DownloadVariableTask;
import ai.metaheuristic.ai.processor.tasks.UploadVariableTask;
import ai.metaheuristic.ai.utils.asset.AssetFile;
import ai.metaheuristic.ai.utils.asset.AssetUtils;
import ai.metaheuristic.ai.yaml.dispatcher_lookup.DispatcherLookupConfig;
import ai.metaheuristic.ai.yaml.metadata.Metadata;
import ai.metaheuristic.ai.yaml.processor_task.ProcessorTask;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.FunctionApiData;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYaml;
import ai.metaheuristic.commons.yaml.variable.VariableArrayParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static ai.metaheuristic.api.EnumsApi.DataSourcing;
import static ai.metaheuristic.api.EnumsApi.DataType;

@Service
@Slf4j
@Profile("processor")
@RequiredArgsConstructor
public class DispatcherVariableProvider implements VariableProvider {

    private final DownloadVariableService downloadVariableService;
    private final UploadVariableService uploadVariableService;

    @Override
    public DataSourcing getSourcing() {
        return DataSourcing.dispatcher;
    }

    @Override
    public List<AssetFile> prepareForDownloadingVariable(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, Metadata.DispatcherInfo dispatcherCode,
            TaskParamsYaml.InputVariable variable) {

        try {

            // process it only if the dispatcher has already sent its config
            if (dispatcher.context.chunkSize != null) {
                if (variable.context==EnumsApi.VariableContext.array) {
                    createDownloadTasksForArray(variable.id, task.getTaskId(), taskDir, dispatcher.context.chunkSize,
                            dispatcher.dispatcherLookup, dispatcherCode.processorId);
                }
                else {
                    DownloadVariableTask variableTask = new DownloadVariableTask(
                            variable.id, variable.context, task.getTaskId(), taskDir, dispatcher.context.chunkSize,
                            dispatcher.dispatcherLookup, dispatcherCode.processorId);
                    downloadVariableService.add(variableTask);
                }
            }
            String es;
            switch(variable.context) {
                case global:
                    return List.of(AssetUtils.prepareFileForVariable(taskDir, variable.id.toString(), null, DataType.global_variable));
                case local:
                    return List.of(AssetUtils.prepareFileForVariable(taskDir, variable.id.toString(), null, DataType.variable));
                case array:
                    return prepareArrayVariable(taskDir, variable);
                default:
                    es = "#810.007 Unknown context: " + variable.context+ ", variableId: " +  variable.id;
                    log.error(es);
                    throw new BreakFromLambdaException(es);
            }
        } catch (IOException e) {
            throw new BreakFromLambdaException(e);
        }
    }

    private void createDownloadTasksForArray(Long variableId, Long taskId, File taskDir, Long chunkSize,
                                             DispatcherLookupConfig.DispatcherLookup dispatcherLookup, String processorId) throws IOException {
        DownloadVariableTask task = new DownloadVariableTask(
                variableId, EnumsApi.VariableContext.local, taskId, taskDir, chunkSize, dispatcherLookup, processorId);
        downloadVariableService.add(task);

        AssetFile assetFile = AssetUtils.prepareFileForVariable(taskDir, variableId.toString(), null, DataType.variable);

        List<VariableArrayParamsYaml.Variable> variables = getVariablesForArray(assetFile);

        for (VariableArrayParamsYaml.Variable v : variables) {
            DownloadVariableTask task1 = new DownloadVariableTask(
                    v.id, EnumsApi.VariableContext.local, taskId, taskDir, chunkSize, dispatcherLookup, processorId);
            downloadVariableService.add(task1);
        }
    }

    private List<AssetFile> prepareArrayVariable(File taskDir, TaskParamsYaml.InputVariable variable) throws IOException {
        AssetFile assetFile = AssetUtils.prepareFileForVariable(taskDir, variable.id.toString(), null, DataType.variable);
        List<AssetFile> assetFiles = new ArrayList<>();
        assetFiles.add(assetFile);
        List<VariableArrayParamsYaml.Variable> variables = getVariablesForArray(assetFile);

        for (VariableArrayParamsYaml.Variable v : variables) {
            AssetFile af = AssetUtils.prepareFileForVariable(taskDir, v.id.toString(), null, v.type);
            assetFiles.add(af);
        }
        return assetFiles;
    }

    @NonNull
    private List<VariableArrayParamsYaml.Variable> getVariablesForArray(AssetFile assetFile) throws IOException {
        List<VariableArrayParamsYaml.Variable> variables = new ArrayList<>();
        if (assetFile.isContent && !assetFile.isError) {
            String data = FileUtils.readFileToString(assetFile.file, StandardCharsets.UTF_8);
            VariableArrayParamsYaml vapy = VariableArrayParamsYamlUtils.BASE_YAML_UTILS.to(data);
            variables.addAll(vapy.array);
        }
        return variables;
    }

    @Override
    public FunctionApiData.SystemExecResult processOutputVariable(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, Metadata.DispatcherInfo dispatcherCode,
            Long outputVariableId, TaskParamsYaml.FunctionConfig functionConfig) {
        File outputVariableFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + outputVariableId);
        if (outputVariableFile.exists()) {
            log.info("Register task for uploading result data to server, resultDataFile: {}", outputVariableFile.getPath());
            UploadVariableTask uploadVariableTask = new UploadVariableTask(task.taskId, outputVariableFile, outputVariableId);
            uploadVariableTask.dispatcher = dispatcher.dispatcherLookup;
            uploadVariableTask.processorId = dispatcherCode.processorId;
            uploadVariableService.add(uploadVariableTask);
        } else {
            String es = "Result data file doesn't exist, resultDataFile: " + outputVariableFile.getPath();
            log.error(es);
            return new FunctionApiData.SystemExecResult(functionConfig.code,false, -1, es);
        }
        return null;
    }

    @Override
    public File getOutputVariableFromFile(
            File taskDir, DispatcherLookupExtendedService.DispatcherLookupExtended dispatcher,
            ProcessorTask task, TaskParamsYaml.OutputVariable variable) {

        File resultDataFile = new File(taskDir, ConstsApi.ARTIFACTS_DIR + File.separatorChar + variable.id);
        return resultDataFile;
    }
}