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

package aiai.ai.launchpad.rest;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.core.ExecProcessService;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.experiment.ExperimentCache;
import aiai.ai.launchpad.experiment.ExperimentService;
import aiai.ai.launchpad.experiment.ExperimentUtils;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.data.ExperimentData;
import aiai.ai.launchpad.data.OperationStatusRest;
import aiai.ai.launchpad.data.SnippetData;
import aiai.ai.launchpad.data.TasksData;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.snippet_exec.SnippetExec;
import aiai.ai.yaml.snippet_exec.SnippetExecUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
@RestController
@RequestMapping("/ng/launchpad/experiment")
@Slf4j
@Profile("launchpad")
@CrossOrigin
//@CrossOrigin(origins="*", maxAge=3600)
public class ExperimentRestController {

    @Data
    public static class Result {
        public Slice<Experiment> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleExperiment {
        public String name;
        public String description;
        public String code;
        public int seed;
        public long id;

        public static SimpleExperiment to(Experiment e) {
            return new SimpleExperiment(e.getName(), e.getDescription(), e.getCode(), e.getSeed(), e.getId());
        }
    }

    private final Globals globals;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;
    private final ExperimentService experimentService;
    private final ExperimentHyperParamsRepository experimentHyperParamsRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final TaskRepository taskRepository;
    private final FlowInstanceRepository flowInstanceRepository;
    private final TaskPersistencer taskPersistencer;

    public ExperimentRestController(Globals globals, SnippetRepository snippetRepository, ExperimentRepository experimentRepository, ExperimentHyperParamsRepository experimentHyperParamsRepository, SnippetService snippetService, ExperimentCache experimentCache, ExperimentService experimentService, ExperimentSnippetRepository experimentSnippetRepository, ExperimentFeatureRepository experimentFeatureRepository, TaskRepository taskRepository, SnippetCache snippetCache, FlowInstanceRepository flowInstanceRepository, TaskPersistencer taskPersistencer) {
        this.globals = globals;
        this.snippetRepository = snippetRepository;
        this.experimentRepository = experimentRepository;
        this.experimentHyperParamsRepository = experimentHyperParamsRepository;
        this.snippetService = snippetService;
        this.experimentCache = experimentCache;
        this.experimentService = experimentService;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.taskRepository = taskRepository;
        this.flowInstanceRepository = flowInstanceRepository;
        this.taskPersistencer = taskPersistencer;
    }

    @GetMapping("/experiments")
    public ExperimentData.ExperimentsResult init(@PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        return new ExperimentData.ExperimentsResult(experimentRepository.findAllByOrderByIdDesc(pageable));
    }

    @PostMapping("/experiment-feature-plot-data-part/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    public @ResponseBody ExperimentService.PlotData getPlotData(
            @PathVariable Long experimentId, @PathVariable Long featureId,
            @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        return experimentService.getPlotData(experimentId, featureId, params, paramsAxis);
    }

    @PostMapping("/experiment-feature-progress-console-part/{taskId}")
    public ExperimentData.ConsoleResult getTasksConsolePart(@PathVariable(name="taskId") Long taskId) {
        ExperimentData.ConsoleResult result = new ExperimentData.ConsoleResult();
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task!=null) {
            SnippetExec snippetExec = SnippetExecUtils.to(task.getSnippetExecResults());
            if (snippetExec!=null) {
                final ExecProcessService.Result execResult = snippetExec.getExec();
                result.items.add(new ExperimentData.ConsoleResult.SimpleConsoleOutput(execResult.exitCode, execResult.isOk, execResult.console));
            }
            else {
                log.warn("snippetExec is null for tasId {}", taskId);
            }
        }
        return result;
    }

    @PostMapping("/experiment-feature-progress-part/{experimentId}/{featureId}/{params}/part")
    public ExperimentData.ExperimentFeatureProgress getFeatureProgressPart(
            @PathVariable Long experimentId, @PathVariable Long featureId,
            @PathVariable String[] params, @PageableDefault(size = 10) Pageable pageable) {
        Experiment experiment= experimentCache.findById(experimentId);
        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);

        ExperimentData.ExperimentFeatureProgress data = new ExperimentData.ExperimentFeatureProgress();
        TasksData.TasksResult result = new TasksData.TasksResult();
        result.items = experimentService.findTasks(ControllerUtils.fixPageSize(10, pageable), experiment, feature, params);

        data.tasksResult = result;
        data.experiment = experiment;
        data.experimentFeature = feature;
        data.consoleResult = new ExperimentData.ConsoleResult();

        return data;
    }

    @GetMapping(value = "/experiment-feature-progress/{experimentId}/{featureId}")
    public String getFeatures(Model model, @PathVariable Long experimentId, @PathVariable Long featureId, final RedirectAttributes redirectAttributes ) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#280.01 experiment wasn't found, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }

        ExperimentFeature experimentFeature = experimentFeatureRepository.findById(featureId).orElse(null);
        if (experimentFeature == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#280.05 feature wasn't found, experimentFeatureId: " + featureId);
            return "redirect:/launchpad/experiments";
        }

        Map<String, Object> map = experimentService.prepareExperimentFeatures(experiment, experimentFeature);
        model.addAllAttributes(map);

        return "launchpad/experiment-feature-progress";
    }

    @GetMapping(value = "/experiment-info/{id}")
    public ExperimentData.ExperimentFeatureProgress info(@PathVariable Long id) {

        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentData.ExperimentFeatureProgress("#280.09 experiment wasn't found, experimentId: " + id);
        }
        if (experiment.getFlowInstanceId() == null) {
            return new ExperimentData.ExperimentFeatureProgress("#280.12 experiment wasn't startet yet, experimentId: " + id);
        }
        FlowInstance flowInstance = flowInstanceRepository.findById(experiment.getFlowInstanceId()).orElse(null);
        if (flowInstance == null) {
            return new ExperimentData.ExperimentFeatureProgress("#280.16 experiment has broken ref to flowInstance, experimentId: " + id);
        }

        for (ExperimentHyperParams hyperParams : experiment.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants( variants.status ?variants.count : 0 );
        }
        ExperimentData.ExperimentFeatureProgress experimentInfoRest = new ExperimentData.ExperimentFeatureProgress();
        if (experiment.getFlowInstanceId()==null) {
            experimentInfoRest.infoMessages = Collections.singletonList("Launch is disabled, dataset isn't assigned");
        }

        ExperimentData.ExperimentExtendedResult experimentResult = new ExperimentData.ExperimentExtendedResult();
        experimentResult.features = experimentFeatureRepository.findByExperimentIdOrderByMaxValueDesc(experiment.getId());
        experimentResult.flowInstance = flowInstance;
        experimentResult.flowInstanceExecState = Enums.FlowInstanceExecState.toState(flowInstance.execState);

        experimentInfoRest.experiment = experiment;
        experimentInfoRest.experimentResult = experimentResult;
        return experimentInfoRest;
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public String edit(@PathVariable Long id, Model model, @ModelAttribute("errorMessage") final String errorMessage, final RedirectAttributes redirectAttributes) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#280.19 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        Iterable<Snippet> snippets = snippetRepository.findAll();
        SnippetData.SnippetResult snippetResult = new SnippetData.SnippetResult();

        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());
        snippetResult.snippets = experimentSnippets;
        final List<String> types = Arrays.asList("fit", "predict");
        snippetResult.selectOptions = snippetService.getSelectOptions(snippets,
                snippetResult.snippets.stream().map(o -> new SnippetCode(o.getId(), o.getSnippetCode())).collect(Collectors.toList()),
                (s) -> {
                    if (!types.contains(s.type) ) {
                        return true;
                    }
                    if ("fit".equals(s.type) && snippetService.hasFit(experimentSnippets)) {
                        return true;
                    }
                    else if ("predict".equals(s.type) && snippetService.hasPredict(experimentSnippets)) {
                        return true;
                    }
                    else return false;
                });

        ExperimentData.ExperimentExtendedResult experimentResult = new ExperimentData.ExperimentExtendedResult();

        snippetResult.sortSnippetsByOrder();
        model.addAttribute("experiment", experiment);
        model.addAttribute("simpleExperiment", SimpleExperiment.to(experiment));
        model.addAttribute("experimentResult", experimentResult);
        model.addAttribute("snippetResult", snippetResult);
        return "launchpad/experiment-edit-form";
    }

    @PostMapping("/experiment-add-form-commit")
    public OperationStatusRest addFormCommit(Experiment experiment) {
        return processCommit(experiment);
    }

    @PostMapping("/experiment-edit-form-commit")
    public OperationStatusRest editFormCommit(SimpleExperiment simpleExperiment) {
        Experiment experiment = experimentCache.findById(simpleExperiment.id);
        if (experiment == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.23 experiment wasn't found, experimentId: " + simpleExperiment.id);
        }
        experiment.setName(simpleExperiment.getName());
        experiment.setDescription(simpleExperiment.getDescription());
        experiment.setSeed(simpleExperiment.getSeed());
        experiment.setCode(simpleExperiment.getCode());
        experiment.setCreatedOn(System.currentTimeMillis());

        return processCommit(experiment);
    }

    private OperationStatusRest processCommit(Experiment experiment) {
        if (StringUtils.isBlank(experiment.getName())) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "#280.27 Name of experiment is blank.");
        }
        if (StringUtils.isBlank(experiment.getCode())) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.31 Code of experiment is blank.");
        }
        if (StringUtils.isBlank(experiment.getDescription())) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.35 Description of experiment is blank.");
        }
        experiment.strip();
        experimentCache.save(experiment);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @PostMapping("/experiment-metadata-add-commit/{id}")
    public OperationStatusRest metadataAddCommit(@PathVariable Long id, String key, String value) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "Experiment not found");
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.42 hyper param's key and value must not be null, key: "+key+", value: " + value );
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }
        String keyFinal = key.trim();
        boolean isExist = experiment.getHyperParams().stream().map(ExperimentHyperParams::getKey).anyMatch(keyFinal::equals);
        if (isExist) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.45 hyper parameter "+key+" already exist");
        }

        ExperimentHyperParams m = new ExperimentHyperParams();
        m.setExperiment(experiment);
        m.setKey(keyFinal);
        m.setValues(value.trim());
        experiment.getHyperParams().add(m);
        experimentCache.save(experiment);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @PostMapping("/experiment-metadata-edit-commit/{id}")
    public OperationStatusRest metadataEditCommit(@PathVariable Long id, String key, String value) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.47 experiment wasn't found, id: "+id );
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.51 hyper param's key and value must not be null, key: "+key+", value: " + value );
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

    @PostMapping("/experiment-snippet-add-commit/{id}")
    public OperationStatusRest snippetAddCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.54 experiment wasn't found, id: "+id );
        }
        Long experimentId = experiment.getId();
        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experimentId);

        SnippetVersion version = SnippetVersion.from(code);
        if (version==null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.57 wrong format of snippet code: "+code);
        }
        Snippet snippet = snippetRepository.findByNameAndSnippetVersion(version.name, version.version);

        ExperimentSnippet ts = new ExperimentSnippet();
        ts.setExperimentId(experimentId);
        ts.setSnippetCode( code );
        ts.setType( snippet.type );

        List<ExperimentSnippet> list = new ArrayList<>(experimentSnippets);
        list.add(ts);

        ExperimentService.sortSnippetsByType(list);
        experimentSnippetRepository.saveAll(list);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @GetMapping("/experiment-metadata-delete-commit/{experimentId}/{id}")
    public OperationStatusRest metadataDeleteCommit(@PathVariable long experimentId, @PathVariable Long id) {
        ExperimentHyperParams hyperParams = experimentHyperParamsRepository.findById(id).orElse(null);
        if (hyperParams == null || experimentId != hyperParams.getExperiment().getId()) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.61 Hyper parameters misconfigured, try again.");
        }
        experimentHyperParamsRepository.deleteById(id);
        experimentCache.invalidate(experimentId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @GetMapping("/experiment-metadata-default-add-commit/{experimentId}")
    public OperationStatusRest metadataDefaultAddCommit(@PathVariable long experimentId) {
        Experiment experiment = experimentCache.findById(experimentId);
        if (experiment == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.63 experiment wasn't found, id: "+experimentId );
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

    @GetMapping("/experiment-snippet-delete-commit/{experimentId}/{id}")
    public OperationStatusRest snippetDeleteCommit(@PathVariable long experimentId, @PathVariable Long id) {
        ExperimentSnippet snippet = experimentSnippetRepository.findById(id).orElse(null);
        if (snippet == null || experimentId != snippet.getExperimentId()) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR,"#280.66 Snippet is misconfigured. Try again" );
        }
        experimentSnippetRepository.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @GetMapping("/experiment/{id}")
    public ExperimentData.ExperimentResult getExperiment(@PathVariable Long id) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new ExperimentData.ExperimentResult("#280.69 experiment wasn't found, id: "+id );
        }
        return new ExperimentData.ExperimentResult(experiment);
    }

    @PostMapping("/experiment-delete-commit")
    public OperationStatusRest deleteCommit(Long id) {
        Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "#280.71 experiment wasn't found, experimentId: " + id);
        }
        experimentSnippetRepository.deleteByExperimentId(id);
        experimentFeatureRepository.deleteByExperimentId(id);
        experimentCache.deleteById(id);

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @PostMapping("/experiment-clone-commit")
    public OperationStatusRest experimentCloneCommit(Long id) {
        final Experiment experiment = experimentCache.findById(id);
        if (experiment == null) {
            return new OperationStatusRest(Enums.OperationStatus.ERROR, "#280.73 experiment wasn't found, experimentId: " + id);
        }
        final Experiment e = new Experiment();
        e.setCode(StrUtils.incCopyNumber(experiment.getCode()));
        e.setName(StrUtils.incCopyNumber(experiment.getName()));
        e.setDescription(experiment.getDescription());
        e.setSeed(experiment.getSeed());
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

    @PostMapping("/task-rerun/{taskId}")
    public @ResponseBody OperationStatusRest rerunTask(@PathVariable long taskId) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            String es = "#280.75 Can't re-run task "+taskId+", task with such taskId wasn't found";
            log.warn(es);
            return new OperationStatusRest(Enums.OperationStatus.ERROR, es);
        }
        FlowInstance flowInstance = flowInstanceRepository.findById(task.getFlowInstanceId()).orElse(null);
        if (flowInstance == null) {
            String es = "#270.84 Can't re-run task "+taskId+", this task is orphan and doesn't belong to any flowInstance";
            log.warn(es);
            return new OperationStatusRest(Enums.OperationStatus.ERROR, es);
        }
        Task t = taskPersistencer.resetTask(taskId);
        return new OperationStatusRest(t!=null ? Enums.OperationStatus.OK : Enums.OperationStatus.ERROR);
    }

}