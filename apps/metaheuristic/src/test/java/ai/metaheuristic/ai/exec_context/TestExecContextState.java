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

package ai.metaheuristic.ai.exec_context;

import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextService;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.task.TaskApiData;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static ai.metaheuristic.api.EnumsApi.SourceCodeType;
import static ai.metaheuristic.api.EnumsApi.TaskExecState;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 8/20/2020
 * Time: 12:02 AM
 */
public class TestExecContextState {

    @Test
    public void test() {

        List<ExecContextApiData.TaskStateInfo> infos = List.of(
                new ExecContextApiData.TaskStateInfo(100L, 1001L, "ctx-1", "process-1", "function-1", null, null),
                new ExecContextApiData.TaskStateInfo(120L, 1001L, "ctx-1.1", "process-2", "function-2", null, null),
                new ExecContextApiData.TaskStateInfo(140L, 1001L, "ctx-1.1", "process-3", "function-3", null, null),
                new ExecContextApiData.TaskStateInfo(160L, 1001L, "ctx-1.2", "process-2", "function-2", null, null),
                new ExecContextApiData.TaskStateInfo(180L, 1001L, "ctx-1.2", "process-3", "function-3", null, null),
                new ExecContextApiData.TaskStateInfo(190L, 1001L, "ctx-1", "mh.finish", "mh.finish", null, null)
        );

        Map<Long, TaskApiData.TaskState> states = Map.of(
                100L, new TaskApiData.TaskState(100L, TaskExecState.OK.value, 0L),
                120L, new TaskApiData.TaskState(120L, TaskExecState.IN_PROGRESS.value, 0L),
                140L, new TaskApiData.TaskState(140L, TaskExecState.NONE.value, 0L),
                160L, new TaskApiData.TaskState(160L, TaskExecState.OK.value, 0L),
                180L, new TaskApiData.TaskState(180L, TaskExecState.ERROR.value, 0L),
                190L, new TaskApiData.TaskState(190L, TaskExecState.OK.value, 0L)
        );
        List<String> processCodes = List.of("process-1", "process-2", "process-3", "mh.finish");
        ExecContextApiData.RawExecContextStateResult raw = new ExecContextApiData.RawExecContextStateResult(
                1L, infos, processCodes, SourceCodeType.batch, "test-source-code", true, states);
        ExecContextApiData.ExecContextStateResult r = ExecContextService.getExecContextStateResult(raw);

        assertNotNull(r);
        assertNotNull(r.header);
        assertNotNull(r.lines);
        assertEquals(4, r.header.length);
        assertEquals(3, r.lines.length);

        assertEquals("process-1", r.header[0].process);
        assertEquals("process-2", r.header[1].process);
        assertEquals("process-3", r.header[2].process);
        assertEquals("mh.finish", r.header[3].process);

        assertEquals("ctx-1", r.lines[0].context);
        assertEquals("ctx-1.1", r.lines[1].context);
        assertEquals("ctx-1.2", r.lines[2].context);

        assertFalse(r.lines[0].cells[0].empty);
        assertTrue(r.lines[0].cells[1].empty);
        assertTrue(r.lines[0].cells[2].empty);
        assertFalse(r.lines[0].cells[3].empty);

        assertTrue(r.lines[1].cells[0].empty);
        assertFalse(r.lines[1].cells[1].empty);
        assertFalse(r.lines[1].cells[2].empty);
        assertTrue(r.lines[1].cells[3].empty);

        assertTrue(r.lines[2].cells[0].empty);
        assertFalse(r.lines[2].cells[1].empty);
        assertFalse(r.lines[2].cells[2].empty);
        assertTrue(r.lines[2].cells[3].empty);

        assertEquals(100L, r.lines[0].cells[0].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[0].cells[0].state);
        assertEquals("ctx-1", r.lines[0].cells[0].context);

        assertEquals(190L, r.lines[0].cells[3].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[0].cells[3].state);
        assertEquals("ctx-1", r.lines[0].cells[3].context);

        assertEquals(120L, r.lines[1].cells[1].taskId);
        assertEquals(TaskExecState.IN_PROGRESS.toString(), r.lines[1].cells[1].state);
        assertEquals("ctx-1.1", r.lines[1].cells[1].context);

        assertEquals(140L, r.lines[1].cells[2].taskId);
        assertEquals(TaskExecState.NONE.toString(), r.lines[1].cells[2].state);
        assertEquals("ctx-1.1", r.lines[1].cells[2].context);

        assertEquals(160L, r.lines[2].cells[1].taskId);
        assertEquals(TaskExecState.OK.toString(), r.lines[2].cells[1].state);
        assertEquals("ctx-1.2", r.lines[2].cells[1].context);

        assertEquals(180L, r.lines[2].cells[2].taskId);
        assertEquals(TaskExecState.ERROR.toString(), r.lines[2].cells[2].state);
        assertEquals("ctx-1.2", r.lines[2].cells[2].context);


        System.out.println(Arrays.toString(r.header));
        for (ExecContextApiData.LineWithState line : r.lines) {
            System.out.println(line.context+", "+ Arrays.toString(line.cells));
        }
    }
}
