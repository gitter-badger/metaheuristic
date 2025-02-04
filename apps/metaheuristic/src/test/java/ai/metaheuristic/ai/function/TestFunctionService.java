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

package ai.metaheuristic.ai.function;

import ai.metaheuristic.ai.Globals;
import ai.metaheuristic.ai.dispatcher.beans.Function;
import ai.metaheuristic.ai.dispatcher.function.FunctionDataService;
import ai.metaheuristic.ai.dispatcher.function.FunctionService;
import ai.metaheuristic.ai.dispatcher.function.FunctionTopLevelService;
import ai.metaheuristic.ai.dispatcher.repositories.FunctionRepository;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.api.data.task.TaskParamsYaml;
import ai.metaheuristic.commons.yaml.function.FunctionConfigYaml;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.core.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 7/12/2019
 * Time: 5:42 PM
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("dispatcher")
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@AutoConfigureCache
public class TestFunctionService {

    private static final String TEST_FUNCTION = "test.function:1.0";
    private static final String FUNCTION_PARAMS = "AAA";

    @Autowired
    private FunctionService functionService;

    @Autowired
    private FunctionTopLevelService functionTopLevelService;

    @Autowired
    private FunctionRepository functionRepository;

    @Autowired
    private FunctionDataService functionDataService;

    @Autowired
    private Globals globals;

    public Function function = null;

    @Test
    public void test() {
        ExecContextParamsYaml.FunctionDefinition sd = new ExecContextParamsYaml.FunctionDefinition();
        sd.code = TEST_FUNCTION;
        sd.params = null;
        TaskParamsYaml.FunctionConfig sc = functionTopLevelService.getFunctionConfig(sd);
        assertNotNull(sc);
        assertNotNull(sc.params);
        final String[] split = StringUtils.split(sc.params);
        assertNotNull(split);
        assertEquals(1, split.length, "Expected: ["+ FUNCTION_PARAMS +"], actual: " + Arrays.toString(split));
        assertArrayEquals(new String[] {FUNCTION_PARAMS}, split);
    }

    @BeforeEach
    public void beforePreparingExperiment() {
        assertTrue(globals.testing);
        function = initFunction();
    }

    private Function initFunction() {
        long mills;
        byte[] bytes = "some program code".getBytes();
        Function f = functionRepository.findByCode(TEST_FUNCTION);
        if (f == null) {
            FunctionConfigYaml sc = new FunctionConfigYaml();
            sc.code = TEST_FUNCTION;
            sc.sourcing = EnumsApi.FunctionSourcing.dispatcher;
            sc.type = "test";
            sc.env = "python-3";
            sc.file = "predict-filename.txt";
            sc.skipParams = false;
            sc.params = FUNCTION_PARAMS;

            mills = System.currentTimeMillis();
            log.info("Start functionRepository.save() #2");
            f = functionService.persistFunction(sc, new ByteArrayInputStream(bytes), bytes.length);

            log.info("functionRepository.save() #2 was finished for {} milliseconds", System.currentTimeMillis() - mills);
        }
        return f;
    }

    @AfterEach
    public void afterPreparingExperiment() {
        long mills = System.currentTimeMillis();
        log.info("Start after()");
        if (function != null) {
            try {
                functionService.deleteFunction(function.getId(), function.code);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            try {
                functionDataService.deleteByFunctionCode(function.getCode());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        System.out.println("Was finished correctly");
        log.info("after() was finished for {} milliseconds", System.currentTimeMillis() - mills);
    }

}
