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

package ai.metaheuristic.ai.launchpad.source_code;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.launchpad.LaunchpadContext;
import ai.metaheuristic.ai.launchpad.beans.SourceCodeImpl;
import ai.metaheuristic.ai.launchpad.variable.GlobalVariableService;
import ai.metaheuristic.ai.launchpad.data.SourceCodeData;
import ai.metaheuristic.ai.launchpad.event.LaunchpadInternalEvent;
import ai.metaheuristic.ai.launchpad.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookService;
import ai.metaheuristic.ai.utils.ControllerUtils;
import ai.metaheuristic.ai.yaml.source_code.PlanParamsYamlUtils;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.OperationStatusRest;
import ai.metaheuristic.api.data.source_code.SourceCodeApiData;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.launchpad.SourceCode;
import ai.metaheuristic.api.launchpad.ExecContext;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.WrongVersionOfYamlFileException;
import ai.metaheuristic.commons.utils.DirUtils;
import ai.metaheuristic.commons.utils.StrUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.YAML_EXT;
import static ai.metaheuristic.ai.Consts.YML_EXT;

@Slf4j
@Profile("launchpad")
@Service
@RequiredArgsConstructor
public class SourceCodeTopLevelService {

    private final Globals globals;
    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeService sourceCodeService;
    private final SourceCodeRepository sourceCodeRepository;
    private final WorkbookService workbookService;
    private final ApplicationEventPublisher publisher;
    private final WorkbookCache workbookCache;
    private final GlobalVariableService globalVariableService;

    public SourceCodeApiData.WorkbookResult addWorkbook(Long planId, String variable, LaunchpadContext context) {
        return getWorkbookResult(variable, context, sourceCodeCache.findById(planId));
    }

    public SourceCodeApiData.WorkbookResult addWorkbook(String planCode, String variable, LaunchpadContext context) {
        return getWorkbookResult(variable, context, sourceCodeRepository.findByCodeAndCompanyId(planCode, context.getCompanyId()));
    }

    private SourceCodeApiData.WorkbookResult getWorkbookResult(String variable, LaunchpadContext context, SourceCodeImpl plan) {
        if (S.b(variable)) {
            return new SourceCodeApiData.WorkbookResult("#560.006 name of variable is empty");
        }
        if (globalVariableService.getIdInVariables(List.of(variable)).isEmpty()) {
            return new SourceCodeApiData.WorkbookResult( "#560.008 global variable " + variable +" wasn't found");
        }
        // validate the sourceCode
        SourceCodeApiData.PlanValidation planValidation = sourceCodeService.validateInternal(plan);
        if (planValidation.status != EnumsApi.SourceCodeValidateStatus.OK) {
            SourceCodeApiData.WorkbookResult r = new SourceCodeApiData.WorkbookResult();
            r.errorMessages = planValidation.errorMessages;
            return r;
        }

        OperationStatusRest status = checkPlan(plan, context);
        if (status != null) {
            return new SourceCodeApiData.WorkbookResult( "#560.011 access denied: " + status.getErrorMessagesAsStr());
        }
        return workbookService.createWorkbookInternal(plan, variable);
    }

    public SourceCodeApiData.SourceCodesResult getSourceCodes(Pageable pageable, boolean isArchive, LaunchpadContext context) {
        pageable = ControllerUtils.fixPageSize(globals.planRowsLimit, pageable);
        List<SourceCode> sourceCodes = sourceCodeRepository.findAllByOrderByIdDesc(context.getCompanyId());
        AtomicInteger count = new AtomicInteger();

        List<SourceCode> activeSourceCodes = sourceCodes.stream()
                .filter(o-> {
                    try {
                        SourceCodeParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                        boolean b = ppy.internalParams == null || !ppy.internalParams.archived;
                        b = isArchive != b;
                        if (b) {
                            count.incrementAndGet();
                        }
                        return b;
                    } catch (YAMLException e) {
                        log.error("#560.020 Can't parse SourceCode params. It's broken or unknown version. SourceCode id: #{}", o.getId());
                        log.error("#560.025 Params:\n{}", o.getParams());
                        log.error("#560.030 Error: {}", e.toString());
                        return false;
                    }
                }).collect(Collectors.toList());

        sourceCodes = activeSourceCodes.stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .peek(o-> o.setParams(null))
                .collect(Collectors.toList());

        SourceCodeApiData.SourceCodesResult sourceCodesResultRest = new SourceCodeApiData.SourceCodesResult();
        sourceCodesResultRest.items = new PageImpl<>(sourceCodes, pageable, count.get());
        sourceCodesResultRest.assetMode = globals.assetMode;

        return sourceCodesResultRest;
    }

    public SourceCodeApiData.SourceCodeResult getSourceCode(Long sourceCodeId, LaunchpadContext context) {
        final SourceCodeImpl plan = sourceCodeCache.findById(sourceCodeId);
        if (plan == null) {
            return new SourceCodeApiData.SourceCodeResult(
                    "#560.050 sourceCode wasn't found, sourceCodeId: " + sourceCodeId,
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR);
        }
        return new SourceCodeApiData.SourceCodeResult(plan, plan.getPlanParamsYaml().origin);
    }

    public SourceCodeApiData.SourceCodeResult validateSourceCode(Long planId, LaunchpadContext context) {
        final SourceCodeImpl plan = sourceCodeCache.findById(planId);
        if (plan == null) {
            return new SourceCodeApiData.SourceCodeResult("#560.070 sourceCode wasn't found, planId: " + planId,
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR);
        }

        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(plan, plan.getPlanParamsYaml().origin);
        SourceCodeApiData.PlanValidation planValidation = sourceCodeService.validateInternal(plan);
        result.errorMessages = planValidation.errorMessages;
        result.infoMessages = planValidation.infoMessages;
        result.status = planValidation.status;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public SourceCodeApiData.SourceCodeResult addSourceCode(String sourceCode, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new SourceCodeApiData.SourceCodeResult("#560.085 Can't add a new sourceCode while 'replicated' mode of asset is active");
        }
        if (StringUtils.isBlank(sourceCode)) {
            return new SourceCodeApiData.SourceCodeResult("#560.090 sourceCode yaml is empty");
        }

        SourceCodeParamsYaml ppy;
        try {
            ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode);
        } catch (WrongVersionOfYamlFileException e) {
            return new SourceCodeApiData.SourceCodeResult("#560.110 An error parsing yaml: " + e.getMessage());
        }

        final String code = ppy.source.code;
        if (StringUtils.isBlank(code)) {
            return new SourceCodeApiData.SourceCodeResult("#560.130 the code of sourceCode is empty");
        }
        SourceCode f = sourceCodeRepository.findByCodeAndCompanyId(code, context.getCompanyId());
        if (f!=null) {
            return new SourceCodeApiData.SourceCodeResult("#560.150 the sourceCode with such code already exists, code: " + code);
        }

        SourceCodeImpl plan = new SourceCodeImpl();
        ppy.origin.source = sourceCode;
        ppy.origin.lang = EnumsApi.SourceCodeLang.yaml;
        ppy.internalParams.updatedOn = System.currentTimeMillis();
        String params = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy);
        plan.setParams(params);

        plan.companyId = context.getCompanyId();
        plan.createdOn = System.currentTimeMillis();
        plan.uid = ppy.source.code;
        plan = sourceCodeCache.save(plan);

        SourceCodeApiData.PlanValidation planValidation = sourceCodeService.validateInternal(plan);

        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(plan, ppy.origin );
        result.infoMessages = planValidation.infoMessages;
        result.errorMessages = planValidation.errorMessages;
        return result;
    }

    @SuppressWarnings("Duplicates")
    public SourceCodeApiData.SourceCodeResult updateSourceCode(Long planId, String sourceCode, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new SourceCodeApiData.SourceCodeResult("#560.160 Can't update a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCodeImpl plan = sourceCodeCache.findById(planId);
        if (plan == null) {
            return new SourceCodeApiData.SourceCodeResult(
                    "#560.010 sourceCode wasn't found, planId: " + planId,
                    EnumsApi.SourceCodeValidateStatus.SOURCE_CODE_NOT_FOUND_ERROR);
        }
        if (StringUtils.isBlank(sourceCode)) {
            return new SourceCodeApiData.SourceCodeResult("#560.170 sourceCode yaml is empty");
        }

        SourceCodeParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(sourceCode);

        final String code = ppy.source.code;
        if (StringUtils.isBlank(code)) {
            return new SourceCodeApiData.SourceCodeResult("#560.190 code of sourceCode is empty");
        }
        if (StringUtils.isBlank(code)) {
            return new SourceCodeApiData.SourceCodeResult("#560.210 sourceCode is empty");
        }
        SourceCode p = sourceCodeRepository.findByCodeAndCompanyId(code, context.getCompanyId());
        if (p!=null && !p.getId().equals(plan.getId())) {
            return new SourceCodeApiData.SourceCodeResult("#560.230 sourceCode with such code already exists, code: " + code);
        }
        plan.uid = code;

        ppy.origin.source = sourceCode;
        ppy.origin.lang = EnumsApi.SourceCodeLang.yaml;
        ppy.internalParams.updatedOn = System.currentTimeMillis();
        String params = PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy);
        plan.setParams(params);

        plan = sourceCodeCache.save(plan);

        SourceCodeApiData.PlanValidation planValidation = sourceCodeService.validateInternal(plan);

        SourceCodeApiData.SourceCodeResult result = new SourceCodeApiData.SourceCodeResult(plan, ppy.origin );
        result.infoMessages = planValidation.infoMessages;
        result.errorMessages = planValidation.errorMessages;
        return result;
    }

    public OperationStatusRest deleteSourceCodeById(Long planId, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.240 Can't delete a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCode sourceCode = sourceCodeCache.findById(planId);
        if (sourceCode == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.250 sourceCode wasn't found, planId: " + planId);
        }
        sourceCodeCache.deleteById(planId);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest archiveSourceCodeById(Long id, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.260 Can't archive a sourceCode while 'replicated' mode of asset is active");
        }
        SourceCodeImpl plan = sourceCodeCache.findById(id);
        OperationStatusRest status = checkPlan(plan, context);
        if (status!=null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,"#560.270 sourceCode wasn't found, planId: " + id+", " + status.getErrorMessagesAsStr());
        }
        SourceCodeParamsYaml ppy = PlanParamsYamlUtils.BASE_YAML_UTILS.to(plan.getParams());
        if (ppy.internalParams==null) {
            ppy.internalParams = new SourceCodeParamsYaml.InternalParams();
        }
        ppy.internalParams.archived = true;
        ppy.internalParams.updatedOn = System.currentTimeMillis();
        plan.setParams(PlanParamsYamlUtils.BASE_YAML_UTILS.toString(ppy));

        sourceCodeCache.save(plan);
        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    public OperationStatusRest uploadSourceCode(MultipartFile file, LaunchpadContext context) {
        if (globals.assetMode==EnumsApi.LaunchpadAssetMode.replicated) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.280 Can't upload sourceCode while 'replicated' mode of asset is active");
        }

        String originFilename = file.getOriginalFilename();
        if (originFilename == null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.290 name of uploaded file is null");
        }
        String ext = StrUtils.getExtension(originFilename);
        if (ext==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.310 file without extension, bad filename: " + originFilename);
        }
        if (!StringUtils.equalsAny(ext.toLowerCase(), YAML_EXT, YML_EXT)) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.330 only '.yml' and '.yaml' files are supported, filename: " + originFilename);
        }

        final String location = System.getProperty("java.io.tmpdir");

        File tempDir=null;
        try {
            tempDir = DirUtils.createTempDir("mh-sourceCode-upload-");
            if (tempDir==null || tempDir.isFile()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                        "#560.350 can't create temporary directory in " + location);
            }
            final File planFile = new File(tempDir, "plans" + ext);
            log.debug("Start storing an uploaded sourceCode to disk");
            try(OutputStream os = new FileOutputStream(planFile)) {
                IOUtils.copy(file.getInputStream(), os, 64000);
            }
            log.debug("Start loading sourceCode into db");
            String yaml = FileUtils.readFileToString(planFile, StandardCharsets.UTF_8);
            SourceCodeApiData.SourceCodeResult result = addSourceCode(yaml, context);

            if (result.isErrorMessages()) {
                return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, result.errorMessages, result.infoMessages);
            }
            return OperationStatusRest.OPERATION_STATUS_OK;
        }
        catch (Throwable e) {
            log.error("Error", e);
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR,
                    "#560.370 can't load plans, Error: " + e.toString());
        }
        finally {
            DirUtils.deleteAsync(tempDir);
        }
    }

    // ========= ExecContext specific =============

    public OperationStatusRest changeWorkbookExecState(String state, Long workbookId, LaunchpadContext context) {
        EnumsApi.WorkbookExecState execState = EnumsApi.WorkbookExecState.valueOf(state.toUpperCase());
        if (execState==EnumsApi.WorkbookExecState.UNKNOWN) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.390 Unknown exec state, state: " + state);
        }
        OperationStatusRest status = checkWorkbook(workbookId, context);
        if (status != null) {
            return status;
        }
        status = workbookService.workbookTargetExecState(workbookId, execState);
        return status;
    }

    public OperationStatusRest deleteWorkbookById(Long workbookId, LaunchpadContext context) {
        OperationStatusRest status = checkWorkbook(workbookId, context);
        if (status != null) {
            return status;
        }
        publisher.publishEvent( new LaunchpadInternalEvent.WorkbookDeletionEvent(this, workbookId) );
        workbookService.deleteWorkbook(workbookId, context.getCompanyId());

        return OperationStatusRest.OPERATION_STATUS_OK;
    }

    private OperationStatusRest checkWorkbook(Long workbookId, LaunchpadContext context) {
        if (workbookId==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.395 workbookId is null");
        }
        ExecContext wb = workbookCache.findById(workbookId);
        if (wb==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.400 ExecContext wasn't found, workbookId: " + workbookId );
        }
        SourceCodeData.PlansForCompany plansForCompany = sourceCodeService.getPlan(context.getCompanyId(), wb.getPlanId());
        if (plansForCompany.isErrorMessages()) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.405 SourceCode wasn't found, " +
                    "companyId: "+context.getCompanyId()+", planId: " + wb.getPlanId()+", workbookId: " + wb.getId()+", error msg: " + plansForCompany.getErrorMessagesAsStr() );
        }
        return null;
    }

    private OperationStatusRest checkPlan(SourceCode sourceCode, LaunchpadContext context) {
        if (sourceCode ==null) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.395 sourceCode is null");
        }
        if (!Objects.equals(sourceCode.getCompanyId(), context.getCompanyId())) {
            return new OperationStatusRest(EnumsApi.OperationStatus.ERROR, "#560.405 Access to sourceCode is denied, planId: " + sourceCode.getId() );
        }
        return null;
    }
}
