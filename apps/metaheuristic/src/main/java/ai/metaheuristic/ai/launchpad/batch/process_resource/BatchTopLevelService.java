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

package ai.metaheuristic.ai.launchpad.batch.process_resource;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.exceptions.PilotResourceProcessingException;
import ai.metaheuristic.ai.exceptions.StoreNewFileWithRedirectException;
import ai.metaheuristic.ai.launchpad.batch.beans.Batch;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchStatus;
import ai.metaheuristic.ai.launchpad.batch.beans.BatchWorkbook;
import ai.metaheuristic.ai.launchpad.binary_data.BinaryDataService;
import ai.metaheuristic.ai.launchpad.data.BatchData;
import ai.metaheuristic.ai.launchpad.launchpad_resource.ResourceService;
import ai.metaheuristic.ai.launchpad.plan.PlanCache;
import ai.metaheuristic.ai.launchpad.plan.PlanService;
import ai.metaheuristic.ai.launchpad.repositories.PlanRepository;
import ai.metaheuristic.ai.launchpad.repositories.WorkbookRepository;
import ai.metaheuristic.ai.resource.ResourceUtils;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.plan.PlanParamsYamlUtils;
import ai.metaheuristic.api.v1.EnumsApi;
import ai.metaheuristic.api.v1.data.OperationStatusRest;
import ai.metaheuristic.api.v1.data.PlanApiData;
import ai.metaheuristic.api.v1.launchpad.Plan;
import ai.metaheuristic.api.v1.launchpad.Workbook;
import ai.metaheuristic.commons.exceptions.UnzipArchiveException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import ai.metaheuristic.commons.utils.ZipUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Serge
 * Date: 6/13/2019
 * Time: 11:52 PM
 */
@Slf4j
@Profile("launchpad")
@Service
public class BatchTopLevelService {

    private static final String ATTACHMENTS_POOL_CODE = "attachments";

    private static final String ITEM_LIST_PREFIX = "  - ";

    private static final String CONFIG_FILE = "config.yaml";
    private static Set<String> EXCLUDE_EXT = Set.of(".zip", ".yaml");

    private final Globals globals;
    private final PlanCache planCache;
    private final PlanService planService;
    private final BinaryDataService binaryDataService;
    private final ResourceService resourceService;
    private final BatchRepository batchRepository;
    private final BatchService batchService;
    private final PlanRepository planRepository;
    private final WorkbookRepository workbookRepository;
    private final BatchWorkbookRepository batchWorkbookRepository;

    public static final Function<String, Boolean> VALIDATE_ZIP_FUNCTION = BatchController::isZipEntityNameOk;

    public BatchTopLevelService(PlanCache planCache, PlanService planService, BinaryDataService binaryDataService, Globals globals, ResourceService resourceService, BatchRepository batchRepository, BatchService batchService, PlanRepository planRepository, WorkbookRepository workbookRepository, BatchWorkbookRepository batchWorkbookRepository) {
        this.planCache = planCache;
        this.planService = planService;
        this.binaryDataService = binaryDataService;
        this.globals = globals;
        this.resourceService = resourceService;
        this.batchRepository = batchRepository;
        this.batchService = batchService;
        this.planRepository = planRepository;
        this.workbookRepository = workbookRepository;
        this.batchWorkbookRepository = batchWorkbookRepository;
    }

    public BatchData.BatchesResult getBatches(Pageable pageable) {
        pageable = ControllerUtils.fixPageSize(20, pageable);
        Page<Long> batchIds = batchRepository.findAllByOrderByCreatedOnDesc(pageable);

        long total = batchIds.getTotalElements();

        List<BatchData.ProcessResourceItem> items = batchService.getBatches(batchIds);
        BatchData.BatchesResult result = new BatchData.BatchesResult();
        result.batches = new PageImpl<>(items, pageable, total);

        //noinspection unused
        int i=0;
        return result;
    }

    public BatchData.PlansForBatchResult getPlansForBatchResult() {
        final BatchData.PlansForBatchResult plans = new BatchData.PlansForBatchResult();
        plans.items = planRepository.findAllAsPlan().stream().filter(o->{
            if (!o.isValid()) {
                return false;
            }
            try {
                PlanApiData.PlanParamsYaml ppy = PlanParamsYamlUtils.to(o.getParams());
                return ppy.internalParams == null || !ppy.internalParams.archived;
            } catch (YAMLException e) {
                final String es = "#990.010 Can't parse Plan params. It's broken or unknown version. Plan id: #" + o.getId();
                plans.addErrorMessage(es);
                log.error(es);
                log.error("#990.015 Params:\n{}", o.getParams());
                log.error("#990.020 Error: {}", e.toString());
                return false;
            }
        }).sorted((o1,o2)-> Long.compare(o2.getId(), o1.getId())
        ).collect(Collectors.toList());

        return plans;
    }

    public OperationStatusRest batchUploadFromFile(final MultipartFile file, Long planId) {

        String tempFilename = file.getOriginalFilename();
        if (tempFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#990.040 name of uploaded file is null");
        }
        final String originFilename = tempFilename.toLowerCase();
        Plan plan = planCache.findById(planId);
        if (plan == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#990.050 plan wasn't found, planId: " + planId);
        }

        // validate the plan
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#990.060 validation of plan was failed, status: " + planValidation.status);
        }

        try {
            File tempDir = DirUtils.createTempDir("batch-file-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#990.070 can't create temporary directory in " + System.getProperty("java.io.tmpdir"));
            }

            final File dataFile = new File(tempDir, originFilename );
            log.debug("Start storing an uploaded file to disk");
            try(OutputStream os = new FileOutputStream(dataFile)) {
                IOUtils.copy(file.getInputStream(), os, 32000);
            }

            final Batch b = batchService.createNewBatch(plan.getId());
            try(InputStream is = new FileInputStream(dataFile)) {
                String code = ResourceUtils.toResourceCode(originFilename);
                binaryDataService.save(
                        is, dataFile.length(), EnumsApi.BinaryDataType.BATCH, code, code,
                        true, originFilename, b.id, EnumsApi.BinaryDataRefType.batch);
            }

            final Batch batch = batchService.changeStateToPreparing(b.id);
            if (batch==null) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#990.080 can't find batch with id " + b.id);
            }

            log.info("The file {} was successfully stored to disk", originFilename);
            new Thread(() -> {
                try {
                    if (originFilename.endsWith(".zip")) {

                        List<String> errors = ZipUtils.validate(dataFile, VALIDATE_ZIP_FUNCTION);
                        if (!errors.isEmpty()) {
                            StringBuilder err = new StringBuilder("#990.090 Zip archive contains wrong chars in name(s):\n");
                            for (String error : errors) {
                                err.append('\t').append(error).append('\n');
                            }
                            batchService.changeStateToError(batch.id, err.toString());
                            return;
                        }

                        log.debug("Start unzipping archive");
                        ZipUtils.unzipFolder(dataFile, tempDir);
                        log.debug("Start loading file data to db");
                        loadFilesFromDirAfterZip(batch, tempDir, plan);
                    }
                    else {
                        log.debug("Start loading file data to db");
                        loadFilesFromDirAfterZip(batch, tempDir,  plan);
                    }
                }
                catch(UnzipArchiveException e) {
                    log.error("Error", e);
                    batchService.changeStateToError(batch.id, "#990.100 can't unzip an archive. Error: " + e.getMessage()+", class: " + e.getClass());
                }
                catch(Throwable th) {
                    log.error("Error", th);
                    batchService.changeStateToError(batch.id, "#990.110 General processing error. Error: " + th.getMessage()+", class: " + th.getClass());
                }

            }).start();
            //noinspection unused
            int i=0;
        }
        catch (Throwable th) {
            log.error("Error", th);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#990.120 can't load file, error: " + th.getMessage()+", class: " + th.getClass());
        }
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest processResourceDeleteCommit(Long batchId) {

        Batch batch = batchService.findById(batchId);
        if (batch == null) {
            final String es = "#990.250 Batch wasn't found, batchId: " + batchId;
            log.info(es);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, es);
        }

        List<Long> bfis = batchWorkbookRepository.findWorkbookIdsByBatchId(batch.id);
        for (Long workbookId : bfis) {
            Workbook wb = workbookRepository.findById(workbookId).orElse(null);
            if (wb == null) {
                continue;
            }
            planService.deleteWorkbook(wb.getId(), wb.getPlanId());
        }
        batchWorkbookRepository.deleteByBatchId(batch.id);
        batchService.deleteById(batch.id);

        return new OperationStatusRest(EnumsApi.OperationStatus.OK, "Batch #"+batch.id+" was deleted successfully.", null);
    }

    private void loadFilesFromDirAfterZip(Batch batch, File srcDir, Plan plan) throws IOException {

        List<Path> paths = Files.list(srcDir.toPath())
                .filter(o -> {
                    File f = o.toFile();
                    return !EXCLUDE_EXT.contains(StrUtils.getExtension(f.getName()));
                })
                .collect(Collectors.toList());

        if (paths.isEmpty()) {
            batchService.changeStateToFinished(batch.id);
            return;
        }

        for (Path dataFile : paths) {
            File file = dataFile.toFile();
            if (file.isDirectory()) {
                try {
                    final File mainDocFile = getMainDocumentFile(file);
                    final List<File> files = new ArrayList<>();
                    Files.list(dataFile)
                            .filter(o -> o.toFile().isFile())
                            .forEach(f -> files.add(f.toFile()));

                    createAndProcessTask(batch, plan, files, mainDocFile);
                } catch (StoreNewFileWithRedirectException e) {
                    throw e;
                } catch (Throwable th) {
                    String es = "#990.130 An error while saving data to file, " + th.toString();
                    log.error(es, th);
                    throw new PilotResourceProcessingException(es);
                }
            } else {
                createAndProcessTask(batch, plan, Collections.singletonList(file), file);
            }
        }
    }

    private File getMainDocumentFile(File srcDir) throws IOException {
        File configFile = new File(srcDir, CONFIG_FILE);
        if (!configFile.exists()) {
            throw new PilotResourceProcessingException("#990.140 config.yaml file wasn't found in path " + srcDir.getPath());
        }

        if (!configFile.isFile()) {
            throw new PilotResourceProcessingException("#990.150 config.yaml must be a file, not a directory");
        }
        Yaml yaml = new Yaml();

        String mainDocument;
        try (InputStream is = new FileInputStream(configFile)) {
            Map<String, Object> config = yaml.load(is);
            mainDocument = config.get(Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH).toString();
        }

        if (StringUtils.isBlank(mainDocument)) {
            throw new PilotResourceProcessingException("#990.160 config.yaml must contain non-empty field '" + Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH + "' ");
        }

        final File mainDocFile = new File(srcDir, mainDocument);
        if (!mainDocFile.exists()) {
            throw new PilotResourceProcessingException("#990.170 main document file "+mainDocument+" wasn't found in path " + srcDir.getPath());
        }
        return mainDocFile;
    }

    private static String asInputResourceParams(String mainPoolCode, String attachPoolCode, List<String> attachmentCodes) {
        String yaml ="preservePoolNames: true\n" +
                "poolCodes:\n  " + Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH + ":\n" + ITEM_LIST_PREFIX + mainPoolCode;
        if (attachmentCodes.isEmpty()) {
            return yaml;
        }
        yaml += "\n  " + ATTACHMENTS_POOL_CODE + ":\n" + ITEM_LIST_PREFIX + attachPoolCode + '\n';
        return yaml;
    }

    private void createAndProcessTask(Batch batch, Plan plan, List<File> dataFile, File mainDocFile) {
        long nanoTime = System.nanoTime();
        List<String> attachments = new ArrayList<>();
        String mainPoolCode = String.format("%d-%s-%d", plan.getId(), Consts.MAIN_DOCUMENT_POOL_CODE_FOR_BATCH, nanoTime);
        String attachPoolCode = String.format("%d-%s-%d", plan.getId(), ATTACHMENTS_POOL_CODE, nanoTime);
        boolean isMainDocPresent = false;
        for (File file : dataFile) {
            String originFilename = file.getName();
            if (EXCLUDE_EXT.contains(StrUtils.getExtension(originFilename))) {
                continue;
            }
            final String code = ResourceUtils.toResourceCode(originFilename);

            String poolCode;
            if (file.equals(mainDocFile)) {
                poolCode = mainPoolCode;
                isMainDocPresent = true;
            }
            else {
                poolCode = attachPoolCode;
                attachments.add(code);
            }

            resourceService.storeInitialResource(file, code, poolCode, originFilename);
        }

        if (!isMainDocPresent) {
            throw new PilotResourceProcessingException("#990.180 main document wasn't found");
        }

        final String paramYaml = asInputResourceParams(mainPoolCode, attachPoolCode, attachments);
        PlanApiData.TaskProducingResultComplex producingResult = planService.createWorkbook(plan.getId(), paramYaml);
        if (producingResult.planProducingStatus!= EnumsApi.PlanProducingStatus.OK) {
            throw new PilotResourceProcessingException("#990.190 Error creating workbook: " + producingResult.planProducingStatus);
        }
        BatchWorkbook bfi = new BatchWorkbook();
        bfi.batchId=batch.id;
        bfi.workbookId=producingResult.workbook.getId();
        batchWorkbookRepository.save(bfi);

        // ugly work-around on ObjectOptimisticLockingFailureException, StaleObjectStateException
        Long planId = plan.getId();
        plan = planCache.findById(planId);
        if (plan == null) {
            throw new PilotResourceProcessingException("#990.200 plan wasn't found, planId: " + planId);
        }

        // validate the plan + the workbook
        PlanApiData.PlanValidation planValidation = planService.validateInternal(plan);
        if (planValidation.status != EnumsApi.PlanValidateStatus.OK ) {
            throw new PilotResourceProcessingException("#990.210 validation of plan was failed, status: " + planValidation.status);
        }

        PlanApiData.TaskProducingResultComplex countTasks = planService.produceTasks(false, plan, producingResult.workbook);
        if (countTasks.planProducingStatus != EnumsApi.PlanProducingStatus.OK) {
            throw new PilotResourceProcessingException("#990.220 validation of plan was failed, status: " + countTasks.planValidateStatus);
        }

        if (globals.maxTasksPerPlan < countTasks.numberOfTasks) {
            planService.changeValidStatus(producingResult.workbook, false);
            throw new PilotResourceProcessingException(
                    "#990.220 number of tasks for this workbook exceeded the allowed maximum number. Workbook was created but its status is 'not valid'. " +
                            "Allowed maximum number of tasks: " + globals.maxTasksPerPlan+", tasks in this workbook:  " + countTasks.numberOfTasks);
        }
        planService.changeValidStatus(producingResult.workbook, true);

        // start producing new tasks
        OperationStatusRest operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.PRODUCING);

        if (operationStatus.isErrorMessages()) {
            throw new PilotResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
        planService.createAllTasks();


        batchService.changeStateToProcessing(batch.id);
        operationStatus = planService.workbookTargetExecState(producingResult.workbook.getId(), EnumsApi.WorkbookExecState.STARTED);

        if (operationStatus.isErrorMessages()) {
            throw new PilotResourceProcessingException(operationStatus.getErrorMessagesAsStr());
        }
    }

    public BatchData.Status getProcessingResourceStatus(Long batchId) {
        Batch batch = batchService.findById(batchId);
        if (batch == null) {
            final String es = "#990.260 Batch wasn't found, batchId: " + batchId;
            log.warn(es);
            return new BatchData.Status(es);
        }
        BatchStatus status = batchService.updateStatus(batchId, false);
        return new BatchData.Status(batchId, status.getStatus(), status.ok);
    }


}