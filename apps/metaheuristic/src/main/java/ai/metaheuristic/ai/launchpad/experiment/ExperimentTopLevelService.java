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

package ai.metaheuristic.ai.launchpad.experiment;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.beans.*;
import ai.metaheuristic.ai.launchpad.repositories.*;
import ai.metaheuristic.ai.launchpad.snippet.SnippetService;
import ai.metaheuristic.ai.launchpad.task.TaskPersistencer;
import ai.metaheuristic.ai.snippet.SnippetCode;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.experiment.ExperimentParamsYamlUtils;
import ai.metaheuristic.ai.yaml.snippet_exec.SnippetExecUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.SnippetApiData;
import ai.metaheuristic.api.data.experiment.ExperimentApiData;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.task.TaskApiData;
import ai.metaheuristic.api.launchpad.Task;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
@Service
@Slf4j
@Profile("launchpad")
@RequiredArgsConstructor
public class ExperimentTopLevelService {

    private final Globals globals;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final TaskRepository taskRepository;
    private final WorkbookRepository workbookRepository;
    private final TaskPersistencer taskPersistencer;

    private final ExperimentCache experimentCache;
    private final ExperimentService experimentService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentHyperParamsRepository experimentHyperParamsRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;

    public static ExperimentApiData.SimpleExperiment asSimpleExperiment(Experiment e) {
        ExperimentParamsYaml params = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(e.getParams());
        return new ExperimentApiData.SimpleExperiment(params.getName(), params.getDescription(), params.getCode(), params.getSeed(), e.getId());
    }

    public static ExperimentApiData.ExperimentResult asExperimentResult(Experiment e) {
        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentData(e));
    }

    public ExperimentApiData.ExperimentsResult getExperiments(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        ExperimentApiData.ExperimentsResult result = new ExperimentApiData.ExperimentsResult();
        final Slice<Experiment> experiments = experimentRepository.findAllByOrderByIdDesc(pageable);

        List<ExperimentApiData.ExperimentResult> experimentResults =
                experiments.stream().map(ExperimentTopLevelService::asExperimentResult).collect(Collectors.toList());

        result.items = new PageImpl<>(experimentResults, pageable, experimentResults.size() + (experiments.hasNext() ? 1 : 0) );
        return result;
    }

    public ExperimentApiData.ExperimentResult getExperiment(long experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentResult("#285.01 experiment wasn't found, experimentId: " + experimentId );
        }
        return new ExperimentApiData.ExperimentResult(ExperimentService.asExperimentData(experiment));
    }

    public ExperimentApiData.PlotData getPlotData(Long experimentId, Long featureId, String[] params, String[] paramsAxis) {
        return experimentService.getPlotData(experimentId, featureId, params, paramsAxis);
    }

    public ExperimentApiData.ConsoleResult getTasksConsolePart(Long taskId) {
        ExperimentApiData.ConsoleResult result = new ExperimentApiData.ConsoleResult();
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task!=null) {
            SnippetApiData.SnippetExec snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            if (snippetExec!=null) {
                final SnippetApiData.SnippetExecResult execSnippetExecResult = snippetExec.getExec();
                result.items.add(new ExperimentApiData.ConsoleResult.SimpleConsoleOutput(
                        execSnippetExecResult.snippetCode, execSnippetExecResult.exitCode, execSnippetExecResult.isOk, execSnippetExecResult.console));
            }
            else {
                log.info("#285.10 snippetExec is null");
            }
        }
        return result;
    }

    public ExperimentApiData.ExperimentFeatureExtendedResult getFeatureProgressPart(Long experimentId, Long featureId, String[] params, Pageable pageable) {
        Experiment experiment= experimentCache.findById(experimentId);
        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);

        TaskApiData.TasksResult tasksResult = new TaskApiData.TasksResult();
        tasksResult.items = experimentService.findTasks(ControllerUtils.fixPageSize(10, pageable), experiment, feature, params);

        ExperimentApiData.ExperimentFeatureExtendedResult result = new ExperimentApiData.ExperimentFeatureExtendedResult();
        result.tasksResult = tasksResult;
        result.experiment = ExperimentService.asExperimentData(experiment);
        result.experimentFeature = ExperimentService.asExperimentFeatureData(feature);
        result.consoleResult = new ExperimentApiData.ConsoleResult();
        return result;
    }

    public ExperimentApiData.ExperimentFeatureExtendedResult getExperimentFeatureExtended(Long experimentId, Long featureId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#285.15 experiment wasn't found, experimentId: " + experimentId);
        }

        ExperimentFeature experimentFeature = experimentFeatureRepository.findById(featureId).orElse(null);
        if (experimentFeature == null) {
            return new ExperimentApiData.ExperimentFeatureExtendedResult("#285.19 feature wasn't found, experimentFeatureId: " + featureId);
        }

        return experimentService.prepareExperimentFeatures(experiment, experimentFeature);
    }

    public ExperimentApiData.ExperimentInfoExtendedResult getExperimentInfo(Long id) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.22 experiment wasn't found, experimentId: " + id);
        }
        if (experiment.getWorkbookId() == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.25 experiment wasn't startet yet, experimentId: " + id);
        }
        Workbook workbook = workbookRepository.findById(experiment.getWorkbookId()).orElse(null);
        if (workbook == null) {
            return new ExperimentApiData.ExperimentInfoExtendedResult("#285.29 experiment has broken ref to workbook, experimentId: " + id);
        }

        for (ExperimentHyperParams hyperParams : experiment.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants( variants.status ?variants.count : 0 );
        }

        ExperimentApiData.ExperimentInfoExtendedResult result = new ExperimentApiData.ExperimentInfoExtendedResult();
        if (experiment.getWorkbookId()==null) {
            result.addInfoMessage("Launch is disabled, dataset isn't assigned");
        }

        ExperimentApiData.ExperimentInfoResult experimentInfoResult = new ExperimentApiData.ExperimentInfoResult();
        final List<ExperimentFeature> experimentFeatures = experimentFeatureRepository.findByExperimentIdOrderByMaxValueDesc(experiment.getId());
        experimentInfoResult.features = experimentFeatures.stream().map(ExperimentService::asExperimentFeatureData).collect(Collectors.toList());
        experimentInfoResult.workbook = workbook;
        experimentInfoResult.workbookExecState = EnumsApi.WorkbookExecState.toState(workbook.getExecState());
        result.experiment = ExperimentService.asExperimentData(experiment);
        result.experimentInfo = experimentInfoResult;
        return result;
    }


    public ExperimentApiData.ExperimentsEditResult editExperiment(@PathVariable Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentApiData.ExperimentsEditResult("#285.33 experiment wasn't found, experimentId: " + id);
        }
        Iterable<Snippet> snippets = snippetRepository.findAll();
        ExperimentApiData.SnippetResult snippetResult = new ExperimentApiData.SnippetResult();

        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());
        snippetResult.snippets = experimentSnippets.stream().map(es->
                new ExperimentApiData.ExperimentSnippetResult(
                        es.getId(), es.getVersion(), es.getSnippetCode(), es.type, es.getExperimentId())).collect(Collectors.toList());

        final List<String> types = Arrays.asList(CommonConsts.FIT_TYPE, CommonConsts.PREDICT_TYPE);
        snippetResult.selectOptions = snippetService.getSelectOptions(snippets,
                snippetResult.snippets.stream().map(o -> new SnippetCode(o.getId(), o.getSnippetCode())).collect(Collectors.toList()),
                (s) -> {
                    if (!types.contains(s.type) ) {
                        return true;
                    }
                    if (CommonConsts.FIT_TYPE.equals(s.type) && snippetService.hasFit(experimentSnippets)) {
                        return true;
                    }
                    else if (CommonConsts.PREDICT_TYPE.equals(s.type) && snippetService.hasPredict(experimentSnippets)) {
                        return true;
                    }
                    else return false;
                });

        snippetResult.sortSnippetsByOrder();
        ExperimentApiData.ExperimentsEditResult result = new ExperimentApiData.ExperimentsEditResult();

        ExperimentApiData.HyperParamsResult r = new ExperimentApiData.HyperParamsResult();
        r.items = experiment.getHyperParams().stream().map(ExperimentTopLevelService::asHyperParamData).collect(Collectors.toList());
        result.hyperParams = r;
        result.simpleExperiment = asSimpleExperiment(experiment);
        result.snippetResult = snippetResult;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public OperationStatusRest addExperimentCommit(ExperimentApiData.ExperimentData experiment) {

        Experiment e = new Experiment();
        ExperimentParamsYaml params = new ExperimentParamsYaml();

        params.name = StringUtils.strip(experiment.getName());
        params.description = StringUtils.strip(experiment.getDescription());
        params.setSeed(experiment.getSeed()==0 ? 1 : experiment.getSeed());
        e.code = StringUtils.strip(experiment.getCode());
        e.setCreatedOn(System.currentTimeMillis());

        experimentCache.save(e);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public static ExperimentApiData.HyperParamData asHyperParamData(ExperimentHyperParams ehp) {
        return new ExperimentApiData.HyperParamData(ehp.getId(), ehp.getVersion(), ehp.getKey(), ehp.getValues(), ehp.getVariants());
    }

    @SuppressWarnings("Duplicates")
    public OperationStatusRest editExperimentCommit(ExperimentApiData.SimpleExperiment simpleExperiment) {
        OperationStatusRest op = validate(simpleExperiment);
        if (op!=null) {
            return op;
        }

        Experiment e = experimentRepository.findByIdForUpdate(simpleExperiment.id);
        if (e == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.37 experiment wasn't found, experimentId: " + simpleExperiment.id);
        }
        ExperimentParamsYaml params = ExperimentParamsYamlUtils.BASE_YAML_UTILS.to(e.getParams());

        params.name = StringUtils.strip(simpleExperiment.getName());
        params.description = StringUtils.strip(simpleExperiment.getDescription());
        params.setSeed(simpleExperiment.getSeed()==0 ? 1 : simpleExperiment.getSeed());
        e.code = StringUtils.strip(simpleExperiment.getCode());
        e.setCreatedOn(System.currentTimeMillis());

        experimentCache.save(e);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private OperationStatusRest validate(ExperimentApiData.SimpleExperiment se) {
        if (StringUtils.isBlank(se.getName())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.40 Name of experiment is blank.");
        }
        if (StringUtils.isBlank(se.getCode())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.41 Code of experiment is blank.");
        }
        if (StringUtils.isBlank(se.getDescription())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.42 Description of experiment is blank.");
        }
        return null;
    }

    public OperationStatusRest metadataAddCommit(Long experimentId, String key, String value) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.45 experiment wasn't found, experimentId: " + experimentId);
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#285.48 hyper param's key and value must not be null, key: "+key+", value: " + value );
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }
        String keyFinal = key.trim();
        boolean isExist = experiment.getHyperParams().stream().map(ExperimentHyperParams::getKey).anyMatch(keyFinal::equals);
        if (isExist) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#285.51 hyper parameter "+key+" already exist");
        }

        ExperimentHyperParams m = new ExperimentHyperParams();
        m.setExperiment(experiment);
        m.setKey(keyFinal);
        m.setValues(value.trim());
        experiment.getHyperParams().add(m);

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataEditCommit(Long experimentId, String key, String value) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.53 experiment wasn't found, id: "+experimentId );
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.55 hyper param's key and value must not be null, key: "+key+", value: " + value );
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }
        ExperimentHyperParams m=null;
        String keyFinal = key.trim();
        for (ExperimentHyperParams hyperParam : experiment.getHyperParams()) {
            if (hyperParam.getKey().equals(keyFinal)) {
                m = hyperParam;
                break;
            }
        }
        if (m==null) {
            m = new ExperimentHyperParams();
            m.setExperiment(experiment);
            m.setKey(keyFinal);
            experiment.getHyperParams().add(m);
        }
        m.setValues(value.trim());

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest snippetAddCommit(Long id, String snippetCode) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.58 experiment wasn't found, id: "+id );
        }
        Long experimentId = experiment.getId();
        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experimentId);

        Snippet snippet = snippetRepository.findByCode(snippetCode);

        ExperimentSnippet ts = new ExperimentSnippet();
        ts.setExperimentId(experimentId);
        ts.setSnippetCode( snippetCode );
        ts.setType( snippet.type );

        List<ExperimentSnippet> list = new ArrayList<>(experimentSnippets);
        list.add(ts);

        experimentSnippetRepository.saveAll(list);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataDeleteCommit(long experimentId, Long id) {
        ExperimentHyperParams hyperParams = experimentHyperParamsRepository.findById(id).orElse(null);
        if (hyperParams == null || experimentId != hyperParams.getExperiment().getId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.61 Hyper parameters misconfigured, try again.");
        }
        experimentHyperParamsRepository.deleteById(id);
        experimentCache.invalidate(experimentId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest metadataDefaultAddCommit(long experimentId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.63 experiment wasn't found, id: "+experimentId );
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }

        add(experiment, "epoch", "[10]");
        add(experiment, "RNN", "[LSTM, GRU, SimpleRNN]");
        add(experiment, "activation", "[hard_sigmoid, softplus, softmax, softsign, relu, tanh, sigmoid, linear, elu]");
        add(experiment, "optimizer", "[sgd, nadam, adagrad, adadelta, rmsprop, adam, adamax]");
        add(experiment, "batch_size", "[20, 40, 60]");
        add(experiment, "time_steps", "[5, 40, 60]");
        add(experiment, "metrics_functions", "['#in_top_draw_digit, accuracy', 'accuracy']");

        experimentCache.save(experiment);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private void add(Experiment experiment, String key, String value) {
        ExperimentHyperParams param = getParams(experiment, key, value);
        List<ExperimentHyperParams> params = experiment.getHyperParams();
        for (ExperimentHyperParams p1 : params) {
            if (p1.getKey().equals(param.getKey())) {
                p1.setValues(param.getValues());
                return;
            }
        }
        params.add(param);
    }

    private ExperimentHyperParams getParams(Experiment experiment, String key, String value) {
        ExperimentHyperParams params = new ExperimentHyperParams();
        params.setExperiment(experiment);
        params.setKey(key);
        params.setValues(value);
        return params;
    }

    public OperationStatusRest snippetDeleteCommit(long experimentId, Long id) {
        ExperimentSnippet snippet = experimentSnippetRepository.findById(id).orElse(null);
        if (snippet == null || experimentId != snippet.getExperimentId()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.66 Snippet is misconfigured. Try again" );
        }
        experimentSnippetRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest experimentDeleteCommit(Long id) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.71 experiment wasn't found, experimentId: " + id);
        }
        experimentSnippetRepository.deleteByExperimentId(id);
        experimentFeatureRepository.deleteByExperimentId(id);
        experimentCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest experimentCloneCommit(Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.73 experiment wasn't found, experimentId: " + id);
        }
        final Experiment e = new Experiment();
        e.setCode(StrUtils.incCopyNumber(experiment.getCode()));
        e.setParams( e.getParams() );
        e.setCreatedOn(System.currentTimeMillis());
        e.setHyperParams(
                experiment.getHyperParams()
                        .stream()
                        .map( p -> new ExperimentHyperParams(p.getKey(), p.getValues(), e))
                        .collect(Collectors.toList()));
        experimentCache.save(e);

        List<ExperimentSnippet> snippets = experimentSnippetRepository.findByExperimentId(experiment.getId());

        experimentSnippetRepository.saveAll( snippets.stream().map(s -> new ExperimentSnippet(s.getSnippetCode(), s.getType(), e.getId())).collect(Collectors.toList()));

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest rerunTask(long taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.75 Can't re-run task "+taskId+", task with such taskId wasn't found");
        }
        Workbook workbook = workbookRepository.findById(task.getWorkbookId()).orElse(null);
        if (workbook == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#285.84 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any workbook");
        }

        Task t = taskPersistencer.resetTask(taskId);
        return t!=null
                ? OperationStatusRest.OPERATION_STATUS_OK
                : new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "Can't re-run task #"+taskId+", see log for more information");
    }
}
