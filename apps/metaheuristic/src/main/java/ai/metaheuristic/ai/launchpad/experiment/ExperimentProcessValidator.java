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

import ai.metaheuristic.ai.launchpad.beans.Experiment;
import ai.metaheuristic.ai.launchpad.beans.Snippet;
import ai.metaheuristic.ai.launchpad.plan.ProcessValidator;
import ai.metaheuristic.ai.launchpad.repositories.ExperimentRepository;
import ai.metaheuristic.ai.launchpad.repositories.SnippetRepository;
import ai.metaheuristic.ai.launchpad.workbook.WorkbookCache;
import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.Meta;
import ai.metaheuristic.api.data.experiment.ExperimentParamsYaml;
import ai.metaheuristic.api.data.plan.PlanParamsYaml;
import ai.metaheuristic.api.launchpad.Plan;
import ai.metaheuristic.api.launchpad.Workbook;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.utils.MetaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("launchpad")
@Slf4j
@RequiredArgsConstructor
public class ExperimentProcessValidator implements ProcessValidator {

    private final ExperimentRepository experimentRepository;
    private final ExperimentCache experimentCache;
    private final WorkbookCache workbookCache;
    private final SnippetRepository snippetRepository;

    // TODO experiment has to be stateless and has its own instances
    // TODO 2019.05.02 do we need an experiment to have its own instance still?
    // TODO 2019.07.04 the current thought is that we don't need stateless experiment
    //      because each experiment has its own set of hyper parameters
    // TODO 2019.08.26 an experiment will be always stateful.
    //      that means that there won't be separated description of experiment and instances of experiment

    @Override
    public EnumsApi.PlanValidateStatus validate(Plan plan, PlanParamsYaml.Process process, boolean isFirst) {
        if (process.snippets!=null && process.snippets.size() > 0) {
            return EnumsApi.PlanValidateStatus.SNIPPET_ALREADY_PROVIDED_BY_EXPERIMENT_ERROR;
        }
        if (StringUtils.isBlank(process.code)) {
            return EnumsApi.PlanValidateStatus.SNIPPET_NOT_DEFINED_ERROR;
        }
        Long experimentId = experimentRepository.findIdByCode(process.code);
        if (experimentId==null) {
            return EnumsApi.PlanValidateStatus.EXPERIMENT_NOT_FOUND_ERROR;
        }
        Experiment e = experimentCache.findById(experimentId);
        if (e==null) {
            return EnumsApi.PlanValidateStatus.EXPERIMENT_NOT_FOUND_ERROR;
        }
        if (e.getWorkbookId()!=null) {
            Workbook workbook = workbookCache.findById(e.getWorkbookId());
            if (workbook != null) {
                if (!plan.getId().equals(workbook.getPlanId())) {
                    return EnumsApi.PlanValidateStatus.EXPERIMENT_ALREADY_STARTED_ERROR;
                }
            }
            else {
                return EnumsApi.PlanValidateStatus.WORKBOOK_DOESNT_EXIST_ERROR;
            }
        }
        ExperimentParamsYaml epy = e.getExperimentParamsYaml();

        if (StringUtils.isBlank(epy.experimentYaml.fitSnippet) || StringUtils.isBlank(epy.experimentYaml.predictSnippet)) {
            return EnumsApi.PlanValidateStatus.EXPERIMENT_HASNT_ALL_SNIPPETS_ERROR;
        }
        Snippet s = snippetRepository.findByCode(epy.experimentYaml.fitSnippet);
        if (s==null) {
            return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
        }

        Snippet predictSnippet = snippetRepository.findByCode(epy.experimentYaml.fitSnippet);
        if (predictSnippet==null) {
            return EnumsApi.PlanValidateStatus.SNIPPET_NOT_FOUND_ERROR;
        }
        boolean isFittingDetection = MetaUtils.isTrue(predictSnippet.getSnippetConfig(false).metas, ConstsApi.META_MH_FITTING_DETECTION_SUPPORTED);
        if (isFittingDetection) {
            if (S.b(epy.experimentYaml.checkFittingSnippet)) {
                return EnumsApi.PlanValidateStatus.FITTING_SNIPPET_NOT_FOUND_ERROR;
            }
            Snippet fittingSnippet = snippetRepository.findByCode(epy.experimentYaml.checkFittingSnippet);
            if (fittingSnippet==null) {
                return EnumsApi.PlanValidateStatus.FITTING_SNIPPET_NOT_FOUND_ERROR;
            }

        }
        if (!isFirst) {
            if (process.metas == null || process.metas.isEmpty()) {
                return EnumsApi.PlanValidateStatus.EXPERIMENT_META_NOT_FOUND_ERROR;
            }

            Meta m1 = process.getMeta("dataset");
            if (m1 == null || StringUtils.isBlank(m1.getValue())) {
                return EnumsApi.PlanValidateStatus.EXPERIMENT_META_DATASET_NOT_FOUND_ERROR;
            }

            Meta m3 = process.getMeta("feature");
            if (m3 == null || StringUtils.isBlank(m3.getValue())) {
                return EnumsApi.PlanValidateStatus.EXPERIMENT_META_FEATURE_NOT_FOUND_ERROR;
            }
        }
        return null;
    }
}
