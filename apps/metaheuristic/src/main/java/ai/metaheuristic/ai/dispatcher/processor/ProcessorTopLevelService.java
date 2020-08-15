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

package ai.metaheuristic.ai.dispatcher.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.CommonSync;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.dispatcher.repositories.TaskRepository;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.task.TaskParamsYamlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ProcessorTopLevelService {

    private final Globals globals;
    private final ProcessorRepository processorRepository;
    private final ProcessorCache processorCache;
    private final ExecContextService execContextService;
    private final TaskRepository taskRepository;

    // Attention, this value must be greater than
    // ai.metaheuristic.ai.dispatcher.server.ServerService.SESSION_UPDATE_TIMEOUT
    // at least for 20 seconds
    public static final long PROCESSOR_TIMEOUT = TimeUnit.SECONDS.toMillis(140);

    public static String createNewSessionId() {
        return UUID.randomUUID().toString() + '-' + UUID.randomUUID().toString();
    }

    public ProcessorData.ProcessorsResult getProcessors(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(globals.processorRowsLimit, pageable);
        ProcessorData.ProcessorsResult result = new ProcessorData.ProcessorsResult();
        Slice<Long> ids = processorRepository.findAllByOrderByUpdatedOnDescId(pageable);
        List<ProcessorData.ProcessorStatus> ss = new ArrayList<>(pageable.getPageSize()+1);
        for (Long processorId : ids) {
            Processor processor = processorCache.findById(processorId);
            if (processor ==null) {
                continue;
            }
            ProcessorStatusYaml status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);

            String blacklistReason = processorBlacklisted(status);

            boolean isFunctionProblem = status.downloadStatuses.stream()
                    .anyMatch(s->s.functionState != Enums.FunctionState.none &&
                            s.functionState != Enums.FunctionState.ready &&
                            s.functionState != Enums.FunctionState.not_found &&
                            s.functionState != Enums.FunctionState.ok);

            ss.add(new ProcessorData.ProcessorStatus(
                    processor, System.currentTimeMillis() - processor.updatedOn < PROCESSOR_TIMEOUT,
                    isFunctionProblem,
                    blacklistReason!=null, blacklistReason,
                    processor.updatedOn,
                    (StringUtils.isNotBlank(status.ip) ? status.ip : "[unknown]"),
                    (StringUtils.isNotBlank(status.host) ? status.host : "[unknown]")
            ));
        }
        result.items =  new SliceImpl<>(ss, pageable, ids.hasNext());
        return result;
    }

    private @Nullable String processorBlacklisted(ProcessorStatusYaml status) {
        if (status.taskParamsVersion > TaskParamsYamlUtils.BASE_YAML_UTILS.getDefault().getVersion()) {
            return "Dispatcher is too old and can't communicate to this processor, needs to be upgraded";
        }
        return null;
    }

    public ProcessorData.ProcessorResult getProcessor(Long id) {
        Processor processor = processorCache.findById(id);
        if (processor==null) {
            return new ProcessorData.ProcessorResult("Processor wasn't found for id #"+ id);
        }
        //noinspection UnnecessaryLocalVariable
        ProcessorData.ProcessorResult r = new ProcessorData.ProcessorResult(processor);
        return r;
    }

    public ProcessorData.ProcessorResult saveProcessor(Processor processor) {
        Processor s = processorRepository.findByIdForUpdate(processor.getId());
        if (s==null) {
            return new ProcessorData.ProcessorResult("#807.05 processor wasn't found, processorId: " + processor.getId());
        }
        s.description = processor.description;
        //noinspection UnnecessaryLocalVariable
        ProcessorData.ProcessorResult r = new ProcessorData.ProcessorResult(processorCache.save(s));
        return r;
    }

    public OperationStatusRest deleteProcessorById(Long id) {
        Processor processor = processorCache.findById(id);
        if (processor == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#807.15 Processor wasn't found, processorId: " + id);
        }
        processorCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private static final CommonSync<Long> commonSync = new CommonSync<>();

    public void storeProcessorStatuses(@Nullable String processorIdAsStr, ProcessorCommParamsYaml.ReportProcessorStatus status, ProcessorCommParamsYaml.FunctionDownloadStatus functionDownloadStatus) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final Long processorId = Long.valueOf(processorIdAsStr);
        final ReentrantReadWriteLock.WriteLock lock = commonSync.getLock(processorId);
        log.debug("Before entering in sync block, storeProcessorStatus()");
        try {
            lock.lock();
            final Processor processor = processorRepository.findByIdForUpdate(processorId);
            if (processor == null) {
                // we throw ISE cos all checks have to be made early
                throw new IllegalStateException("Processor wasn't found for processorId: " + processorId);
            }
            ProcessorStatusYaml ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
            boolean isUpdated = false;
            if (isProcessorStatusDifferent(ss, status)) {
                ss.env = status.env;
                ss.gitStatusInfo = status.gitStatusInfo;
                ss.schedule = status.schedule;

                // Do not include updating of sessionId
                // ss.sessionId = command.status.sessionId;

                // Do not include updating of sessionCreatedOn!
                // ss.sessionCreatedOn = command.status.sessionCreatedOn;

                ss.ip = status.ip;
                ss.host = status.host;
                ss.errors = status.errors;
                ss.logDownloadable = status.logDownloadable;
                ss.taskParamsVersion = status.taskParamsVersion;
                ss.os = (status.os == null ? EnumsApi.OS.unknown : status.os);

                processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
                processor.updatedOn = System.currentTimeMillis();
                isUpdated = true;
            }
            if (isProcessorFunctionDownloadStatusDifferent(ss, functionDownloadStatus)) {
                ss.downloadStatuses = functionDownloadStatus.statuses.stream()
                        .map(o->new ProcessorStatusYaml.DownloadStatus(o.functionState, o.functionCode))
                        .collect(Collectors.toList());
                processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
                processor.updatedOn = System.currentTimeMillis();
                isUpdated = true;
            }
            if (isUpdated) {
                try {
                    log.debug("Save new processor status, processor: {}", processor);
                    processorCache.save(processor);
                } catch (ObjectOptimisticLockingFailureException e) {
                    log.warn("#807.105 ObjectOptimisticLockingFailureException was encountered\n" +
                            "new processor:\n{}\n" +
                            "db processor\n{}", processor, processorRepository.findById(processorId).orElse(null));

                    processorCache.clearCache();
                }
            }
            else {
                log.debug("Processor status is equal to the status stored in db");
            }
        } finally {
            lock.unlock();
        }
        log.debug("After leaving sync block");
    }

    public static boolean isProcessorStatusDifferent(ProcessorStatusYaml ss, ProcessorCommParamsYaml.ReportProcessorStatus status) {
        return
        !Objects.equals(ss.env, status.env) ||
        !Objects.equals(ss.gitStatusInfo, status.gitStatusInfo) ||
        !Objects.equals(ss.schedule, status.schedule) ||
        !Objects.equals(ss.ip, status.ip) ||
        !Objects.equals(ss.host, status.host) ||
        !Objects.equals(ss.errors, status.errors) ||
        ss.logDownloadable!=status.logDownloadable ||
        ss.taskParamsVersion!=status.taskParamsVersion||
        ss.os!=status.os;
    }

    public static boolean isProcessorFunctionDownloadStatusDifferent(ProcessorStatusYaml ss, ProcessorCommParamsYaml.FunctionDownloadStatus status) {
        if (ss.downloadStatuses.size()!=status.statuses.size()) {
            return true;
        }
        for (ProcessorStatusYaml.DownloadStatus downloadStatus : ss.downloadStatuses) {
            for (ProcessorCommParamsYaml.FunctionDownloadStatus.Status sds : status.statuses) {
                if (downloadStatus.functionCode.equals(sds.functionCode) && !downloadStatus.functionState.equals(sds.functionState)) {
                    return true;
                }
            }
        }
        return false;
    }

    // TODO Need to re-write this method
    // TODO 2020-01-18 why need to re-write?
    public void reconcileProcessorTasks(@Nullable String processorIdAsStr, List<ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus> statuses) {
        if (S.b(processorIdAsStr)) {
            return;
        }
        final long processorId = Long.parseLong(processorIdAsStr);
        List<Object[]> tasks = taskRepository.findAllByProcessorIdAndResultReceivedIsFalseAndCompletedIsFalse(processorId);
        for (Object[] obj : tasks) {
            long taskId = ((Number)obj[0]).longValue();
            Long assignedOn = obj[1]!=null ? ((Number)obj[1]).longValue() : null;

            boolean isFound = false;
            for (ProcessorCommParamsYaml.ReportProcessorTaskStatus.SimpleStatus status : statuses) {
                if (status.taskId == taskId) {
                    isFound = true;
                    break;
                }
            }

            boolean isExpired = assignedOn!=null && (System.currentTimeMillis() - assignedOn > 90_000);
            if (!isFound && isExpired) {
                log.info("De-assign task #{} from processor #{}", taskId, processorIdAsStr);
                log.info("\tstatuses: {}", statuses.stream().map( o -> Long.toString(o.taskId)).collect(Collectors.toList()));
                log.info("\ttasks: {}", tasks.stream().map( o -> ""+o[0] + ',' + o[1]).collect(Collectors.toList()));
                log.info("\tisFound: {}, is expired: {}", isFound, isExpired);
                OperationStatusRest result = execContextService.resetTask(taskId);
                if (result.status== EnumsApi.OperationStatus.ERROR) {
                    log.error("#179.10 Resetting of task #{} was failed. See log for more info.", taskId);
                }
            }
        }
    }


}
