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

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.beans.Processor;
import ai.metaheuristic.ai.dispatcher.data.ProcessorData;
import ai.metaheuristic.ai.dispatcher.repositories.ProcessorRepository;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.utils.TxUtils;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 9/29/2020
 * Time: 7:54 PM
 */
@SuppressWarnings("DuplicatedCode")
@Slf4j
@Profile("dispatcher")
@Service
@RequiredArgsConstructor
public class ProcessorTransactionService {

    private final ProcessorRepository processorRepository;
    private final ProcessorCache processorCache;
    private final ProcessorSyncService processorSyncService;

    private static final int COOL_DOWN_MINUTES = 2;
    private static final long LOG_FILE_REQUEST_COOL_DOWN_TIMEOUT = TimeUnit.MINUTES.toMillis(COOL_DOWN_MINUTES);

    private static String createNewSessionId() {
        return UUID.randomUUID().toString() + '-' + UUID.randomUUID().toString();
    }

    @Transactional
    public OperationStatusRest requestLogFile(Long processorId) {
        processorSyncService.checkWriteLockPresent(processorId);

        Processor processor = processorCache.findById(processorId);
        if (processor==null) {
            String es = "#807.040 Can't find Processor #" + processorId;
            log.warn(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }
        ProcessorStatusYaml psy = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        if (psy.log==null) {
            psy.log = new ProcessorStatusYaml.Log();
        }
        if (psy.log.logRequested) {
            return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Log file for processor #"+processorId+" was already requested", null);
        }
        if (psy.log.logReceivedOn != null) {
            long diff = System.currentTimeMillis() - psy.log.logReceivedOn;
            if (diff<=LOG_FILE_REQUEST_COOL_DOWN_TIMEOUT) {
                return new OperationStatusRest(EnumsApi.OperationStatus.OK,
                        S.f("Log file can be requested not often than 1 time in %s minutes. Cool down in %d seconds",
                                COOL_DOWN_MINUTES, (LOG_FILE_REQUEST_COOL_DOWN_TIMEOUT - diff)/1_000),
                        null);
            }
        }

        psy.log.logRequested = true;
        processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(psy);
        processorCache.save(processor);
        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Log file for processor #"+processorId+" was requested successfully", null);
    }

    @Transactional
    public Void setLogFileReceived(Long processorId) {
        processorSyncService.checkWriteLockPresent(processorId);

        Processor processor = processorCache.findById(processorId);
        if (processor==null) {
            log.warn("#807.045 Can't find Processor #{}", processorId);
            return null;
        }
        ProcessorStatusYaml psy = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        if (psy.log==null) {
            psy.log = new ProcessorStatusYaml.Log();
        }
        psy.log.logReceivedOn = System.currentTimeMillis();
        processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(psy);
        processorCache.save(processor);
        return null;

    }

    @Nullable
    @Transactional
    public DispatcherCommParamsYaml.ReAssignProcessorId assignNewSessionIdWithTx(Long processorId, ProcessorStatusYaml ss) {
        processorSyncService.checkWriteLockPresent(processorId);
        Processor processor = processorCache.findById(processorId);
        if (processor==null) {
            log.warn("#807.040 Can't find Processor #{}", processorId);
            return null;
        }
        return assignNewSessionId(processor, ss);

    }

    private DispatcherCommParamsYaml.ReAssignProcessorId assignNewSessionId(Processor processor, ProcessorStatusYaml ss) {
        TxUtils.checkTxExists();
        processorSyncService.checkWriteLockPresent(processor.id);

        ss.sessionId = createNewSessionId();
        ss.sessionCreatedOn = System.currentTimeMillis();
        processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
        processor.updatedOn = ss.sessionCreatedOn;
        processorCache.save(processor);
        // the same processorId but new sessionId
        return new DispatcherCommParamsYaml.ReAssignProcessorId(processor.getId(), ss.sessionId);
    }

    @Transactional
    public ProcessorData.ProcessorWithSessionId getNewProcessorId() {
        String sessionId = createNewSessionId();
        ProcessorStatusYaml psy = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown),
                "", sessionId, System.currentTimeMillis(), "", "", null, false,
                1, EnumsApi.OS.unknown, Consts.UNKNOWN_INFO, null);

        final Processor p = createProcessor(null, null, psy);
        return new ProcessorData.ProcessorWithSessionId(p, sessionId);
    }

    @Transactional
    public Processor createProcessor(@Nullable String description, @Nullable String ip, ProcessorStatusYaml ss) {
        Processor p = new Processor();
        p.setStatus(ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss));
        p.description= description;
        p.ip = ip;
        return processorCache.save(p);
    }

    @Transactional
    public void deleteById(Long id) {
        processorSyncService.checkWriteLockPresent(id);
        processorCache.deleteById(id);
    }

    @Transactional
    public ProcessorData.ProcessorResult updateDescription(Long processorId, @Nullable String desc) {
        processorSyncService.checkWriteLockPresent(processorId);
        Processor s = processorCache.findById(processorId);
        if (s==null) {
            return new ProcessorData.ProcessorResult("#807.060 processor wasn't found, processorId: " + processorId);
        }
        s.description = desc;
        ProcessorData.ProcessorResult r = new ProcessorData.ProcessorResult(processorCache.save(s));
        return r;
    }

    @Transactional
    public OperationStatusRest deleteProcessorById(Long id) {
        processorSyncService.checkWriteLockPresent(id);
        Processor processor = processorCache.findById(id);
        if (processor == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#807.080 Processor wasn't found, processorId: " + id);
        }
        processorCache.deleteById(id);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    @Transactional
    public Void storeProcessorStatuses(Long processorId, ProcessorCommParamsYaml.ReportProcessorStatus status, ProcessorCommParamsYaml.FunctionDownloadStatus functionDownloadStatus) {
        processorSyncService.checkWriteLockPresent(processorId);

        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            // we throw ISE cos all checks have to be made early
            throw new IllegalStateException("#807.100 Processor wasn't found for processorId: " + processorId);
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
            ss.currDir = status.currDir;

            processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
            processor.updatedOn = System.currentTimeMillis();
            isUpdated = true;
        }
        if (isProcessorFunctionDownloadStatusDifferent(ss, functionDownloadStatus)) {
            ss.downloadStatuses = functionDownloadStatus.statuses.stream()
                    .map(o -> new ProcessorStatusYaml.DownloadStatus(o.functionState, o.functionCode))
                    .collect(Collectors.toList());
            processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
            processor.updatedOn = System.currentTimeMillis();
            isUpdated = true;
        }
        if (isUpdated) {
            try {
                log.debug("#807.120 Save new processor status, processor: {}", processor);
                processorCache.save(processor);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("#807.140 ObjectOptimisticLockingFailureException was encountered\n" +
                        "new processor:\n{}\n" +
                        "db processor\n{}", processor, processorRepository.findById(processorId).orElse(null));

                processorCache.clearCache();
            }
        } else {
            log.debug("#807.160 Processor status is equal to the status stored in db");
        }
        return null;
    }

    private static boolean isProcessorFunctionDownloadStatusDifferent(ProcessorStatusYaml ss, ProcessorCommParamsYaml.FunctionDownloadStatus status) {
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
                        ss.os!=status.os ||
                        !Objects.equals(ss.currDir, status.currDir);
    }

    @Transactional
    public DispatcherCommParamsYaml.ReAssignProcessorId reassignProcessorId(@Nullable String remoteAddress, @Nullable String description) {
        String sessionId = ProcessorTransactionService.createNewSessionId();
        ProcessorStatusYaml psy = new ProcessorStatusYaml(new ArrayList<>(), null,
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown), "",
                sessionId, System.currentTimeMillis(),
                Consts.UNKNOWN_INFO, Consts.UNKNOWN_INFO, null, false, 1, EnumsApi.OS.unknown, Consts.UNKNOWN_INFO, null);
        Processor p = createProcessor(description, remoteAddress, psy);

        return new DispatcherCommParamsYaml.ReAssignProcessorId(p.getId(), sessionId);
    }

    /**
     * session is Ok, so we need to update session's timestamp periodically
     */
    private void updateSession(Processor processor, ProcessorStatusYaml ss) {
        final long millis = System.currentTimeMillis();
        final long diff = millis - ss.sessionCreatedOn;
        if (diff > Consts.SESSION_UPDATE_TIMEOUT) {
            log.debug("#807.200 (System.currentTimeMillis()-ss.sessionCreatedOn)>SESSION_UPDATE_TIMEOUT),\n" +
                            "'    processor.version: {}, millis: {}, ss.sessionCreatedOn: {}, diff: {}, SESSION_UPDATE_TIMEOUT: {},\n" +
                            "'    processor.status:\n{},\n" +
                            "'    return ReAssignProcessorId() with the same processorId and sessionId. only session'p timestamp was updated.",
                    processor.version, millis, ss.sessionCreatedOn, diff, Consts.SESSION_UPDATE_TIMEOUT, processor.status);
            // the same processor, with the same sessionId
            // so we just need to refresh sessionId timestamp
            ss.sessionCreatedOn = millis;
            processor.updatedOn = millis;
            processor.status = ProcessorStatusYamlUtils.BASE_YAML_UTILS.toString(ss);
            processorCache.save(processor);

            // the same processorId but new sessionId
            return;
        }
        else {
            // the same processorId, the same sessionId, session isn't expired
            return;
        }
    }

    @Transactional
    public Void checkProcessorId(final Long processorId, @Nullable String sessionId, String remoteAddress, DispatcherCommParamsYaml lcpy) {
        processorSyncService.checkWriteLockPresent(processorId);

        final Processor processor = processorCache.findById(processorId);
        if (processor == null) {
            log.warn("#807.220 processor == null, return ReAssignProcessorId() with new processorId and new sessionId");
            lcpy.reAssignedProcessorId = reassignProcessorId(remoteAddress, "Id was reassigned from " + processorId);
            return null;
        }
        ProcessorStatusYaml ss;
        try {
            ss = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(processor.status);
        } catch (Throwable e) {
            log.error("#807.280 Error parsing current status of processor:\n{}", processor.status);
            log.error("#807.300 Error ", e);
            // skip any command from this processor
            return null;
        }
        if (StringUtils.isBlank(sessionId)) {
            log.debug("#807.320 StringUtils.isBlank(sessionId), return ReAssignProcessorId() with new sessionId");
            // the same processor but with different and expired sessionId
            // so we can continue to use this processorId with new sessionId
            lcpy.reAssignedProcessorId = assignNewSessionId(processor, ss);
            return null;
        }
        if (!ss.sessionId.equals(sessionId)) {
            if ((System.currentTimeMillis() - ss.sessionCreatedOn) > Consts.SESSION_TTL) {
                log.debug("#807.340 !ss.sessionId.equals(sessionId) && (System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL, return ReAssignProcessorId() with new sessionId");
                // the same processor but with different and expired sessionId
                // so we can continue to use this processorId with new sessionId
                // we won't use processor's sessionIf to be sure that sessionId has valid format
                lcpy.reAssignedProcessorId = assignNewSessionId(processor, ss);
                return null;
            } else {
                log.debug("#807.360 !ss.sessionId.equals(sessionId) && !((System.currentTimeMillis() - ss.sessionCreatedOn) > SESSION_TTL), return ReAssignProcessorId() with new processorId and new sessionId");
                // different processors with the same processorId
                // there is other active processor with valid sessionId
                lcpy.reAssignedProcessorId = reassignProcessorId(remoteAddress, "Id was reassigned from " + processorId);
                return null;
            }
        } else {
            // see logs in method
            updateSession(processor, ss);
        }
        return null;
    }


}
