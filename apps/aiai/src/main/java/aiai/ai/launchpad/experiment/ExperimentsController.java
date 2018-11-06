/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
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

package aiai.ai.launchpad.experiment;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.core.ArtifactStatus;
import aiai.ai.core.ExecProcessService;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.experiment.dataset.DatasetCache;
import aiai.ai.launchpad.env.EnvService;
import aiai.ai.launchpad.experiment.feature.FeatureExecStatus;
import aiai.ai.launchpad.repositories.*;
import aiai.ai.launchpad.snippet.SnippetService;
import aiai.ai.snippet.SnippetCode;
import aiai.ai.utils.ControllerUtils;
import aiai.ai.utils.SimpleSelectOption;
import aiai.ai.utils.StrUtils;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import aiai.apps.commons.yaml.snippet.SnippetType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/**
 * User: Serg
 * Date: 12.06.2017
 * Time: 20:22
 */
@Controller
@RequestMapping("/launchpad")
@Slf4j
@Profile("launchpad")
public class ExperimentsController {

    @Data
    public static class Result {
        public Slice<Experiment> items;
    }

    @Data
    public static class SequencesResult {
        public Slice<Task> items;
    }

    @Data
    public static class ConsoleResult {
        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class SimpleConsoleOutput {
            public int exitCode;
            public boolean isOk;
            public String console;
        }
        public final List<SimpleConsoleOutput> items = new ArrayList<>();
    }

    @Data
    public static class SnippetResult {
        public List<SimpleSelectOption> selectOptions = new ArrayList<>();
        public List<ExperimentSnippet> snippets = new ArrayList<>();

        public void sortSnippetsByOrder() {
//            snippets.sort(Comparator.comparingInt(ExperimentSnippet::getOrder));
        }
    }

    @Data
    public static class ExperimentResult {
        public Dataset dataset;
        public final List<SimpleSelectOption> allDatasetOptions = new ArrayList<>();
        public List<ExperimentFeature> features;
        public boolean isCanBeLaunched;
        public Map<String, Env> envs = new HashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleExperiment {
        public String name;
        public String description;
        public int seed;
        public String epoch;
        public long id;

        public static SimpleExperiment to(Experiment e) {
            return new SimpleExperiment(e.getName(), e.getDescription(), e.getSeed(), e.getEpoch(), e.getId());
        }
    }

    private final Globals globals;

    private final DatasetRepository datasetRepository;
    private final FeatureRepository featureRepository;
    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final ExperimentRepository experimentRepository;
    private final ExperimentService experimentService;
    private final ExperimentHyperParamsRepository experimentHyperParamsRepository;
    private final ExperimentSnippetRepository experimentSnippetRepository;
    private final ExperimentFeatureRepository experimentFeatureRepository;
    private final TaskRepository taskRepository;
    private final ExperimentSequenceWithSpecRepository experimentSequenceWithSpecRepository;
    private final DatasetCache datasetCache;
    private final EnvService envService;

    public ExperimentsController(Globals globals, DatasetRepository datasetRepository, FeatureRepository featureRepository, SnippetRepository snippetRepository, ExperimentRepository experimentRepository, ExperimentHyperParamsRepository experimentHyperParamsRepository, SnippetService snippetService, ExperimentService experimentService, ExperimentSnippetRepository experimentSnippetRepository, ExperimentFeatureRepository experimentFeatureRepository, TaskRepository taskRepository, ExperimentSequenceWithSpecRepository experimentSequenceWithSpecRepository, DatasetCache datasetCache, EnvService envService) {
        this.globals = globals;
        this.datasetRepository = datasetRepository;
        this.featureRepository = featureRepository;
        this.snippetRepository = snippetRepository;
        this.experimentRepository = experimentRepository;
        this.experimentHyperParamsRepository = experimentHyperParamsRepository;
        this.snippetService = snippetService;
        this.experimentService = experimentService;
        this.experimentSnippetRepository = experimentSnippetRepository;
        this.experimentFeatureRepository = experimentFeatureRepository;
        this.taskRepository = taskRepository;
        this.experimentSequenceWithSpecRepository = experimentSequenceWithSpecRepository;
        this.datasetCache = datasetCache;
        this.envService = envService;
    }

    @GetMapping("/experiments")
    public String init(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable, @ModelAttribute("errorMessage") final String errorMessage) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        result.items = experimentRepository.findAll(pageable);
        return "launchpad/experiments";
    }

    // for AJAX
    @PostMapping("/experiments-part")
    public String getExperiments(@ModelAttribute Result result, @PageableDefault(size = 5) Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.experimentRowsLimit, pageable);
        result.items = experimentRepository.findAll(pageable);
        return "launchpad/experiments :: table";
    }

    @PostMapping("/experiment-feature-progress-part/{experimentId}/{featureId}/{params}/part")
    public String getSequncesPart(Model model, @PathVariable Long experimentId, @PathVariable Long featureId, @PathVariable String[] params, @PageableDefault(size = 10) Pageable pageable) {
        Experiment experiment= experimentRepository.findById(experimentId).orElse(null);
        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);

        SequencesResult result = new SequencesResult();
        result.items = experimentService.findExperimentSequence(ControllerUtils.fixPageSize(10, pageable), experiment, feature, params);

        model.addAttribute("result", result);
        model.addAttribute("experiment", experiment);
        model.addAttribute("feature", feature);
        model.addAttribute("consoleResult", new ConsoleResult());

        return "launchpad/experiment-feature-progress :: fragment-table";
    }

    @PostMapping("/experiment-feature-plot-data-part/{experimentId}/{featureId}/{params}/{paramsAxis}/part")
    public @ResponseBody ExperimentService.PlotData getPlotData(Model model, @PathVariable Long experimentId, @PathVariable Long featureId,
                                                                @PathVariable String[] params, @PathVariable String[] paramsAxis) {
        Experiment experiment= experimentRepository.findById(experimentId).orElse(null);
        ExperimentFeature feature = experimentFeatureRepository.findById(featureId).orElse(null);

        //noinspection UnnecessaryLocalVariable
        ExperimentService.PlotData data = experimentService.findExperimentSequenceForPlot(experiment, feature, params, paramsAxis);
        return data;
    }

    @PostMapping("/experiment-feature-progress-console-part/{id}")
    public String getSequencesConsolePart(Model model, @PathVariable(name="id") Long sequenceId) {
        ConsoleResult result = new ConsoleResult();
        Task sequence = taskRepository.findById(sequenceId).orElse(null);
        if (sequence!=null) {
            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(sequence.getSnippetExecResults());
            final ExecProcessService.Result execResult = snippetExec.getExec();
            result.items.add( new ConsoleResult.SimpleConsoleOutput(execResult.exitCode, execResult.isOk, execResult.console));
        }
        model.addAttribute("consoleResult", result);

        return "launchpad/experiment-feature-progress :: fragment-console-table";
    }

    @GetMapping(value = "/experiment-feature-progress/{experimentId}/{featureId}")
    public String getSequences(Model model, @PathVariable Long experimentId, @PathVariable Long featureId, final RedirectAttributes redirectAttributes ) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#280.01 experiment wasn't found, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }

        ExperimentFeature experimentFeature = experimentFeatureRepository.findById(featureId).orElse(null);
        if (experimentFeature == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#280.02 feature wasn't found, experimentFeatureId: " + featureId);
            return "redirect:/launchpad/experiments";
        }

        Map<String, Object> map = experimentService.prepareExperimentFeatures(experiment, experimentFeature);
        model.addAllAttributes(map);

        return "launchpad/experiment-feature-progress";
    }


    @GetMapping(value = "/experiment-add")
    public String add(@ModelAttribute("experiment") Experiment experiment) {
        experiment.setSeed(1);
        return "launchpad/experiment-add-form";
    }

    @GetMapping(value = "/experiment-info/{id}")
    public String info(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes, @ModelAttribute("errorMessage") final String errorMessage ) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#282.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        for (ExperimentHyperParams hyperParams : experiment.getHyperParams()) {
            if (StringUtils.isBlank(hyperParams.getValues())) {
                continue;
            }
            ExperimentUtils.NumberOfVariants variants = ExperimentUtils.getNumberOfVariants(hyperParams.getValues());
            hyperParams.setVariants( variants.status ?variants.count : 0 );
        }
        if (experiment.getDatasetId()==null) {
            model.addAttribute("infoMessages", Collections.singleton("Launch is disabled, dataset isn't assigned"));
        }

        ExperimentResult experimentResult = new ExperimentResult();
        Dataset dataset = getDatasetAndCheck(experiment);
        experimentResult.dataset = dataset;
        experimentResult.features = experimentFeatureRepository.findByExperimentId(experiment.getId());
        experimentResult.features.sort( (ExperimentFeature o1, ExperimentFeature o2) -> (Boolean.compare(o2.isFinished, o1.isFinished)));

        experimentResult.isCanBeLaunched = isCanBeLaunched_FirstCheck(experiment);
        if (experimentResult.isCanBeLaunched) {
            if (dataset.getAssemblySnippet()==null || dataset.getDatasetSnippet()==null ||
                dataset.getRawAssemblingStatus()!= ArtifactStatus.OK.value ||
                dataset.getDatasetProducingStatus()!= ArtifactStatus.OK.value ||
                    dataset.getFeatures()==null || dataset.getFeatures().isEmpty()
            ) {
                experimentResult.isCanBeLaunched = false;
            }
            else {
                for (Feature feature : dataset.getFeatures()) {
                    if (feature.getFeatureStatus()!=ArtifactStatus.OK.value) {
                        experimentResult.isCanBeLaunched = false;
                        break;
                    }
                }
            }
        }
        experimentResult.envs.putAll( envService.envsAsMap() );

        model.addAttribute("experiment", experiment);
        model.addAttribute("experimentResult", experimentResult);
        return "launchpad/experiment-info";
    }

    private boolean isCanBeLaunched_FirstCheck(Experiment experiment) {
        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());
        return experimentSnippets.size() > 1 && experiment.getExecState() != Enums.TaskExecState.FINISHED.code &&
                !experiment.isLaunched() && experiment.getDatasetId() != null;
    }

    @GetMapping(value = "/experiment-edit/{id}")
    public String edit(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        final Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#275.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        Iterable<Snippet> snippets = snippetRepository.findAll();
        SnippetResult snippetResult = new SnippetResult();

        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());
//        snippetService.sortSnippetsByOrder(experimentSnippets);
        snippetResult.snippets = experimentSnippets;
        final List<SnippetType> types = Arrays.asList(SnippetType.fit, SnippetType.predict);
        snippetResult.selectOptions = snippetService.getSelectOptions(snippets,
                snippetResult.snippets.stream().map(o -> new SnippetCode(o.getId(), o.getSnippetCode())).collect(Collectors.toList()),
                (s) -> {
                    if (!types.contains(SnippetType.valueOf(s.type)) ) {
                        return true;
                    }
                    if (SnippetType.fit.equals(s.type) && snippetService.hasFit(experimentSnippets)) {
                        return true;
                    }
                    if (SnippetType.predict.equals(s.type) && snippetService.hasPredict(experimentSnippets)) {
                        return true;
                    }
                    return false;
                });

        ExperimentResult experimentResult = new ExperimentResult();
        Dataset dataset = getDatasetAndCheck(experiment);
        if (dataset==null) {
            for (Dataset ds : datasetRepository.findAll()) {
                experimentResult.allDatasetOptions.add(new SimpleSelectOption(ds.getId().toString(), String.format("Id: %d; %s", ds.getId(), ds.getName())));
            }
        }
        experimentResult.dataset = dataset;
        snippetResult.sortSnippetsByOrder();
        model.addAttribute("experiment", experiment);
        model.addAttribute("simpleExperiment", SimpleExperiment.to(experiment));
        model.addAttribute("experimentResult", experimentResult);
        model.addAttribute("snippetResult", snippetResult);
        return "launchpad/experiment-edit-form";
    }

    private Dataset getDatasetAndCheck(Experiment experiment) {
        Dataset dataset = null;
        if (experiment.getDatasetId()!=null) {
            dataset = datasetCache.findById(experiment.getDatasetId());
            if (dataset == null) {
                log.warn("dataset wasn't found for id {}", experiment.getDatasetId());
                experiment.setDatasetId(null);
                experimentRepository.save(experiment);
            }
        }
        return dataset;
    }

    @PostMapping("/experiment-add-form-commit")
    public String addFormCommit(Model model, Experiment experiment) {
        return processCommit(model, experiment,  "launchpad/experiment-add-form", "redirect:/launchpad/experiments");
    }

    @PostMapping("/experiment-edit-form-commit")
    public String editFormCommit(Model model, SimpleExperiment simpleExperiment, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(simpleExperiment.id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#281.01 experiment wasn't found, experimentId: " + simpleExperiment.id);
            return "redirect:/launchpad/experiments";
        }
        experiment.setName(simpleExperiment.getName());
        experiment.setDescription(simpleExperiment.getDescription());
        experiment.setSeed(simpleExperiment.getSeed());
        experiment.setEpoch(simpleExperiment.getEpoch());
        return processCommit(model, experiment,  "launchpad/experiment-edit-form", "redirect:/launchpad/experiment-edit/"+experiment.getId());
    }

    private String processCommit(Model model, Experiment experiment, String errorTarget, String normalTarget) {
        ExperimentUtils.NumberOfVariants numberOfVariants = ExperimentUtils.getNumberOfVariants(experiment.getEpoch());
        if (!numberOfVariants.status) {
            model.addAttribute("errorMessage", numberOfVariants.getError());
            return errorTarget;
        }
        experiment.setEpochVariant(numberOfVariants.getCount());
        experimentRepository.save(experiment);
        return normalTarget;
    }

    public static void sortSnippetsByType(List<ExperimentSnippet> snippets) {
        snippets.sort(Comparator.comparing(ExperimentSnippet::getType));
    }

    @PostMapping("/experiment-dataset-assign-commit/{id}")
    public String datasetAddCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes ) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#287.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getDatasetId()!=null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#287.02 Dataset is already assigned to this experiment, experimentId: " + id);
            return "redirect:/launchpad/experiment-edit/"+id;
        }
        Dataset dataset = datasetCache.findById(Long.parseLong(code));
        if (dataset==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#287.03 Wrong datasetId: "+code+", experimentId: " + id);
            return "redirect:/launchpad/experiment-edit/"+id;
        }

        experiment.setDatasetId(dataset.getId());
        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @GetMapping("/experiment-dataset-unassign-commit/{id}")
    public String datasetAddCommit(@PathVariable Long id, final RedirectAttributes redirectAttributes ) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#288.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getDatasetId()==null) {
            // dataset was already unassigned
            return "redirect:/launchpad/experiment-edit/"+id;
        }
        experiment.setDatasetId(null);
        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @PostMapping("/experiment-metadata-add-commit/{id}")
    public String metadataAddCommit(@PathVariable Long id, String key, String value, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            return "redirect:/launchpad/experiments";
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#289.51 hyper param's key and value must not be null, key: "+key+", value: " + value );
            return "redirect:/launchpad/experiment-edit/"+id;
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }
        String keyFinal = key.trim();
        boolean isExist = experiment.getHyperParams().stream().map(ExperimentHyperParams::getKey).anyMatch(keyFinal::equals);
        if (isExist) {
            redirectAttributes.addFlashAttribute("errorMessage", "#289.52 hyper parameter "+key+" already exist");
            return "redirect:/launchpad/experiment-edit/"+id;
        }

        ExperimentHyperParams m = new ExperimentHyperParams();
        m.setExperiment(experiment);
        m.setKey(keyFinal);
        m.setValues(value.trim());
        experiment.getHyperParams().add(m);

        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @PostMapping("/experiment-metadata-edit-commit/{id}")
    public String metadataEditCommit(@PathVariable Long id, String key, String value, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#289.01 experiment wasn't found, id: "+id );
            return "redirect:/launchpad/experiments";
        }
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) ) {
            redirectAttributes.addFlashAttribute("errorMessage", "#289.02 hyper param's key and value must not be null, key: "+key+", value: " + value );
            return "redirect:/launchpad/experiment-edit/"+id;
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

        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @PostMapping("/experiment-snippet-add-commit/{id}")
    public String snippetAddCommit(@PathVariable Long id, String code, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#290.01 experiment wasn't found, id: "+id );
            return "redirect:/launchpad/experiments";
        }
        Long experimentId = experiment.getId();
        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experimentId);
        ExperimentSnippet ts = new ExperimentSnippet();
        ts.setRefId(experimentId);
        ts.setTaskType(Enums.TaskType.Experiment.code);
        ts.setSnippetCode( code );

        List<ExperimentSnippet> list = new ArrayList<>(experimentSnippets);
        list.add(ts);

        sortSnippetsByType(list);

        experimentSnippetRepository.saveAll(list);
        return "redirect:/launchpad/experiment-edit/"+id;
    }

    @GetMapping("/experiment-metadata-delete-commit/{experimentId}/{id}")
    public String metadataDeleteCommit(@PathVariable long experimentId, @PathVariable Long id, final RedirectAttributes redirectAttributes) {
        ExperimentHyperParams hyperParams = experimentHyperParamsRepository.findById(id).orElse(null);
        if (hyperParams == null || experimentId != hyperParams.getExperiment().getId()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#291.01 Hyper parameters misconfigured, try again.");
            return "redirect:/launchpad/experiment-edit/" + experimentId;
        }
        experimentHyperParamsRepository.deleteById(id);
        return "redirect:/launchpad/experiment-edit/"+experimentId;
    }

    @GetMapping("/experiment-metadata-default-add-commit/{experimentId}")
    public String metadataDefaultAddCommit(@PathVariable long experimentId, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#292.01 experiment wasn't found, id: "+experimentId );
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getHyperParams()==null) {
            experiment.setHyperParams(new ArrayList<>());
        }

        add(experiment, "RNN", "[LSTM, GRU, SimpleRNN]");
        add(experiment, "activation", "[hard_sigmoid, softplus, softmax, softsign, relu, tanh, sigmoid, linear, elu]");
        add(experiment, "optimizer", "[sgd, nadam, adagrad, adadelta, rmsprop, adam, adamax]");
        add(experiment, "batch_size", "[20, 40, 60]");
        add(experiment, "time_steps", "[5, 40, 60]");

        experimentRepository.save(experiment);
        return "redirect:/launchpad/experiment-edit/"+experimentId;
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
    public String snippetDeleteCommit(@PathVariable long experimentId, @PathVariable Long id, final RedirectAttributes redirectAttributes) {
        ExperimentSnippet snippet = experimentSnippetRepository.findById(id).orElse(null);
        if (snippet == null || experimentId != snippet.getRefId()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#293.01 Snippet is misconfigured. Try again" );
            return "redirect:/launchpad/experiment-edit/" + experimentId;
        }
        experimentSnippetRepository.deleteById(id);
        return "redirect:/launchpad/experiment-edit/"+experimentId;
    }

    @GetMapping("/experiment-delete/{id}")
    public String delete(@PathVariable Long id, Model model, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#294.01 experiment wasn't found, id: "+id );
            return "redirect:/launchpad/experiments";
        }
        model.addAttribute("experiment", experiment);
        return "launchpad/experiment-delete";
    }

    @PostMapping("/experiment-delete-commit")
    public String deleteCommit(Long id, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#283.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        experimentSnippetRepository.deleteByTaskTypeAndRefId(Enums.TaskType.Experiment.code, id);
        taskRepository.deleteByExperimentId(id);
        experimentFeatureRepository.deleteByExperimentId(id);
        experimentRepository.deleteById(id);
        return "redirect:/launchpad/experiments";
    }

    @PostMapping("/experiment-clone-commit")
    public String cloneCommit(Long id, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(id).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#284.01 experiment wasn't found, experimentId: " + id);
            return "redirect:/launchpad/experiments";
        }
        Experiment trg = new Experiment();
        trg.setDatasetId(experiment.getDatasetId());
        trg.setEpoch(experiment.getEpoch());
        trg.setEpochVariant(experiment.getEpochVariant());
        trg.setSeed(experiment.getSeed());
        trg.setName( StrUtils.incCopyNumber(experiment.getName()) );
        trg.setDescription( experiment.getDescription() );
        trg.setAllSequenceProduced(false);
        trg.setFeatureProduced(false);
        trg.setCreatedOn(System.currentTimeMillis());
        trg.setLaunchedOn(null);
        trg.setLaunched(false);
        trg.setHyperParams(new ArrayList<>());
        trg.setExecState(Enums.TaskExecState.NONE.code);
        experimentRepository.save(trg);

        List<ExperimentSnippet> experimentSnippets = snippetService.getTaskSnippetsForExperiment(experiment.getId());

        for (ExperimentSnippet snippet : experimentSnippets) {
            ExperimentSnippet trgSnippet = new ExperimentSnippet();
            BeanUtils.copyProperties(snippet, trgSnippet);
            trgSnippet.setId(null);
            trgSnippet.setVersion(null);
            trgSnippet.setRefId(trg.getId());
            experimentSnippetRepository.save(trgSnippet);
        }
        for (ExperimentHyperParams params1 : experiment.getHyperParams()) {
            ExperimentHyperParams trgParam = new ExperimentHyperParams();
            BeanUtils.copyProperties(params1, trgParam);
            trgParam.setId(null);
            trgParam.setVersion(null);
            trgParam.setExperiment(trg);
            trg.getHyperParams().add(trgParam);
            experimentHyperParamsRepository.save(trgParam);
        }

        return "redirect:/launchpad/experiments";
    }

    @PostMapping("/task-rerun/{id}")
    public @ResponseBody boolean rerunSequence(@PathVariable long id) {
        if (true) throw new IllegalStateException("Not implemented yet");
/*
        Task seq = taskRepository.findById(id).orElse(null);
        if (seq == null) {
            log.warn("#291.01 Can't re-run sequence {}, sequence with such id wasn't found", id);
            return false;
        }
        ExperimentFeature feature = experimentFeatureRepository.findById(seq.experimentFeatureId).orElse(null);
        if (feature == null) {
            log.warn("#292.01 Can't re-run sequence {}, ExperimentFeature wasn't found for this sequence", id);
            return false;
        }
        Experiment experiment = experimentRepository.findById(seq.getExperimentId()).orElse(null);
        if (experiment == null) {
            log.warn("#293.01 Can't re-run sequence {}, this sequence is orphan and doesn't belong to any experiment", id);
            return false;
        }

        seq.setCompletedOn(null);
        seq.setCompleted(false);
        seq.setMetrics(null);
        seq.setSnippetExecResults(null);
        seq.setStationId(null);
        seq.setAssignedOn(null);
        taskRepository.save(seq);

        feature.setExecStatus(FeatureExecStatus.unknown.code);
        feature.setFinished(false);
        feature.setInProgress(true);
        experimentFeatureRepository.save(feature);

*/
        return true;
    }

    @GetMapping("/experiment-launch/{experimentId}")
    public String launch(@PathVariable long experimentId, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#284.01 experiment wasn't found, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.isLaunched()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#284.02 experiment was already launched, experimentId: " + experimentId);
            return "redirect:/launchpad/experiment-info/"+experimentId;
        }

        if (experiment.getDatasetId()==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#284.03 dataset wasn't assigned, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        Dataset dataset = datasetCache.findById(experiment.getDatasetId());
        if (dataset == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#284.04 experiment has broken link to dataset. Need to reassign a dataset.");
            return "redirect:/launchpad/experiments";
        }
        if (!dataset.isLocked()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "#284.05 You have to lock and process dataset before running experiment.");
            return "redirect:/launchpad/experiments";
        }

        experiment.setLaunched(true);
        experiment.setLaunchedOn(System.currentTimeMillis());
        experiment.setExecState(Enums.TaskExecState.STARTED.code);
        experimentRepository.save(experiment);

        return "redirect:/launchpad/experiment-info/"+experimentId;
    }

    @GetMapping("/experiment-target-exec-state/{state}/{experimentId}")
    public String stop(@PathVariable String state, @PathVariable long experimentId, final RedirectAttributes redirectAttributes) {
        Experiment experiment = experimentRepository.findById(experimentId).orElse(null);
        if (experiment == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#285.01 experiment wasn't found, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        if (experiment.getDatasetId()==null) {
            redirectAttributes.addFlashAttribute("errorMessage", "#285.03 dataset wasn't assigned, experimentId: " + experimentId);
            return "redirect:/launchpad/experiments";
        }
        if(!experiment.isLaunched()) {
            redirectAttributes.addFlashAttribute("errorMessage", "#285.04 Experiment wasn't started yet, experimentId: " + experimentId);
            return "redirect:/launchpad/experiment-info/"+experimentId;
        }
        Enums.TaskExecState execState = Enums.TaskExecState.valueOf(state.toUpperCase());

        if ((execState== Enums.TaskExecState.STARTED && experiment.getExecState()== Enums.TaskExecState.STARTED.code) ||
                (execState== Enums.TaskExecState.STOPPED && experiment.getExecState()== Enums.TaskExecState.STOPPED.code)) {
            redirectAttributes.addFlashAttribute("errorMessage", "#285.05 Experiment is already in target state: " + execState.toString());
            return "redirect:/launchpad/experiment-info/"+experimentId;

        }
        experiment.setExecState(execState.code);
        experimentRepository.save(experiment);

        return "redirect:/launchpad/experiment-info/"+experimentId;
    }

}
