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
package ai.metaheuristic.ai.service;

import ai.metaheuristic.ai.preparing.FeatureMethods;
import ai.metaheuristic.ai.yaml.communication.dispatcher.DispatcherCommParamsYaml;
import ai.metaheuristic.ai.yaml.communication.processor.ProcessorCommParamsYaml;
import ai.metaheuristic.api.EnumsApi;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
public class TestFeatureWithSomeOk extends FeatureMethods {

    @Test
    public void testFeatureCompletionWithPartialError() {
        assertTrue(isCorrectInit);

        long mills = System.currentTimeMillis();
        log.info("Start produceTasks()");
        produceTasks();
        log.info("produceTasks() was finished for {}", System.currentTimeMillis() - mills);

        execContextSyncService.getWithSync(execContextForTest.id, () -> {
            execContextFSM.toStarted(execContextForTest);
            execContextForTest = Objects.requireNonNull(execContextCache.findById(execContextForTest.getId()));

            assertEquals(EnumsApi.ExecContextState.STARTED.code, execContextForTest.getState());

            getTaskAndAssignToProcessor_mustBeNewTask();

            // this processor already got task, so don't provide any new
            DispatcherCommParamsYaml.AssignedTask task = execContextService.getTaskAndAssignToProcessor(
                    new ProcessorCommParamsYaml.ReportProcessorTaskStatus(), processor.getId(), false, experiment.getExecContextId());
            // task is empty cos we still didn't finish those task
            assertNull(task);

            finishCurrentWithError();

            DispatcherCommParamsYaml.AssignedTask task1 = execContextService.getTaskAndAssignToProcessor(
                    new ProcessorCommParamsYaml.ReportProcessorTaskStatus(), processor.getId(), false, experiment.getExecContextId());

            assertNull(task1);

            return null;

            // TODO 2019.05.04 this test needs to be rewritten completely
/*
        if (true) throw new NotImplementedException("Not implemented yet");
        final ExperimentFeature feature = null;
//        final ExperimentFeature feature = task1.getFeature();
        assertNotNull(feature);
        assertNotNull(task1.getSimpleTask());
        assertNotNull(task1.getSimpleTask());

        finishCurrentWithOk(2);

        getTaskAndAssignToProcessor_mustBeNewTask();


        System.out.println();
*/
        });

    }

}
