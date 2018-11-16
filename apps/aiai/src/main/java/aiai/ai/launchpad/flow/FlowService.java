package aiai.ai.launchpad.flow;

import aiai.ai.Enums;
import aiai.ai.Globals;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.*;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.experiment.ExperimentCache;
import aiai.ai.launchpad.experiment.ExperimentProcessService;
import aiai.ai.launchpad.file_process.FileProcessService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class FlowService {

    private final Globals globals;
    private final FlowRepository flowRepository;
    private final FlowYamlUtils flowYamlUtils;
    private final ExperimentCache experimentCache;
    private final BinaryDataService binaryDataService;

    private final ExperimentProcessService experimentProcessService;
    private final FileProcessService fileProcessService;
    private final FlowInstanceRepository flowInstanceRepository;
    private final SnippetCache snippetCache;
    private final TaskRepository taskRepository;

    public FlowService(Globals globals, FlowRepository flowRepository, FlowYamlUtils flowYamlUtils, ExperimentCache experimentCache, BinaryDataService binaryDataService, ExperimentProcessService experimentProcessService, FileProcessService fileProcessService, FlowInstanceRepository flowInstanceRepository, SnippetCache snippetCache, TaskRepository taskRepository) {
        this.globals = globals;
        this.flowRepository = flowRepository;
        this.flowYamlUtils = flowYamlUtils;
        this.experimentCache = experimentCache;
        this.binaryDataService = binaryDataService;
        this.experimentProcessService = experimentProcessService;
        this.fileProcessService = fileProcessService;
        this.flowInstanceRepository = flowInstanceRepository;
        this.snippetCache = snippetCache;
        this.taskRepository = taskRepository;
    }

    @Data
    @NoArgsConstructor
    public static class TaskProducingResult {
        public Enums.FlowVerifyStatus flowVerifyStatus = Enums.FlowVerifyStatus.NOT_VERIFIED_YET_ERROR ;
        public Enums.FlowProducingStatus flowProducingStatus = Enums.FlowProducingStatus.NOT_PRODUCING_YET_ERROR;
        public List<Task> tasks = new ArrayList<>();
        public FlowYaml flowYaml;
        public FlowInstance flowInstance;

        public Enums.TaskProducingStatus getStatus() {
            if (flowVerifyStatus != Enums.FlowVerifyStatus.OK) {
                return Enums.TaskProducingStatus.VERIFY_ERROR;
            }
            if (flowProducingStatus!= Enums.FlowProducingStatus.OK) {
                return Enums.TaskProducingStatus.PRODUCING_ERROR;
            }
            return Enums.TaskProducingStatus.OK;
        }
    }

    public TaskProducingResult createTasks(Flow flow, String startWithResourcePoolCode) {
        TaskProducingResult result = new TaskProducingResult();
        result.flowVerifyStatus = verify(flow);
        if (result.flowVerifyStatus != Enums.FlowVerifyStatus.OK) {
            return result;
        }
        produce(result, flow, startWithResourcePoolCode);

        return result;
    }

    public Enums.FlowVerifyStatus verify(Flow flow) {
        if (flow==null) {
            return Enums.FlowVerifyStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(flow.code)) {
            return Enums.FlowVerifyStatus.FLOW_CODE_EMPTY_ERROR;
        }
        if (StringUtils.isBlank(flow.params)) {
            return Enums.FlowVerifyStatus.FLOW_PARAMS_EMPTY_ERROR;
        }
        FlowYaml flowYaml = flowYamlUtils.toFlowYaml(flow.params);
        if (flowYaml.getProcesses().isEmpty()) {
            return Enums.FlowVerifyStatus.NO_ANY_PROCESSES_ERROR;
        }
        if (StringUtils.isBlank(flowYaml.getProcesses().get(0).inputType)) {
            return Enums.FlowVerifyStatus.INPUT_TYPE_EMPTY_ERROR;
        }

        FlowYaml fl = flowYamlUtils.toFlowYaml(flow.getParams());

        Process lastProcess = null;
        boolean experimentPresent = false;
        for (Process process : fl.getProcesses()) {
            lastProcess = process;
            if (StringUtils.containsWhitespace(process.code) || StringUtils.contains(process.code, ',') ){
                return Enums.FlowVerifyStatus.PROCESS_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (StringUtils.containsWhitespace(process.inputResourceCode) || StringUtils.contains(process.inputResourceCode, ',') ){
                return Enums.FlowVerifyStatus.RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (StringUtils.containsWhitespace(process.outputResourceCode) || StringUtils.contains(process.outputResourceCode, ',') ){
                return Enums.FlowVerifyStatus.RESOURCE_CODE_CONTAINS_ILLEGAL_CHAR_ERROR;
            }
            if (process.type == Enums.ProcessType.EXPERIMENT) {
                experimentPresent = true;
                if (process.snippetCodes!=null && process.snippetCodes.size() > 0) {
                    return Enums.FlowVerifyStatus.SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR;
                }
                if (StringUtils.isBlank(process.code)) {
                    return Enums.FlowVerifyStatus.SNIPPET_NOT_DEFINED_ERROR;
                }
                Experiment e = experimentCache.findByCode(process.code);
                if (e==null) {
                    return Enums.FlowVerifyStatus.EXPERIMENT_NOT_FOUND_ERROR;
                }
                if (process.metas==null || process.metas.isEmpty()) {
                    return Enums.FlowVerifyStatus.EXPERIMENT_META_NOT_FOUND_ERROR;
                }

                Process.Meta m1 = process.getMeta("dataset");
                if (m1 ==null || StringUtils.isBlank(m1.getValue())) {
                    return Enums.FlowVerifyStatus.EXPERIMENT_META_DATASET_NOT_FOUND_ERROR;
                }
                Process.Meta m2 = process.getMeta("assembled-raw");
                if (m2 ==null || StringUtils.isBlank(m2.getValue())) {
                    return Enums.FlowVerifyStatus.EXPERIMENT_META_ASSEMBLED_RAW_NOT_FOUND_ERROR;
                }
                Process.Meta m3 = process.getMeta("feature");
                if (m3 ==null || StringUtils.isBlank(m3.getValue())) {
                    return Enums.FlowVerifyStatus.EXPERIMENT_META_FEATURE_NOT_FOUND_ERROR;
                }
            }
            else {
                if (process.getSnippetCodes() == null || process.getSnippetCodes().isEmpty()) {
                    return Enums.FlowVerifyStatus.SNIPPET_NOT_DEFINED_ERROR;
                }
                for (String snippetCode : process.snippetCodes) {
                    SnippetVersion sv = SnippetVersion.from(snippetCode);
                    Snippet snippet = snippetCache.findByNameAndSnippetVersion(sv.name, sv.version);
                    if (snippet==null) {
                        log.warn("Snippet wasn't found for code: {}, process: {}", snippetCode, process);
                        return Enums.FlowVerifyStatus.SNIPPET_NOT_FOUND_ERROR;
                    }
                }
            }
            if (process.parallelExec && (process.snippetCodes==null || process.snippetCodes.size()<2)) {
                return Enums.FlowVerifyStatus.NOT_ENOUGH_FOR_PARALLEL_EXEC_ERROR;
            }
            if (process.type== Enums.ProcessType.FILE_PROCESSING) {
                if (!process.parallelExec && process.snippetCodes.size()>1) {
                    return Enums.FlowVerifyStatus.TOO_MANY_SNIPPET_CODES_ERROR;
                }
            }
        }
        if (experimentPresent && lastProcess.type!=Enums.ProcessType.EXPERIMENT) {
            return  Enums.FlowVerifyStatus.EXPERIMENT_MUST_BE_LAST_PROCESS_ERROR;
        }
        return Enums.FlowVerifyStatus.OK;
    }

    @Data
    public static class ProduceTaskResult {
        public Enums.FlowProducingStatus status;
        public List<String> outputResourceCodes;
    }

    private void produce(TaskProducingResult result, Flow flow, String startWithResourcePoolCode) {
        List<String> inputResourceCodes = binaryDataService.getResourceCodesInPool(startWithResourcePoolCode);
        if (inputResourceCodes==null || inputResourceCodes.isEmpty()) {
            result.flowProducingStatus = Enums.FlowProducingStatus.INPUT_POOL_DOESNT_EXIST_ERROR;
            return;
        }

        FlowInstance fi = new FlowInstance();
        fi.setFlowId(flow.getId());
        fi.setCreatedOn(System.currentTimeMillis());
        fi.setCompleted(false);
        fi.setCompletedOn(null);
        fi.setInputResourcePoolCode(startWithResourcePoolCode);
        fi.setCompletedOrder(0);

        flowInstanceRepository.save(fi);
        result.flowInstance = fi;

        result.flowYaml = flowYamlUtils.toFlowYaml(flow.getParams());
        int idx = 0;
        result.flowProducingStatus = Enums.FlowProducingStatus.OK;
        List<String> outputResourceCode;
        for (Process process : result.flowYaml.getProcesses()) {
            ++idx;

            ProduceTaskResult produceTaskResult;
            switch(process.type) {
                case FILE_PROCESSING:
                    produceTaskResult = fileProcessService.produceTasks(flow, fi, process, idx, inputResourceCodes);

                    break;
                case EXPERIMENT:
                    produceTaskResult = experimentProcessService.produceTasks(flow, fi, process, idx, inputResourceCodes);
                    break;
                default:
                    throw new IllegalStateException("Unknown process type");
            }
            if (produceTaskResult.status!= Enums.FlowProducingStatus.OK) {
                result.flowProducingStatus = produceTaskResult.status;
                return;
            }
            inputResourceCodes = produceTaskResult.outputResourceCodes;
        }
    }

    public void markOrderAsCompleted(FlowInstance flowInstance) {
        for (Task task : taskRepository.findForCompletion(flowInstance.getId(), flowInstance.getCompletedOrder())) {
            if (!task.isCompleted) {
                return;
            }
        }
        flowInstance.setCompletedOrder(flowInstance.getCompletedOrder()+1);
        flowInstanceRepository.save(flowInstance);
    }


}
