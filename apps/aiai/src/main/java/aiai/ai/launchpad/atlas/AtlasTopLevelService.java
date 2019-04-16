/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.launchpad.atlas;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.core.ExecProcessService;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.data.AtlasData;
import aiai.ai.launchpad.data.ExperimentData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.data.TasksData;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.experiment.task.TaskWIthType;
import aiai.ai.launchpad.repositories.AtlasRepository;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.yaml.metrics.MetricValues;
import aiai.ai.yaml.metrics.MetricsUtils;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import aiai.ai.yaml.task.TaskParamYaml;
import aiai.ai.yaml.task.TaskParamYamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
@Slf4j
@Service
@Profile("launchpad")
public class AtlasTopLevelService {

    private final AtlasRepository atlasRepository;
    private final AtlasService atlasService;
    private final BinaryDataService binaryDataService;
    private final ConsoleFormAtlasService consoleFormAtlasService;

    private static class ParamFilter {
        String key;
        int idx;

        ParamFilter(String filter) {
            final int endIndex = filter.lastIndexOf('-');
            this.key = filter.substring( 0, endIndex);
            this.idx = Integer.parseInt(filter.substring( endIndex+1));
        }
        static ParamFilter of(String filetr) {
            return new ParamFilter(filetr);
        }
    }

    public AtlasTopLevelService(AtlasRepository atlasRepository, AtlasService atlasService, BinaryDataService binaryDataService, ConsoleFormAtlasService consoleFormAtlasService) {
        this.atlasRepository = atlasRepository;
        this.atlasService = atlasService;
        this.binaryDataService = binaryDataService;
        this.consoleFormAtlasService = consoleFormAtlasService;
    }

    public AtlasData.ExperimentInfoExtended getExperimentInfo(Long id) {

        Atlas atlas = atlasRepository.findById(id).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentInfoExtended("#280.09 experiment wasn't found in atlas, id: " + id);
        }

        ExperimentStoredToAtlas estb1;
        try {
            estb1 = atlasService.fromJson(atlas.experiment);
        } catch (IOException e) {
            log.error("Error", e);
            return new AtlasData.ExperimentInfoExtended("#280.09 Can't extract experiment from atlas, error: " + e.toString());
        }

        Experiment experiment = estb1.experiment;
        if (experiment == null) {
            return new AtlasData.ExperimentInfoExtended("#280.09 experiment wasn't found, experimentId: " + id);
        }
        if (experiment.getFlowInstanceId() == null) {
            return new AtlasData.ExperimentInfoExtended("#280.12 experiment wasn't startet yet, experimentId: " + id);
        }
        FlowInstance flowInstance = estb1.flowInstance;
        if (flowInstance == null) {
            return new AtlasData.ExperimentInfoExtended("#280.16 experiment has broken ref to flowInstance, experimentId: " + id);
        }

        for (ExperimentHyperParams hyperParams : estb1.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants(variants.status ? variants.count : 0);
        }

        AtlasData.ExperimentInfoExtended result = new AtlasData.ExperimentInfoExtended();
        if (experiment.getFlowInstanceId() == null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }
        result.atlas = atlas;

        AtlasData.ExperimentInfo experimentInfoResult = new AtlasData.ExperimentInfo();
        experimentInfoResult.features = estb1.features;
        experimentInfoResult.flowInstance = flowInstance;
        experimentInfoResult.flowInstanceExecState = Enums.FlowInstanceExecState.toState(flowInstance.execState);

        result.experiment = experiment;
        result.experimentInfo = experimentInfoResult;
        return result;
    }

    public OperationStatusRest experimentDeleteCommit(Long id) {
        Atlas atlas = atlasRepository.findById(id).orElse(null);
        if (atlas == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,
                    "#280.71 experiment wasn't found in atlas, id: " + id);
        }
        atlasRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public AtlasData.PlotData getPlotData(Long atlasId, Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.PlotData("#280.09 experiment wasn't found in atlas, id: " + atlasId);
        }

        ExperimentStoredToAtlas estb1;
        try {
            estb1 = atlasService.fromJson(atlas.experiment);
        } catch (IOException e) {
            log.error("Error", e);
            return new AtlasData.PlotData("#280.09 Can't extract experiment from atlas, error: " + e.toString());
        }

        ExperimentFeature feature = estb1.getFeature(featureId);

        //noinspection UnnecessaryLocalVariable
        AtlasData.PlotData data = findExperimentTaskForPlot(estb1, feature, params, paramsAxis);
        return data;
    }

    public AtlasData.PlotData findExperimentTaskForPlot(
            ExperimentStoredToAtlas estb, ExperimentFeature feature, String[] params, String[] paramsAxis) {
        if (estb.experiment == null || feature == null) {
            return AtlasData.EMPTY_PLOT_DATA;
        } else {
            List<Task> selected = getTasksForFeatureIdAndParams(estb, feature, params);
            return collectDataForPlotting(estb, selected, paramsAxis);
        }
    }

    public List<Task> getTasksForFeatureIdAndParams(ExperimentStoredToAtlas estb1, ExperimentFeature feature, String[] params) {
        final Map<Long, Integer> taskToTaskType = estb1.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(feature.getId()))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

//                selected = taskRepository.findByIsCompletedIsTrueAndFeatureId(feature.getId());
        List<Task> selected = estb1.tasks.stream()
                .filter(o -> taskToTaskType.containsKey(o.id) && o.execState > 1)
                .collect(Collectors.toList());

        if (!isEmpty(params)) {
            selected = filterTasks(estb1.experiment, params, selected);
        }
        return selected;
    }

    private AtlasData.PlotData collectDataForPlotting(ExperimentStoredToAtlas estb, List<Task> selected, String[] paramsAxis) {
        final AtlasData.PlotData data = new AtlasData.PlotData();
        final List<String> paramCleared = new ArrayList<>();
        for (String param : paramsAxis) {
            if (StringUtils.isBlank(param)) {
                continue;
            }
            if (!paramCleared.contains(param)) {
                paramCleared.add(param);
            }
        }
        if (paramCleared.size()!=2) {
            throw new IllegalStateException("Wrong number of params for axes. Expected: 2, actual: " + paramCleared.size());
        }
        Map<String, Map<String, Integer>> map = estb.getHyperParamsAsMap(false);
        data.x.addAll(map.get(paramCleared.get(0)).keySet());
        data.y.addAll(map.get(paramCleared.get(1)).keySet());

        Map<String, Integer> mapX = new HashMap<>();
        int idx=0;
        for (String x : data.x) {
            mapX.put(x, idx++);
        }
        Map<String, Integer> mapY = new HashMap<>();
        idx=0;
        for (String y : data.y) {
            mapY.put(y, idx++);
        }

        data.z = new BigDecimal[data.y.size()][data.x.size()];
        for (int i = 0; i < data.y.size(); i++) {
            for (int j = 0; j < data.x.size(); j++) {
                data.z[i][j] = BigDecimal.ZERO;
            }
        }

        String metricKey = null;
        for (Task task : selected) {

            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(task.metrics) );
            if (metricValues==null) {
                continue;
            }
            if (metricKey==null) {
                //noinspection LoopStatementThatDoesntLoop
                for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                    metricKey = entry.getKey();
                    break;
                }
            }

            final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
            int idxX = mapX.get(taskParamYaml.hyperParams.get(paramCleared.get(0)));
            int idxY = mapY.get(taskParamYaml.hyperParams.get(paramCleared.get(1)));
            data.z[idxY][idxX] = data.z[idxY][idxX].add(metricValues.values.get(metricKey));
        }

        return data;
    }


    private List<Task> filterTasks(Experiment experiment, String[] params, List<Task> tasks) {
        final Set<String> paramSet = new HashSet<>();
        final Set<String> paramFilterKeys = new HashSet<>();
        for (String param : params) {
            if (StringUtils.isBlank(param)) {
                continue;
            }
            paramSet.add(param);
            paramFilterKeys.add(ParamFilter.of(param).key);
        }
        final Map<String, Map<String, Integer>> paramByIndex = experiment.getHyperParamsAsMap();

        List<Task> selected = new ArrayList<>();
        for (Task task : tasks) {
            final TaskParamYaml taskParamYaml = TaskParamYamlUtils.toTaskYaml(task.getParams());
            boolean[] isOk = new boolean[taskParamYaml.hyperParams.size()];
            int idx = 0;
            for (Map.Entry<String, String> entry : taskParamYaml.hyperParams.entrySet()) {
                try {
                    if (!paramFilterKeys.contains(entry.getKey())) {
                        isOk[idx] = true;
                        continue;
                    }
                    final Map<String, Integer> map = paramByIndex.getOrDefault(entry.getKey(), new HashMap<>());
                    if (map.isEmpty()) {
                        continue;
                    }
                    if (map.size()==1) {
                        isOk[idx] = true;
                        continue;
                    }

                    boolean isFilter = paramSet.contains(entry.getKey() + "-" + paramByIndex.get(entry.getKey()).get(entry.getKey() + "-" + entry.getValue()));
                    if (isFilter) {
                        isOk[idx] = true;
                    }
                }
                finally {
                    idx++;
                }
            }
            if (isInclude(isOk)) {
                selected.add(task);
            }
        }
        return selected;
    }

    private boolean isInclude(boolean[] isOk ) {
        for (boolean b : isOk) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmpty(String[] params) {
        for (String param : params) {
            if (StringUtils.isNotBlank(param)) {
                return false;
            }
        }
        return true;
    }

    public AtlasData.ExperimentFeatureExtendedResult getExperimentFeatureExtended(long atlasId, Long experimentId, Long featureId) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#280.09 experiment wasn't found in atlas, id: " + atlasId);
        }

        ExperimentStoredToAtlas estb1;
        try {
            estb1 = atlasService.fromJson(atlas.experiment);
        } catch (IOException e) {
            log.error("Error", e);
            return new AtlasData.ExperimentFeatureExtendedResult("#280.09 Can't extract experiment from atlas, error: " + e.toString());
        }

        ExperimentFeature experimentFeature = estb1.getFeature(featureId);
        if (experimentFeature == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#280.05 feature wasn't found, experimentFeatureId: " + featureId);
        }

        AtlasData.ExperimentFeatureExtendedResult result = prepareExperimentFeatures(estb1, estb1.experiment, experimentFeature);
        if (result==null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#280.15 can't prepare experiment data");
        }
        return result;
    }

    public AtlasData.ExperimentFeatureExtendedResult prepareExperimentFeatures(
            ExperimentStoredToAtlas estb,
            Experiment experiment, final ExperimentFeature experimentFeature) {

        TasksData.TasksResult tasksResult = new TasksData.TasksResult();

        final Map<Long, Integer> taskToTaskType = estb.taskFeatures
                .stream()
                .filter(taskFeature -> taskFeature.featureId.equals(experimentFeature.id))
                .collect(Collectors.toMap(o -> o.taskId, o -> o.taskType));

        List<TaskWIthType> taskWIthTypes = estb.tasks.stream()
                .filter(o->taskToTaskType.containsKey(o.id))
                .sorted(Comparator.comparingLong(o -> o.id))
                .limit(11)
                .map(o-> new TaskWIthType(o, taskToTaskType.get(o.id)))
                .collect(Collectors.toList());

        tasksResult.items = new SliceImpl<>(
                taskWIthTypes.subList(0, taskWIthTypes.size()>10 ? 10 : taskWIthTypes.size()),
                Consts.PAGE_REQUEST_10_REC,
                taskWIthTypes.size()>10
        );

//        SELECT new aiai.ai.launchpad.experiment.task.TaskWIthType(t, tef.taskType)
//        FROM Task t, ExperimentTaskFeature tef
//        WHERE t.id=tef.taskId and tef.featureId=:featureId order by t.id asc
//        tasksResult.items = taskRepository.findPredictTasks(Consts.PAGE_REQUEST_10_REC, experimentFeature.getId());


        AtlasData.HyperParamResult hyperParamResult = new AtlasData.HyperParamResult();
        for (ExperimentHyperParams hyperParam : estb.getHyperParams()) {
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParam.getValues());
            ExperimentData.HyperParamList list = new ExperimentData.HyperParamList(hyperParam.getKey());
            for (String value : variants.values) {
                list.getList().add( new ExperimentData.HyperParamElement(value, false));
            }
            if (list.getList().isEmpty()) {
                list.getList().add( new ExperimentData.HyperParamElement("<Error value>", false));
            }
            hyperParamResult.getElements().add(list);
        }

        final AtlasData.MetricsResult metricsResult = new AtlasData.MetricsResult();
        final List<Map<String, BigDecimal>> values = new ArrayList<>();

        estb.tasks.stream()
                .filter(o->taskToTaskType.containsKey(o.id) && o.execState > 1)
                .forEach( o-> {
                    MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(o.metrics) );
                    if (metricValues==null) {
                        return;
                    }
                    for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
                        metricsResult.metricNames.add(entry.getKey());
                    }
                    values.add(metricValues.values);

                });

//        List<Task> tasks = taskRepository.findByIsCompletedIsTrueAndFeatureId(experimentFeature.getId());
//        for (Task seq : tasks) {
//            MetricValues metricValues = MetricsUtils.getValues( MetricsUtils.to(seq.metrics) );
//            if (metricValues==null) {
//                continue;
//            }
//            for (Map.Entry<String, BigDecimal> entry : metricValues.values.entrySet()) {
//                metricsResult.metricNames.add(entry.getKey());
//            }
//            values.add(metricValues.values);
//        }

        List<AtlasData.MetricElement> elements = new ArrayList<>();
        for (Map<String, BigDecimal> value : values) {
            AtlasData.MetricElement element = new AtlasData.MetricElement();
            for (String metricName : metricsResult.metricNames) {
                element.values.add(value.get(metricName));
            }
            elements.add(element);
        }
        elements.sort(AtlasData.MetricElement::compare);

        metricsResult.metrics.addAll( elements.subList(0, Math.min(20, elements.size())) );

        AtlasData.ExperimentFeatureExtendedResult result = new AtlasData.ExperimentFeatureExtendedResult();
        result.metricsResult = metricsResult;
        result.hyperParamResult = hyperParamResult;
        result.tasksResult = tasksResult;
        result.experiment = experiment;
        result.experimentFeature = experimentFeature;
        result.consoleResult = new AtlasData.ConsoleResult();

        return result;
    }

    public AtlasData.ConsoleResult getTasksConsolePart(long atlasId, long taskId) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ConsoleResult("#280.09 experiment wasn't found in atlas, id: " + atlasId);
        }

        ExperimentStoredToAtlas estb;
        try {
            estb = atlasService.fromJson(atlas.experiment);
        } catch (IOException e) {
            log.error("Error", e);
            return new AtlasData.ConsoleResult("#280.09 Can't extract experiment from atlas, error: " + e.toString());
        }

        String poolCode = AtlasService.getPoolCodeForExperiment(estb.flowInstance.id, estb.experiment.id);
        List<BinaryData> datas = binaryDataService.getByPoolCodeAndType(poolCode, Enums.BinaryDataType.CONSOLE);
        if (datas.isEmpty()) {
            return new AtlasData.ConsoleResult("#280.27 Can't find a console output");
        }

        // TODO need to refactor to use InputStream
        byte[] bytes = binaryDataService.getDataAsBytes(datas.get(0).id);
        ConsoleOutputStoredToAtlas.TaskOutput taskOutput = null;
        try(InputStream is = new ByteArrayInputStream(bytes)) {
            LineIterator it = IOUtils.lineIterator(is, StandardCharsets.UTF_8);
            String taskIdAsStr = Long.toString(taskId);
            while (it.hasNext()) {
                String line = it.nextLine();
                int idx = line.indexOf(',');
                if (idx==-1) {
                    log.warn("wrong format of line: " + line);
                    continue;
                }
                if (taskIdAsStr.equals(line.substring(0, idx))) {
                    String json = line.substring(idx+1);
                    taskOutput = consoleFormAtlasService.fromJson(json);
                    break;
                }
            }
        } catch (IOException e) {
            return new AtlasData.ConsoleResult("#280.29 Can't process a console output, error: " + e.toString());
        }

        AtlasData.ConsoleResult result = new AtlasData.ConsoleResult();
        if (taskOutput!=null) {
            SnippetExec snippetExec = SnippetExecUtils.to(taskOutput.console);
            if (snippetExec!=null) {
                final ExecProcessService.Result execResult = snippetExec.getExec();
                result.items.add(new AtlasData.ConsoleResult.SimpleConsoleOutput(execResult.exitCode, execResult.isOk, execResult.console));
            }
            else {
                log.info("#280.10 snippetExec is null");
            }
        }
        return result;
    }

    public AtlasData.ExperimentFeatureExtendedResult getFeatureProgressPart(long atlasId, Long experimentId, Long featureId, String[] params, Pageable pageable) {
        Atlas atlas = atlasRepository.findById(atlasId).orElse(null);
        if (atlas == null) {
            return new AtlasData.ExperimentFeatureExtendedResult("#280.37 experiment wasn't found in atlas, id: " + atlasId);
        }

        ExperimentStoredToAtlas estb;
        try {
            estb = atlasService.fromJson(atlas.experiment);
        } catch (IOException e) {
            log.error("Error", e);
            return new AtlasData.ExperimentFeatureExtendedResult("#280.39 Can't extract experiment from atlas, error: " + e.toString());
        }

        ExperimentFeature feature = estb.getFeature(featureId);

        TasksData.TasksResult tasksResult = new TasksData.TasksResult();
        tasksResult.items = findTasks(estb, atlasId, ControllerUtils.fixPageSize(10, pageable), estb.experiment, feature, params);

        AtlasData.ExperimentFeatureExtendedResult result = new AtlasData.ExperimentFeatureExtendedResult();
        result.tasksResult = tasksResult;
        result.experiment = estb.experiment;
        result.experimentFeature = feature;
        result.consoleResult = new AtlasData.ConsoleResult();
        return result;
    }

    public Slice<TaskWIthType> findTasks(ExperimentStoredToAtlas estb, long atlasId, Pageable pageable, Experiment experiment, ExperimentFeature feature, String[] params) {
        if (experiment == null || feature == null) {
            return Page.empty();
        } else {
            List<Task> selected = getTasksForFeatureIdAndParams(estb, feature, params);
            List<Task> subList = selected.subList((int)pageable.getOffset(), (int)Math.min(selected.size(), pageable.getOffset() + pageable.getPageSize()));
            List<TaskWIthType> list = new ArrayList<>();
            for (Task task : subList) {
                ExperimentTaskFeature etf = estb.getExperimentTaskFeature(task.id);
                if (etf==null) {
                    log.warn("Can't get type of task for taskId " + task.id);
                }
                int type = etf!=null ? Enums.ExperimentTaskType.from( etf.getTaskType() ).value : Enums.ExperimentTaskType.UNKNOWN.value;

                list.add(new TaskWIthType(task, type));
            }
            //noinspection UnnecessaryLocalVariable
            Slice<TaskWIthType> slice = new PageImpl<>(list, pageable, selected.size());
            return slice;
        }
    }


}