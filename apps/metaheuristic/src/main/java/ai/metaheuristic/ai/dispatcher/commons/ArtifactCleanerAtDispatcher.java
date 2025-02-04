/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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
package ai.metaheuristic.ai.dispatcher.commons;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.batch.BatchCache;
import ai.metaheuristic.ai.dispatcher.batch.BatchTopLevelService;
import ai.metaheuristic.ai.dispatcher.beans.*;
import ai.metaheuristic.ai.dispatcher.cache.CacheService;
import ai.metaheuristic.ai.dispatcher.event.DispatcherEventService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextCache;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.*;
import ai.metaheuristic.ai.dispatcher.source_code.SourceCodeCache;
import ai.metaheuristic.ai.dispatcher.task.TaskTransactionalService;
import ai.metaheuristic.ai.dispatcher.variable.VariableService;
import ai.metaheuristic.ai.utils.CollectionUtils;
import ai.metaheuristic.ai.utils.TxUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SuppressWarnings("DuplicatedCode")
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class ArtifactCleanerAtDispatcher {

    private final Globals globals;
    private final ExecContextTopLevelService execContextTopLevelService;
    private final ExecContextRepository execContextRepository;
    private final ExecContextGraphRepository execContextGraphRepository;
    private final ExecContextTaskStateRepository execContextTaskStateRepository;
    private final ExecContextVariableStateRepository execContextVariableStateRepository;
    private final SourceCodeCache sourceCodeCache;
    private final BatchRepository batchRepository;
    private final CompanyRepository companyRepository;
    private final BatchTopLevelService batchTopLevelService;
    private final BatchCache batchCache;
    private final ExecContextCache execContextCache;
    private final TaskRepository taskRepository;
    private final TaskTransactionalService taskTransactionalService;
    private final VariableService variableService;
    private final VariableRepository variableRepository;
    private final FunctionRepository functionRepository;
    private final CacheProcessRepository cacheProcessRepository;
    private final CacheService cacheService;
    private final ExecContextService execContextService;
    private final DispatcherEventRepository dispatcherEventRepository;
    private final FunctionDataRepository functionDataRepository;

    private static final AtomicInteger busy = new AtomicInteger(0);

    private static boolean isBusy() {
        return busy.get() > 0;
    }

    public static void setBusy() {
        ArtifactCleanerAtDispatcher.busy.incrementAndGet();
    }

    public static void notBusy() {
        int curr = ArtifactCleanerAtDispatcher.busy.decrementAndGet();
        if (curr<0) {
            throw new IllegalStateException("(curr<0)");
        }
    }

    public void fixedDelay() {
        TxUtils.checkTxNotExists();
        if (isBusy()) {
            return;
        }

        // do not change the order of calling
        deleteOrphanAndObsoletedBatches();
        deleteOrphanExecContexts();
        deleteOrphanTasks();
        deleteOrphanVariables();
        deleteOrphanCacheData();
        deleteObsoleteEvents();
        deleteObsoleteFunctionData();
    }

    private void deleteObsoleteFunctionData() {
        List<String> functionCodesInData = functionDataRepository.findAllFunctionCodes();
        List<String> functionCodes = functionRepository.findAllFunctionCodes();
        for (String functionCode : functionCodesInData) {
            if (!functionCodes.contains(functionCode)) {
                if (isBusy()) {
                    return;
                }
                functionDataRepository.deleteByFunctionCode(functionCode);
            }
        }
    }

    private void deleteObsoleteEvents() {
        final int keepPeriod = globals.dispatcher.getKeepEventsInDb().getDays();
        if (keepPeriod >100000) {
            final String es = "#510.100 globals.dispatcher.keepEventsInDb greater than 100000 days, actual: " + keepPeriod;
            log.error(es);
            throw new RuntimeException(es);
        }

        LocalDate today = LocalDate.now();
        LocalDate keepStartDate = today.minusDays(keepPeriod);
        int period = DispatcherEventService.getPeriod(keepStartDate);

        List<Long> periodsForDelete = dispatcherEventRepository.getPeriodIdsBefore(period);
        List<List<Long>> pages = CollectionUtils.parseAsPages(periodsForDelete, 10);
        for (List<Long> page : pages) {
            if (isBusy()) {
                return;
            }
            if (page.isEmpty()) {
                continue;
            }
            log.info("#510.140 Found obsolete events #{}", page);
            try {
                dispatcherEventRepository.deleteAllByIdIn(page);
            }
            catch (Throwable th) {
                log.error("#510.200 dispatcherEventRepository.deleteAllByIdIn("+page+")", th);
            }
        }
    }

    private void deleteOrphanAndObsoletedBatches() {
        List<Long> execContextIds = execContextRepository.findAllIds();
        List<Long> companyUniqueIds = companyRepository.findAllUniqueIds();
        Set<Long> forDeletion = new HashSet<>();
        List<Object[]> batches = batchRepository.findAllIdAndCreatedOnAndDeleted();
        for (Object[] obj : batches) {
            if (isBusy()) {
                return;
            }
            long batchId = ((Number)obj[0]).longValue();
            long createdOn = ((Number)obj[1]).longValue();
            boolean deleted = Boolean.TRUE.equals(obj[2]);

            if (deleted && System.currentTimeMillis() > createdOn + globals.dispatcher.timeout.batchDeletion.toMillis()) {
                forDeletion.add(batchId);
            }
            else {
                Batch b = batchCache.findById(batchId);
                if (b == null) {
                    continue;
                }
                if (!execContextIds.contains(b.execContextId) || !companyUniqueIds.contains(b.companyId)) {
                    forDeletion.add(b.id);
                }
            }
        }
        batchTopLevelService.deleteOrphanOrObsoletedBatches(forDeletion);
    }

    private void deleteOrphanExecContexts() {
        List<Long> execContextIds = execContextRepository.findAllIds();
        deleteOrphanExecContexts(execContextIds);
        if (isBusy()) {
            return;
        }
        deleteOrphanExecContextGraph(execContextIds);
        if (isBusy()) {
            return;
        }
        deleteOrphanExecContextTaskState(execContextIds);
        if (isBusy()) {
            return;
        }
        deleteOrphanExecContextVariableState(execContextIds);
    }

    private void deleteOrphanExecContexts(List<Long> execContextIds) {
        Set<Long> forDeletion = new HashSet<>();
        for (Long execContextId : execContextIds) {
            ExecContextImpl ec = execContextCache.findById(execContextId);
            if (ec==null) {
                continue;
            }
            if (sourceCodeCache.findById(ec.sourceCodeId)==null ||
                    (ec.rootExecContextId!=null && execContextCache.findById(ec.rootExecContextId)==null)) {
                forDeletion.add(execContextId);
            }
        }
        execContextTopLevelService.deleteOrphanExecContexts(forDeletion);
    }

    private void deleteOrphanExecContextGraph(List<Long> execContextIds) {
        Set<Long> execContextGraphIds = new HashSet<>();
        for (Long execContextId : execContextIds) {
            ExecContextImpl ec = execContextCache.findById(execContextId);
            if (ec==null) {
                continue;
            }
            execContextGraphIds.add(ec.execContextGraphId);
        }
        List<Long> allExecContextGraphIds = execContextGraphRepository.findAllIds();
        for (Long allExecContextGraphId : allExecContextGraphIds) {
            if (!execContextGraphIds.contains(allExecContextGraphId)) {
                ExecContextGraph execContextGraph = execContextGraphRepository.findById(allExecContextGraphId).orElse(null);
                if (execContextGraph==null || execContextGraph.createdOn==null ||
                        execContextGraph.createdOn==0 || (System.currentTimeMillis()-execContextGraph.createdOn) < 3_600_000 ) {
                    continue;
                }
                log.info("#510.240 Found orphan ExecContextGraph #{}", allExecContextGraphId);
                execContextService.deleteOrphanExecContextGraph(allExecContextGraphId);
            }
        }
    }

    private void deleteOrphanExecContextTaskState(List<Long> execContextIds) {
        Set<Long> execContextTaskStateIds = new HashSet<>();
        for (Long execContextId : execContextIds) {
            ExecContextImpl ec = execContextCache.findById(execContextId);
            if (ec==null) {
                continue;
            }
            execContextTaskStateIds.add(ec.execContextTaskStateId);
        }
        List<Long> allExecContextTaskStateIds = execContextTaskStateRepository.findAllIds();
        for (Long allExecContextTaskStateId : allExecContextTaskStateIds) {
            if (!execContextTaskStateIds.contains(allExecContextTaskStateId)) {
                ExecContextTaskState execContextTaskState = execContextTaskStateRepository.findById(allExecContextTaskStateId).orElse(null);
                if (execContextTaskState==null || execContextTaskState.createdOn==null ||
                        execContextTaskState.createdOn==0 || (System.currentTimeMillis()-execContextTaskState.createdOn) < 3_600_000 ) {
                    continue;
                }
                log.info("#510.280 Found orphan ExecContextTaskState #{}", allExecContextTaskStateId);
                execContextService.deleteOrphanExecContextTaskState(allExecContextTaskStateId);
            }
        }
    }

    private void deleteOrphanExecContextVariableState(List<Long> execContextIds) {
        Set<Long> execContextVariableStateIds = new HashSet<>();
        for (Long execContextId : execContextIds) {
            ExecContextImpl ec = execContextCache.findById(execContextId);
            if (ec==null) {
                continue;
            }
            execContextVariableStateIds.add(ec.execContextVariableStateId);
        }
        List<Long> allExecContextVariableStateIds = execContextVariableStateRepository.findAllIds();
        for (Long allExecContextVariableStateId : allExecContextVariableStateIds) {
            if (!execContextVariableStateIds.contains(allExecContextVariableStateId)) {
                ExecContextVariableState execContextVariableState = execContextVariableStateRepository.findById(allExecContextVariableStateId).orElse(null);
                if (execContextVariableState==null || execContextVariableState.createdOn==null ||
                        execContextVariableState.createdOn==0 || (System.currentTimeMillis()-execContextVariableState.createdOn) < 3_600_000 ) {
                    continue;
                }
                log.info("#510.320 Found orphan ExecContextVariableState #{}", allExecContextVariableStateId);
                execContextService.deleteOrphanExecContextVariableState(allExecContextVariableStateId);
            }
        }
    }

    private void deleteOrphanTasks() {
        TxUtils.checkTxNotExists();

        List<Long> taskExecContextIds = taskRepository.getAllExecContextIds();
        List<Long> execContextIds = execContextRepository.findAllIds();

        //noinspection SimplifyStreamApiCallChains
        List<Long> orphanExecContextIds = taskExecContextIds.stream()
                .filter(o->!execContextIds.contains(o)).collect(Collectors.toList());

        for (Long execContextId : orphanExecContextIds) {
            if (isBusy()) {
                return;
            }
            if (execContextCache.findById(execContextId)!=null) {
                log.warn("execContextId #{} still here", execContextId);
                Long id = execContextRepository.findIdById(execContextId);
                if (id != null) {
                    continue;
                }
                log.warn("execContextId #{} was deleted in db, clean up the cache", execContextId);
                try {
                    execContextService.deleteExecContextFromCache(execContextId);
                }
                catch (Throwable th) {
                    log.error("execContextService.deleteExecContextFromCache("+execContextId+")", th);
                }
            }

            List<Long> ids;
            while (!(ids = taskRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 10);
                for (List<Long> page : pages) {
                    if (page.isEmpty() || isBusy()) {
                        return;
                    }
                    log.info("Found orphan task, execContextId: #{}, tasks #{}", execContextId, page);
                    try {
                        taskTransactionalService.deleteOrphanTasks(page);
                    }
                    catch (Throwable th) {
                        log.error("taskTransactionalService.deleteOrphanTasks("+execContextId+")", th);
                    }
                }
            }
        }
    }

    private void deleteOrphanVariables() {
        List<Long> variableExecContextIds = variableRepository.getAllExecContextIds();
        List<Long> execContextIds = execContextRepository.findAllIds();

        //noinspection SimplifyStreamApiCallChains
        List<Long> orphanExecContextIds = variableExecContextIds.stream()
                .filter(o->!execContextIds.contains(o)).collect(Collectors.toList());

        for (Long execContextId : orphanExecContextIds) {
            if (execContextCache.findById(execContextId)!=null) {
                log.warn("execContextId #{} wasn't deleted, actually", execContextId);
                continue;
            }

            List<Long> ids;
            while (!(ids = variableRepository.findAllByExecContextId(Consts.PAGE_REQUEST_100_REC, execContextId)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 5);
                for (List<Long> page : pages) {
                    if (isBusy()) {
                        return;
                    }
                    if (page.isEmpty()) {
                        continue;
                    }
                    log.info("Found orphan variables, execContextId: #{}, variables #{}", execContextId, page);
                    try {
                        variableService.deleteOrphanVariables(page);
                    }
                    catch (Throwable th) {
                        log.error("variableService.deleteOrphanVariables("+execContextId+")", th);
                    }
                }
            }
        }
    }

    private void deleteOrphanCacheData() {
        List<String> funcCodes = functionRepository.findAllFunctionCodes();
        Set<String> currFuncCodes = cacheProcessRepository.findAllFunctionCodes();

        //noinspection SimplifyStreamApiCallChains
        List<String> missingCodes = currFuncCodes.stream().filter(currFuncCode -> !funcCodes.contains(currFuncCode)).collect(Collectors.toList());

        for (String funcCode : missingCodes) {
            List<Long> ids;
            while (!(ids = cacheProcessRepository.findByFunctionCode(Consts.PAGE_REQUEST_100_REC, funcCode)).isEmpty()) {
                List<List<Long>> pages = CollectionUtils.parseAsPages(ids, 5);
                for (List<Long> page : pages) {
                    if (isBusy()) {
                        return;
                    }
                    if (page.isEmpty()) {
                        continue;
                    }
                    log.info("Found orphan cache entries, funcCode: #{}, cacheProcessIds #{}", funcCode, page);
                    try {
                        cacheService.deleteCacheProcesses(page);
                    }
                    catch (Throwable th) {
                        log.error("cacheService.deleteCacheProcesses("+page+")", th);
                    }
                    for (Long cacheProcessId : page) {
                        try {
                            cacheService.deleteCacheVariable(cacheProcessId);
                        }
                        catch (Throwable th) {
                            log.error("cacheService.deleteCacheVariable("+page+")", th);
                        }
                    }
                }
            }
        }
    }
}
